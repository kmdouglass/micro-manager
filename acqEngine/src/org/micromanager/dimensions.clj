(ns org.micromanager.dimensions)

(defn next-item
  "Given an item and a vector of items, returns the next
   item in the vector. If item is not found, the first
   item in the vector is returned."
  [item items]
  (nth items (mod (inc (.indexOf items item))
                        (count items))))

(defn next-indices
  "Give a vector of indices, and a list of ranges,
   produces the next vector of indices, where the first index
   loops the fastest. For example,
   (next-indices [3 2 4] [4 3 7]) => [0 0 5]) .
   Returns nil when indices is the last in the sequence."
  [indices ranges]
  (let [n (count indices)
        ranges (vec ranges)]
    (loop [dimension 0 new-indices (vec indices)]
      (when (< dimension n)
        (let [new-index (inc (new-indices dimension))]
          (if (< new-index (ranges dimension))
            (assoc new-indices dimension new-index)
            (recur (inc dimension)
                   (assoc new-indices dimension 0))))))))
        
(defn indices-in-order
  "Give a set of plane-indices and a dimension order, produces a
   vector of indices (integers. For example,
   (indices-in-order {:position 10 :slice 4 :channel 3 :frame 20}
                     [:channel :slice :frame :position])
     => [3 4 20 10] ."
  [plane-indices order]
  (vec (map #(% plane-indices) order)))

(defn next-plane-indices [current-plane-indices dimension-sizes order]
  (when-let [next-indices  (next-indices (indices-in-order current-plane-indices order)
                                         (indices-in-order dimension-sizes order))]
    (zipmap order next-indices)))

(defn plane-indices [{:keys [position channel slice frame] :as plane}
                     {:keys [positions channels slices] :as dimension-ranges}]
  {:frame frame
   :position (.indexOf positions position)
   :channel (.indexOf channels channel)
   :slice (.indexOf slices slice)})

(defn plane-values [{:keys [position channel slice frame] :as plane-indices}
                    {:keys [positions channels slices] :as dimension-values}]
  {:frame frame
   :position (positions position)
   :channel (channels channel)
   :slice (slices slice)})
    
(defn dimension-sizes [{:keys [positions channels slices num-frames]}]
  {:frame num-frames
   :position (count positions)
   :slice (count slices)
   :channel (count channels)})
  
(defn next-plane [{:keys [position channel slice frame] :as current-plane}
                  {:keys [positions channels slices frames] :as dimension-values}
                  order]
    (let [dimension-sizes (dimension-sizes dimension-values)
          current-plane-indices (plane-indices current-plane dimension-values)
          next-plane-indices (next-plane-indices current-plane-indices dimension-sizes order)]
      (when next-plane-indices
        (plane-values next-plane-indices dimension-values))))
  
(defn middle-item [items]
  (let [n (count items)]
    (nth items (long (/ n 2)))))

(defn plane-forbidden? [{:keys [position channel slice frame] :as plane}
                        {:keys [slices] :as dimension-values}
                      channel-settings]
  (let [{:keys [use-channel skip-frames use-z-stack] :as channel-setting}
        (channel-settings channel)]
    (and channel-setting
         (or (not use-channel)
             (and (pos? skip-frames) (pos? (mod frame (inc skip-frames))))
             (and (not use-z-stack) (not= slice (middle-item slices)))))))             

(defn next-allowed-plane [current-plane dimension-values order channel-settings]
  (loop [plane current-plane]
    (when-let [next-plane (next-plane plane dimension-values order)]
      (if (plane-forbidden? next-plane dimension-values channel-settings)
        (recur next-plane)
        next-plane))))

;; testing ;;;;;;;;;


(defn run-through-planes-simple [first-plane dimension-values order]
  (take-while identity (iterate #(next-plane % dimension-values order) first-plane)))

(defn run-through-planes [first-plane dimension-values order channel-settings]
  (take-while identity (iterate #(next-allowed-plane
                                   % dimension-values order channel-settings)
                                first-plane)))

(defn compute-first-plane [{:keys [positions channels slices] :as dimension-ranges}]
  {:frame 0
   :position (first positions)
   :slice (first slices)
   :channel (first channels)})

(def test-dimension-values
  {:num-frames 10
   :positions ["Pos0" "Pos1" "Pos2"]
   :slices [0.0 1.0 2.0]
   :channels ["Cy5" "DAPI" "FITC"]
   })

(def test-plane
  (compute-first-plane test-dimension-values))
  
(def test-order
  [:slice :channel :position :frame])
    
(def test-channel-settings
  {"Cy5"
   {;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
    :exposure 10.0,
    :properties
    {["Dichroic" "Label"] "400DCLP",
     ["Emission" "Label"] "Chroma-HQ700",
     ["Excitation" "Label"] "Chroma-HQ570"},
    :skip-frames 0,
    :use-channel true,
    :use-z-stack false,
    :z-offset 0.0}
   "DAPI"
   {;:color #<Color java.awt.Color[r=0,g=204,b=51]>,
    :exposure 10.0,
    :properties
    {["Dichroic" "Label"] "400DCLP",
     ["Emission" "Label"] "Chroma-HQ620",
     ["Excitation" "Label"] "Chroma-D360"},
    :skip-frames 1,
    :use-channel true,
    :use-z-stack true,
    :z-offset 0.0}
   "FITC"
   {;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
    :exposure 10.0,
    :properties
    {["Dichroic" "Label"] "Q505LP",
     ["Emission" "Label"] "Chroma-HQ535",
     ["Excitation" "Label"] "Chroma-HQ480"},
    :skip-frames 0,
    :use-channel true,
    :use-z-stack true,
    :z-offset 0.0}})

(defn test-run []
  (run-through-planes test-plane test-dimension-values test-order test-channel-settings))

  
(def test-acq-settings
  {:autofocus-skip 0,
   :channels
   [{;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
     :exposure 10.0,
     :name "Cy5",
     :properties
     {["Dichroic" "Label"] "400DCLP",
      ["Emission" "Label"] "Chroma-HQ700",
      ["Excitation" "Label"] "Chroma-HQ570"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}
    {;:color #<Color java.awt.Color[r=0,g=204,b=51]>,
     :exposure 10.0,
     :name "DAPI",
     :properties
     {["Dichroic" "Label"] "400DCLP",
      ["Emission" "Label"] "Chroma-HQ620",
      ["Excitation" "Label"] "Chroma-D360"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}
    {;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
     :exposure 10.0,
     :name "FITC",
     :properties
     {["Dichroic" "Label"] "Q505LP",
      ["Emission" "Label"] "Chroma-HQ535",
      ["Excitation" "Label"] "Chroma-HQ480"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}],
   :comment "",
   :custom-intervals-ms [],
   :default-exposure 10.0,
   :frames '(0 1 2),
   :interval-ms 0.0,
   :keep-shutter-open-channels false,
   :keep-shutter-open-slices false,
   :numFrames 3,
   :positions [0 1],
   :prefix nil,
   :relative-slices true,
   :root nil,
   :save false,
   :slices [0.0 1.0 2.0],
   :slices-first false,
   :time-first false,
   :use-autofocus false,
   :zReference 3.0})
  
(def test-acq-settings
  {:autofocus-skip 0,
   :channels
   [{;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
     :exposure 10.0,
     :name "Cy5",
     :properties
     {["Dichroic" "Label"] "400DCLP",
      ["Emission" "Label"] "Chroma-HQ700",
      ["Excitation" "Label"] "Chroma-HQ570"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}
    {;:color #<Color java.awt.Color[r=0,g=204,b=51]>,
     :exposure 10.0,
     :name "DAPI",
     :properties
     {["Dichroic" "Label"] "400DCLP",
      ["Emission" "Label"] "Chroma-HQ620",
      ["Excitation" "Label"] "Chroma-D360"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}
    {;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
     :exposure 10.0,
     :name "FITC",
     :properties
     {["Dichroic" "Label"] "Q505LP",
      ["Emission" "Label"] "Chroma-HQ535",
      ["Excitation" "Label"] "Chroma-HQ480"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}],
   :comment "",
   :custom-intervals-ms [],
   :default-exposure 10.0,
   :frames '(0 1 2),
   :interval-ms 0.0,
   :keep-shutter-open-channels false,
   :keep-shutter-open-slices false,
   :numFrames 3,
   :positions [0 1],
   :prefix nil,
   :relative-slices true,
   :root nil,
   :save false,
   :slices [0.0 1.0 2.0],
   :slices-first false,
   :time-first false,
   :use-autofocus false,
   :zReference 3.0})

(def test-acq-event
  {:close-shutter true,
   :next-frame-index 0,
   :frame-index 0,
   :position 0,
   :position-index 0,
   :slice 0.0,
   :new-position true,
   :autofocus false,
   :channel-index 0,
   :slice-index 0,
   :frame 0,
   :channel
   {;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
    :exposure 10.0,
    :name "Cy5",
    :properties
    {["Dichroic" "Label"] "400DCLP",
     ["Emission" "Label"] "Chroma-HQ700",
     ["Excitation" "Label"] "Chroma-HQ570"},
    :skip-frames 0,
    :use-channel true,
    :use-z-stack true,
    :z-offset 0.0},
   :exposure 10.0,
   :relative-z true,
   :task :snap,
   :wait-time-ms 0})