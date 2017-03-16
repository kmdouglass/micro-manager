/*
 * Copyright (c) 2015-2017, Regents the University of California
 * Author: Nico Stuurman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package edu.ucsf.valelab.gaussianfit.datasetdisplay;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.ResultsTableListener;
import edu.ucsf.valelab.gaussianfit.Terms;
import edu.ucsf.valelab.gaussianfit.data.GsSpotPair;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.fitting.FittingException;
import edu.ucsf.valelab.gaussianfit.fitting.Gaussian1DFitter;
import edu.ucsf.valelab.gaussianfit.fitting.P2DFitter;
import edu.ucsf.valelab.gaussianfit.spotoperations.NearestPoint2D;
import edu.ucsf.valelab.gaussianfit.spotoperations.NearestPointGsSpotPair;
import edu.ucsf.valelab.gaussianfit.utils.CalcUtils;
import edu.ucsf.valelab.gaussianfit.utils.GaussianUtils;
import edu.ucsf.valelab.gaussianfit.utils.ListUtils;
import edu.ucsf.valelab.gaussianfit.utils.NumberUtils;
import edu.ucsf.valelab.gaussianfit.utils.ReportingUtils;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Arrow;
import ij.gui.ImageWindow;
import ij.gui.MessageDialog;
import ij.gui.StackWindow;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.jfree.data.xy.XYSeries;
import org.micromanager.internal.MMStudio;

/**
 *
 * @author nico
 */
public class ParticlePairLister {

   final private int[] rows_;
   final private Double maxDistanceNm_;
   final private Boolean showTrack_;
   final private Boolean showSummary_;
   final private Boolean showOverlay_;
   final private Boolean saveFile_;
   final private Boolean p2d_;
   final private Boolean doGaussianEstimate_;
   final private Boolean fitSigmaInP2D_;
   final private Boolean useSigmaUserGuess_;
   final private Boolean useVectorDistances_;
   final private Double sigmaUserGuess_;
   final private String filePath_;

   public static class Builder {

      private int[] rows_;
      private Double maxDistanceNm_; //maximum distance in nm for two spots in different
      // channels to be considered a pair
      private Boolean showTrack_;
      private Boolean showSummary_;
      private Boolean showOverlay_;
      private Boolean saveFile_;
      private Boolean p2d_;
      private Boolean doGaussianEstimate_;
      private Boolean fitSigma_;
      private Boolean useSigmaUserGuess_;
      private Boolean useVectorDistances_;
      private Double sigmaEstimate_;
      private String filePath_;

      public ParticlePairLister build() {
         return new ParticlePairLister(this);
      }

      public Builder rows(int[] rows) {
         rows_ = rows;
         return this;
      }

      public Builder maxDistanceNm(Double maxDistanceNm) {
         maxDistanceNm_ = maxDistanceNm;
         return this;
      }

      public Builder showTrack(Boolean showTrack) {
         showTrack_ = showTrack;
         return this;
      }

      public Builder showSummary(Boolean showSummary) {
         showSummary_ = showSummary;
         return this;
      }

      public Builder showOverlay(Boolean showOverlay) {
         showOverlay_ = showOverlay;
         return this;
      }

      public Builder saveFile(Boolean saveFile) {
         saveFile_ = saveFile;
         return this;
      }

      public Builder p2d(Boolean p2d) {
         p2d_ = p2d;
         return this;
      }

      public Builder doGaussianEstimate(Boolean doGaussianEstimate) {
         doGaussianEstimate_ = doGaussianEstimate;
         return this;
      }

      public Builder fitSigma(Boolean fixSigma) {
         fitSigma_ = fixSigma;
         return this;
      }

      public Builder useSigmaEstimate(Boolean useSigmaEstimate) {
         useSigmaUserGuess_ = useSigmaEstimate;
         return this;
      }
      
      public Builder useVectorDistances(Boolean useVectorDistances) {
         useVectorDistances_ = useVectorDistances;
         return this;
      }

      public Builder sigmaEstimate(Double sigmaEstimate) {
         sigmaEstimate_ = sigmaEstimate;
         return this;
      }

      public Builder filePath(String filePath) {
         filePath_ = filePath;
         return this;
      }

   }

   public ParticlePairLister(Builder builder) {
      rows_ = builder.rows_;
      maxDistanceNm_ = builder.maxDistanceNm_;
      showTrack_ = builder.showTrack_;
      showSummary_ = builder.showSummary_;
      showOverlay_ = builder.showOverlay_;
      saveFile_ = builder.saveFile_;
      p2d_ = builder.p2d_;
      doGaussianEstimate_ = builder.doGaussianEstimate_;
      fitSigmaInP2D_ = builder.fitSigma_;
      useSigmaUserGuess_ = builder.useSigmaUserGuess_;
      useVectorDistances_ = builder.useVectorDistances_;
      sigmaUserGuess_ = builder.sigmaEstimate_;
      filePath_ = builder.filePath_;
   }

   public Builder copy() {
      return new Builder().
              rows(rows_).
              maxDistanceNm(maxDistanceNm_).
              showTrack(showTrack_).
              showSummary(showSummary_).
              showOverlay(showOverlay_).
              saveFile(saveFile_).
              p2d(p2d_).
              doGaussianEstimate(doGaussianEstimate_).
              fitSigma(fitSigmaInP2D_).
              useSigmaEstimate(useSigmaUserGuess_).
              useVectorDistances(useVectorDistances_).
              sigmaEstimate(sigmaUserGuess_).
              filePath(filePath_);
   }

   /**
    * Cycles through the spots of the selected data set and finds the most
    * nearby spot in channel 2. It will list this as a pair if the two spots are
    * within MAXMATCHDISTANCE nm of each other.
    *
    * Once all pairs are found, it will go through all frames and try to build
    * up tracks. If the spot is within MAXMATCHDISTANCE between frames, the code
    * will consider the particle to be identical.
    *
    * All "tracks" of particles will be listed
    *
    * In addition, it will list the average distance, and average distance in x
    * and y for each frame.
    *
    * Currently, the code only lists "tracks" starting at frame 1.
    *
    * Needed input variables are set through a builder.
    *
    */
   public void listParticlePairTracks() {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {

            final DataCollectionForm dc = DataCollectionForm.getInstance();

            // Show Particle List as linked Results Table
            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);

            // Show Particle Summary as Linked Results Table
            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(1);

            // Saves output of P2D fitting            
            ResultsTable rt3 = new ResultsTable();
            rt3.reset();
            rt3.setPrecision(2);

            int rowCounter = 0;
            for (int row : rows_) {
               rowCounter++;
               ij.IJ.showStatus("Creating Pairs for row " + rowCounter);

               Map<Integer, ArrayList<ArrayList<GsSpotPair>>> spotPairsByFrame
                       = new HashMap<Integer, ArrayList<ArrayList<GsSpotPair>>>();

               // index spots by position
               Map<Integer, ArrayList<SpotData>> spotListsByPosition = new HashMap<Integer, ArrayList<SpotData>>();
               // and keep track of the positions that are actually used
               List<Integer> positions = new ArrayList<Integer>();
               for (SpotData spot : dc.getSpotData(row).spotList_) {
                  if (positions.indexOf(spot.getPosition()) == -1) {
                     positions.add(spot.getPosition());
                  }
                  if (spotListsByPosition.get(spot.getPosition()) == null) {
                     spotListsByPosition.put(spot.getPosition(), new ArrayList<SpotData>());
                  }
                  spotListsByPosition.get(spot.getPosition()).add(spot);
               }
               Collections.sort(positions);

               // First go through all frames to find all pairs, organize by position
               int nrSpotPairsInFrame1 = 0;
               for (int pos : positions) {
                  spotPairsByFrame.put(pos, new ArrayList<ArrayList<GsSpotPair>>());

                  for (int frame = 1; frame <= dc.getSpotData(row).nrFrames_; frame++) {
                     // TODO: show correct progress
                     ij.IJ.showProgress(frame, dc.getSpotData(row).nrFrames_);

                     spotPairsByFrame.get(pos).add(new ArrayList<GsSpotPair>());

                     // Get points from both channels as ArrayLists   
                     ArrayList<SpotData> gsCh1 = new ArrayList<SpotData>();
                     ArrayList<SpotData> gsCh2 = new ArrayList<SpotData>();
                     ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
                     for (SpotData gs : spotListsByPosition.get(pos)) {
                        if (gs.getFrame() == frame) {
                           if (gs.getChannel() == 1) {
                              gsCh1.add(gs);
                           } else if (gs.getChannel() == 2) {
                              gsCh2.add(gs);
                              Point2D.Double point = new Point2D.Double(
                                      gs.getXCenter(), gs.getYCenter());
                              xyPointsCh2.add(point);
                           }
                        }
                     }

                     if (xyPointsCh2.isEmpty()) {
                        ReportingUtils.logError(
                                "Pairs function in Localization plugin: no points found in second channel in frame "
                                + frame);
                        continue;
                     }

                     // Find matching points in the two ArrayLists
                     Iterator it2 = gsCh1.iterator();
                     NearestPoint2D np = new NearestPoint2D(xyPointsCh2, maxDistanceNm_);
                     while (it2.hasNext()) {
                        SpotData ch1Spot = (SpotData) it2.next();
                        Point2D.Double pCh1 = new Point2D.Double(
                                ch1Spot.getXCenter(), ch1Spot.getYCenter());
                        Point2D.Double pCh2 = np.findKDWSE(pCh1);
                        if (pCh2 != null) {
                           // find this point in the ch2 spot list
                           SpotData ch2Spot = null;
                           for (int i = 0; i < gsCh2.size() && ch2Spot == null; i++) {
                              if (pCh2.x == gsCh2.get(i).getXCenter()
                                      && pCh2.y == gsCh2.get(i).getYCenter()) {
                                 ch2Spot = gsCh2.get(i);
                              }
                           }
                           if (ch2Spot != null) {
                              GsSpotPair pair = new GsSpotPair(ch1Spot, ch2Spot, pCh1, pCh2);
                              spotPairsByFrame.get(pos).get(frame - 1).add(pair);
                           } else {
                              // this should never happen!
                              System.out.println("Failed to find spot");
                           }
                        }
                     }
                  }
               }

               // We have all pairs, assemble in tracks
               ij.IJ.showStatus("Assembling tracks for row " + rowCounter);

               ArrayList<ArrayList<GsSpotPair>> tracks = new ArrayList<ArrayList<GsSpotPair>>();

               for (int pos : positions) {
                  // prepare NearestPoint objects to speed up finding closest pair 
                  ArrayList<NearestPointGsSpotPair> npsp = new ArrayList<NearestPointGsSpotPair>();
                  Iterator<GsSpotPair> iSpotPairs = spotPairsByFrame.get(pos).get(0).iterator();
                  for (int frame = 1; frame <= dc.getSpotData(row).nrFrames_; frame++) {
                     npsp.add(new NearestPointGsSpotPair(
                             spotPairsByFrame.get(pos).get(frame - 1), maxDistanceNm_));
                  }
                  int i = 0;
                  while (iSpotPairs.hasNext()) {
                     ij.IJ.showProgress(i++, nrSpotPairsInFrame1);
                     GsSpotPair spotPair = iSpotPairs.next();
                     // for now, we only start tracks at frame number 1
                     if (spotPair.getFirstSpot().getFrame() == 1) {
                        ArrayList<GsSpotPair> track = new ArrayList<GsSpotPair>();
                        track.add(spotPair);
                        int frame = 2;
                        while (frame <= dc.getSpotData(row).nrFrames_) {
                           GsSpotPair newSpotPair = npsp.get(frame - 1).findKDWSE(
                                   new Point2D.Double(spotPair.getFirstPoint().getX(),
                                           spotPair.getFirstPoint().getY()));
                           if (newSpotPair != null) {
                              spotPair = newSpotPair;
                              track.add(spotPair);
                           }
                           frame++;
                        }
                        tracks.add(track);
                     }
                  }
               }

               if (tracks.isEmpty()) {
                  MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                          "No Pairs found", "No Pairs found");
                  continue;
               }

               Iterator<ArrayList<GsSpotPair>> itTracks = tracks.iterator();
               int spotId = 0;
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  Iterator<GsSpotPair> itTrack = track.iterator();
                  while (itTrack.hasNext()) {
                     GsSpotPair spot = itTrack.next();
                     rt.incrementCounter();
                     rt.addValue("Spot ID", spotId);
                     rt.addValue(Terms.FRAME, spot.getFirstSpot().getFrame());
                     rt.addValue(Terms.SLICE, spot.getFirstSpot().getSlice());
                     rt.addValue(Terms.CHANNEL, spot.getFirstSpot().getChannel());
                     rt.addValue(Terms.POSITION, spot.getFirstSpot().getPosition());
                     rt.addValue(Terms.XPIX, spot.getFirstSpot().getX());
                     rt.addValue(Terms.YPIX, spot.getFirstSpot().getY());
                     double distance = Math.sqrt(
                             NearestPoint2D.distance2(spot.getFirstPoint(), spot.getSecondPoint()));
                     rt.addValue("Distance", distance);
                     if (spot.getFirstSpot().hasKey("stdDev")) {
                        double stdDev = spot.getFirstSpot().getValue("stdDev");
                        rt.addValue("stdDev1", stdDev);
                        SpotData spot2 = spot.getSecondSpot();
                        if (spot2 != null && spot2.hasKey("stdDev")) {
                           double stdDev2 = spot2.getValue("stdDev");
                           rt.addValue("stdDev2", stdDev2);
                           double distanceStdDev = CalcUtils.stdDev(
                                   spot.getFirstPoint().x, spot.getSecondPoint().x,
                                   spot.getFirstPoint().y, spot.getSecondPoint().y,
                                   spot.getFirstSpot().getValue("stdDevX"),
                                   spot2.getValue("stdDevX"),
                                   spot.getFirstSpot().getValue("stdDevY"),
                                   spot2.getValue("stdDevY"));
                           rt.addValue("stdDev-distance", distanceStdDev);
                        }
                     }
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(spot.getFirstPoint(), spot.getSecondPoint()));
                  }
                  spotId++;
               }

               TextPanel tp;
               TextWindow win;
               String rtName = dc.getSpotData(row).getName() + " Particle List";
               if (showTrack_) {
                  rt.show(rtName);
                  ImagePlus siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
                  Frame frame = WindowManager.getFrame(rtName);
                  if (frame != null && frame instanceof TextWindow && siPlus != null) {
                     win = (TextWindow) frame;
                     tp = win.getTextPanel();

                     // TODO: the following does not work, there is some voodoo going on here
                     for (MouseListener ms : tp.getMouseListeners()) {
                        tp.removeMouseListener(ms);
                     }
                     for (KeyListener ks : tp.getKeyListeners()) {
                        tp.removeKeyListener(ks);
                     }

                     ResultsTableListener myk = new ResultsTableListener(
                             MMStudio.getInstance(), dc.getSpotData(row).dw_, siPlus,
                             rt, win, dc.getSpotData(row).halfSize_);
                     tp.addKeyListener(myk);
                     tp.addMouseListener(myk);
                     frame.toFront();
                  }
               }

               if (saveFile_) {
                  try {
                     String fileName = filePath_ + File.separator
                             + dc.getSpotData(row).getName() + "_PairTracks.cvs";
                     rt.saveAs(fileName);
                     ij.IJ.log("Saved file: " + fileName);
                  } catch (IOException ex) {
                     ReportingUtils.showError(ex, "Failed to save file");
                  }
               }

               ImagePlus siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
               if (showOverlay_) {
                  if (siPlus != null && siPlus.getOverlay() != null) {
                     siPlus.getOverlay().clear();
                  }
                  Arrow.setDefaultWidth(0.5);
               }

               itTracks = tracks.iterator();
               spotId = 0;
               List<Double> avgDistances = new ArrayList<Double>(tracks.size());
               List<Double> avgVectDistances = new ArrayList<Double>(tracks.size());
               List<Double> allDistances = new ArrayList<Double>(
                       tracks.size() * dc.getSpotData(row).nrFrames_);
               List<Double> stdDevs = new ArrayList<Double>(tracks.size());
               List<Double> avgSigmas = new ArrayList<Double>(tracks.size());
               List<Double> allSigmas = new ArrayList<Double>(
                       tracks.size() * dc.getSpotData(row).nrFrames_);
               List<Integer> trackLengths = new ArrayList<Integer>(tracks.size());
               while (itTracks.hasNext()) {
                  ArrayList<GsSpotPair> track = itTracks.next();
                  ArrayList<Double> distances = new ArrayList<Double>();
                  ArrayList<Double> orientations = new ArrayList<Double>();
                  ArrayList<Double> xDiff = new ArrayList<Double>();
                  ArrayList<Double> yDiff = new ArrayList<Double>();
                  ArrayList<Double> sigmas = new ArrayList<Double>();
                  for (GsSpotPair pair : track) {
                     double distance = Math.sqrt(
                             NearestPoint2D.distance2(pair.getFirstPoint(), pair.getSecondPoint()));
                     distances.add(distance);
                     allDistances.add(distance);
                     orientations.add(NearestPoint2D.orientation(pair.getFirstPoint(),
                             pair.getSecondPoint()));
                     xDiff.add(pair.getFirstPoint().getX() - pair.getSecondPoint().getX());
                     yDiff.add(pair.getFirstPoint().getY() - pair.getSecondPoint().getY());
                     double sigma = Math.sqrt(
                             pair.getFirstSpot().getSigma()
                             * pair.getFirstSpot().getSigma()
                             + pair.getSecondSpot().getSigma()
                             * pair.getSecondSpot().getSigma());
                     sigmas.add(sigma);
                     allSigmas.add(sigma);
                  }
                  GsSpotPair pair = track.get(0);
                  rt2.incrementCounter();
                  rt2.addValue("Row ID", dc.getSpotData(row).ID_);
                  rt2.addValue("Spot ID", spotId);
                  rt2.addValue(Terms.FRAME, pair.getFirstSpot().getFrame());
                  rt2.addValue(Terms.SLICE, pair.getFirstSpot().getSlice());
                  rt2.addValue(Terms.CHANNEL, pair.getFirstSpot().getSlice());
                  rt2.addValue(Terms.POSITION, pair.getFirstSpot().getPosition());
                  rt2.addValue(Terms.XPIX, pair.getFirstSpot().getX());
                  rt2.addValue(Terms.YPIX, pair.getFirstSpot().getY());
                  trackLengths.add(track.size());
                  rt2.addValue("n", track.size());

                  double avg = ListUtils.listAvg(distances);
                  avgDistances.add(avg);
                  rt2.addValue("Distance-Avg", avg);
                  double std = ListUtils.listStdDev(distances, avg);
                  stdDevs.add(std);
                  rt2.addValue("Distance-StdDev", std);
                  double avgSigma = ListUtils.listAvg(sigmas);
                  avgSigmas.add(avgSigma);
                  rt2.addValue("Sigma", avgSigma);
                  double oAvg = ListUtils.listAvg(orientations);
                  rt2.addValue("Orientation-Avg", oAvg);
                  rt2.addValue("Orientation-StdDev",
                          ListUtils.listStdDev(orientations, oAvg));

                  double xDiffAvg = ListUtils.listAvg(xDiff);
                  double yDiffAvg = ListUtils.listAvg(yDiff);
                  double xDiffAvgStdDev = ListUtils.listStdDev(xDiff, xDiffAvg);
                  double yDiffAvgStdDev = ListUtils.listStdDev(yDiff, yDiffAvg);
                  double vectAvg = Math.sqrt(
                          (xDiffAvg * xDiffAvg) + (yDiffAvg * yDiffAvg));
                  avgVectDistances.add(vectAvg);
                  rt2.addValue("Dist.Vect.Avg", vectAvg);
                  rt2.addValue("Dist.Vect.StdDev", Math.sqrt(
                          (xDiffAvgStdDev * xDiffAvgStdDev)
                          + (yDiffAvgStdDev * yDiffAvgStdDev)));

                  if (showOverlay_) {
                     /* draw arrows in overlay */
                     double mag = 100.0;  // factor that sets magnification of the arrow
                     double factor = mag * 1 / dc.getSpotData(row).pixelSizeNm_;  // factor relating mad and pixelSize
                     int xStart = track.get(0).getFirstSpot().getX();
                     int yStart = track.get(0).getFirstSpot().getY();

                     Arrow arrow = new Arrow(xStart, yStart,
                             xStart + (factor * xDiffAvg),
                             yStart + (factor * yDiffAvg));
                     arrow.setHeadSize(3);
                     arrow.setOutline(false);
                     if (siPlus != null && siPlus.getOverlay() == null) {
                        siPlus.setOverlay(arrow, Color.yellow, 1, Color.yellow);
                     } else if (siPlus != null && siPlus.getOverlay() != null) {
                        siPlus.getOverlay().add(arrow);
                     }
                  }

                  spotId++;
               }
               if (showOverlay_) {
                  if (siPlus != null) {
                     siPlus.setHideOverlay(false);
                  }
               }

               if (showSummary_) {
                  rtName = dc.getSpotData(row).getName() + " Particle Summary";
                  rt2.show(rtName);
                  siPlus = ij.WindowManager.getImage(dc.getSpotData(row).title_);
                  Frame frame = WindowManager.getFrame(rtName);
                  if (frame != null && frame instanceof TextWindow && siPlus != null) {
                     win = (TextWindow) frame;
                     tp = win.getTextPanel();

                     // TODO: the following does not work, there is some voodoo going on here
                     for (MouseListener ms : tp.getMouseListeners()) {
                        tp.removeMouseListener(ms);
                     }
                     for (KeyListener ks : tp.getKeyListeners()) {
                        tp.removeKeyListener(ks);
                     }

                     ResultsTableListener myk = new ResultsTableListener(
                             MMStudio.getInstance(), dc.getSpotData(row).dw_, siPlus,
                             rt2, win, dc.getSpotData(row).halfSize_);
                     tp.addKeyListener(myk);
                     tp.addMouseListener(myk);
                     frame.toFront();
                  }
               }

               double[] gResult = null;
               double[] avgVectDistancesAsDouble = null;
               if (doGaussianEstimate_ || (p2d_ && useVectorDistances_) ) {
                  // fit vector distances with gaussian function and plot
                  try {
                     avgVectDistancesAsDouble = ListUtils.toArray(avgVectDistances);
                     gResult = fitGaussianToData(avgVectDistancesAsDouble, maxDistanceNm_);
                     if (doGaussianEstimate_) {
                        GaussianUtils.plotGaussian("Gaussian fit of: "
                             + dc.getSpotData(row).getName() + " distances",
                             avgVectDistancesAsDouble, maxDistanceNm_, gResult);
                     }
                  } catch (FittingException ex) {
                     // TODO
                  }

               }

               if (p2d_) {
                  List<Double> distancesToUse = allDistances; 
                  if (useVectorDistances_) {
                     distancesToUse = avgVectDistances;
                  }
                  double[] d = new double[distancesToUse.size()];
                  for (int j = 0; j < distancesToUse.size(); j++) {
                     d[j] = distancesToUse.get(j);
                  }
                  P2DFitter p2df = new P2DFitter(d, fitSigmaInP2D_, maxDistanceNm_);
                  double distMean = ListUtils.listAvg(distancesToUse);
                  double distStd = sigmaUserGuess_;
                  if (fitSigmaInP2D_ || !useSigmaUserGuess_) {
                     // how do we best estimate sigma? If we have multiple
                     // measurements per particle, it seems best to calculate it 
                     // directly from the spread in those measurements
                     // if we have only one particle per track, we need to
                     // calculate it from the sigmas of the two spots in the particle
                     // But where is the cutoff between these two methods?
                     // From sigmas of two spots:
                     // distStd = ListUtils.listAvg(avgSigmas);
                     //if (ListUtils.listAvg(trackLengths) > 3.0) {
                     //   distStd = ListUtils.listStdDev(avgToUse, distMean);
                     //} else {
                     distStd = ListUtils.listAvg(allSigmas);
                     //}
                  }
                  if (gResult != null && gResult.length == 2 && useVectorDistances_){
                     p2df.setStartParams(gResult[0], gResult[1]);
                  } else {
                     p2df.setStartParams(distMean, distStd);
                  }

                  try {
                     double[] p2dfResult = p2df.solve();
                     // Confidence interval calculation as in matlab code by Stirlink Churchman
                     double mu = p2dfResult[0];
                     double sigma = distStd;
                     if (fitSigmaInP2D_) {
                        sigma = p2dfResult[1];
                     }
                     double sigmaRange = 4.0 * sigma / Math.sqrt(d.length);
                     double resolution = 0.001 * sigma;
                     double[] distances;
                     distances = p2df.getDistances(mu - sigmaRange, resolution, mu + sigmaRange);
                     double[] logLikelihood = p2df.logLikelihood(p2dfResult, distances);

                     // Uncomment the following to plot loglikelihood
                     // XYSeries data = new XYSeries("distances(nm)");
                     // for (int i = 0; i < distances.length && i < logLikelihood.length; i++) {
                     //    data.add(distances[i], logLikelihood[i]);
                     // }
                     // GaussianUtils.plotData("Log Likelihood for " + dc.getSpotData(row).getName(), 
                     //                 data, "Distance (nm)", "Likelihood", 100, 100);
                     int indexOfMaxLogLikelihood = CalcUtils.maxIndex(logLikelihood);
                     int[] halfMax = CalcUtils.indicesToValuesClosest(logLikelihood,
                             logLikelihood[indexOfMaxLogLikelihood] - 0.5);
                     double dist1 = distances[halfMax[0]];
                     double dist2 = distances[halfMax[1]];
                     double lowConflim = mu - dist1;
                     double highConflim = dist2 - mu;
                     if (lowConflim < 0.0) {
                        lowConflim = mu - dist2;
                        highConflim = dist1 - mu;
                     }
                     String msg1 = "P2D fit for " + dc.getSpotData(row).getName();
                     String msg2 = "n = " + distancesToUse.size() + ", mu = "
                             + NumberUtils.doubleToDisplayString(mu, 2)
                             + " - "
                             + NumberUtils.doubleToDisplayString(lowConflim, 2)
                             + " + "
                             + NumberUtils.doubleToDisplayString(highConflim, 2)
                             + "  nm, sigma = "
                             + NumberUtils.doubleToDisplayString(sigma, 2)
                             + " nm, ";
                     MMStudio.getInstance().alerts().postAlert(msg1, null, msg2);

                     MMStudio.getInstance().alerts().postAlert("Gaussian distribution for "
                             + dc.getSpotData(row).getName(),
                             null,
                             "n = " + distancesToUse.size()
                             + ", avg = "
                             + NumberUtils.doubleToDisplayString(distMean, 2)
                             + " nm, std = "
                             + NumberUtils.doubleToDisplayString(distStd, 2) + " nm");

                     // plot function and histogram
                     double[] muSigma = {p2dfResult[0], sigma};
                     if (fitSigmaInP2D_) {
                        muSigma = p2dfResult;
                     }
                     GaussianUtils.plotP2D("P2D fit of: " + dc.getSpotData(row).getName() + " distances",
                             d, maxDistanceNm_, muSigma);

                     // The following is used to output results in a machine readable fashion
                     // Uncomment when needed:
                     rt3.incrementCounter();
                     rt3.addValue("Max. Dist.", maxDistanceNm_);
                     rt3.addValue("File", dc.getSpotData(row).getName());
                     String useVect = doGaussianEstimate_ ? "yes" : "no";
                     rt3.addValue("Vect. Dist.", useVect);
                     String fittedSigma = fitSigmaInP2D_ ? "yes" : "no";
                     rt3.addValue("Fit Sigma", fittedSigma);
                     String sigmaFromData = useSigmaUserGuess_ || fitSigmaInP2D_ ? "no" : "yes";
                     rt3.addValue("Sigma from data", sigmaFromData);
                     rt3.addValue("n", distancesToUse.size());
                     rt3.addValue("Frames", dc.getSpotData(row).nrFrames_);
                     rt3.addValue("Positions", dc.getSpotData(row).nrPositions_);
                     rt3.addValue("mu", mu);
                     rt3.addValue("mu-lowConf", lowConflim);
                     rt3.addValue("mu-highConf", highConflim);
                     rt3.addValue("sigma", muSigma[1]);
                     rt3.addValue("mean", distMean);
                     rt3.addValue("std", distStd);
                     rt3.show("P2D Summary");

                  } catch (FittingException fe) {
                     // ReportingUtils.showError(fe.getMessage());
                  } catch (TooManyEvaluationsException tmee) {
                     // ReportingUtils.showError(tmee.getMessage());
                  }
               }

               ij.IJ.showStatus("Done listing pairs");

            }
         }
      };

      if (showTrack_ || showSummary_ || showOverlay_ || saveFile_ || p2d_
              || doGaussianEstimate_) {
         (new Thread(doWorkRunnable)).start();
      }

   }

   /**
    * Cycles through the spots of the selected data set and finds the most
    * nearby spot in channel 2. It will list this as a pair if the two spots are
    * within MAXMATCHDISTANCE nm of each other. In addition, it will list the
    * average distance, and average distance in x and y for each frame.
    *
    * spots in channel 2 that are within MAXMATCHDISTANCE of
    *
    * @param row
    * @param maxDistance
    * @param showPairs
    * @param showImage
    * @param savePairs
    * @param filePath
    * @param showSummary
    * @param showGraph
    */
   public static void ListParticlePairs(final int row, final double maxDistance,
           final boolean showPairs, final boolean showImage,
           final boolean savePairs, final String filePath,
           final boolean showSummary, final boolean showGraph) {

      Runnable doWorkRunnable = new Runnable() {

         @Override
         public void run() {

            RowData spotData = DataCollectionForm.getInstance().getSpotData(row);

            ResultsTable rt = new ResultsTable();
            rt.reset();
            rt.setPrecision(2);

            ResultsTable rt2 = new ResultsTable();
            rt2.reset();
            rt2.setPrecision(2);

            int width = spotData.width_;
            int height = spotData.height_;
            double factor = spotData.pixelSizeNm_;
            boolean useS = spotData.useSeconds();
            ij.ImageStack stack = new ij.ImageStack(width, height);

            XYSeries xData = new XYSeries("XError");
            XYSeries yData = new XYSeries("YError");

            ij.IJ.showStatus("Creating Pairs...");

            for (int frame = 1; frame <= spotData.nrFrames_; frame++) {
               ij.IJ.showProgress(frame, spotData.nrFrames_);
               ImageProcessor ip = new ShortProcessor(width, height);
               short pixels[] = new short[width * height];
               ip.setPixels(pixels);
               stack.addSlice("frame: " + frame, ip);

               // Get points from both channels in each frame as ArrayLists        
               ArrayList<SpotData> gsCh1 = new ArrayList<SpotData>();
               ArrayList<Point2D.Double> xyPointsCh2 = new ArrayList<Point2D.Double>();
               for (SpotData gs : spotData.spotList_) {
                  if (gs.getFrame() == frame) {
                     if (gs.getChannel() == 1) {
                        gsCh1.add(gs);
                     } else if (gs.getChannel() == 2) {
                        Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                        xyPointsCh2.add(point);
                     }
                  }
               }

               if (xyPointsCh2.isEmpty()) {
                  ReportingUtils.logError("Pairs function in Localization plugin: no points found in second channel in frame " + frame);
                  continue;
               }

               // Find matching points in the two ArrayLists
               Iterator it2 = gsCh1.iterator();
               NearestPoint2D np = new NearestPoint2D(xyPointsCh2,
                       maxDistance);
               ArrayList<Double> distances = new ArrayList<Double>();
               ArrayList<Double> errorX = new ArrayList<Double>();
               ArrayList<Double> errorY = new ArrayList<Double>();
               while (it2.hasNext()) {
                  SpotData gs = (SpotData) it2.next();
                  Point2D.Double pCh1 = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
                  Point2D.Double pCh2 = np.findKDWSE(pCh1);
                  if (pCh2 != null) {
                     rt.incrementCounter();
                     rt.addValue(Terms.FRAME, gs.getFrame());
                     rt.addValue(Terms.SLICE, gs.getSlice());
                     rt.addValue(Terms.CHANNEL, gs.getSlice());
                     rt.addValue(Terms.POSITION, gs.getPosition());
                     rt.addValue(Terms.XPIX, gs.getX());
                     rt.addValue(Terms.YPIX, gs.getY());
                     rt.addValue("X1", pCh1.getX());
                     rt.addValue("Y1", pCh1.getY());
                     rt.addValue("X2", pCh2.getX());
                     rt.addValue("Y2", pCh2.getY());
                     double d2 = NearestPoint2D.distance2(pCh1, pCh2);
                     double d = Math.sqrt(d2);
                     rt.addValue("Distance", d);
                     rt.addValue("Orientation (sine)",
                             NearestPoint2D.orientation(pCh1, pCh2));
                     distances.add(d);

                     ip.putPixel((int) (pCh1.x / factor), (int) (pCh1.y / factor), (int) d);

                     double ex = pCh2.getX() - pCh1.getX();
                     //double ex = (pCh1.getX() - pCh2.getX()) * (pCh1.getX() - pCh2.getX());
                     //ex = Math.sqrt(ex);
                     errorX.add(ex);
                     //double ey = (pCh1.getY() - pCh2.getY()) * (pCh1.getY() - pCh2.getY());
                     //ey = Math.sqrt(ey);
                     double ey = pCh2.getY() - pCh1.getY();
                     errorY.add(ey);
                  }
               }
               Double avg = ListUtils.listAvg(distances);
               Double stdDev = ListUtils.listStdDev(distances, avg);
               Double avgX = ListUtils.listAvg(errorX);
               Double stdDevX = ListUtils.listStdDev(errorX, avgX);
               Double avgY = ListUtils.listAvg(errorY);
               Double stdDevY = ListUtils.listStdDev(errorY, avgY);
               rt2.incrementCounter();
               rt2.addValue("Frame Nr.", frame);
               rt2.addValue("Avg. distance", avg);
               rt2.addValue("StdDev distance", stdDev);
               rt2.addValue("X", avgX);
               rt2.addValue("StdDev X", stdDevX);
               rt2.addValue("Y", avgY);
               rt2.addValue("StdDevY", stdDevY);
               double timePoint = frame;
               if (spotData.timePoints_ != null) {
                  timePoint = spotData.timePoints_.get(frame);
                  if (useS) {
                     timePoint /= 1000;
                  }
               }
               xData.add(timePoint, avgX);
               yData.add(timePoint, avgY);
            }

            if (rt.getCounter() == 0) {
               MessageDialog md = new MessageDialog(DataCollectionForm.getInstance(),
                       "No Pairs found", "No Pairs found");
               return;
            }

            if (showSummary) {
               // show summary in resultstable
               rt2.show("Summary of Pairs found in " + spotData.getName());
            }

            if (showGraph) {
               String xAxis = "Time (frameNr)";
               if (spotData.timePoints_ != null) {
                  xAxis = "Time (ms)";
                  if (useS) {
                     xAxis = "Time (s)";
                  }
               }
               GaussianUtils.plotData2("Error in " + spotData.getName(),
                       xData, yData, xAxis, "Error(nm)", 0, 400);

               ij.IJ.showStatus("");
            }

            if (showPairs) {
               //  show Pairs panel and attach listener
               TextPanel tp;
               TextWindow win;

               String rtName = "Pairs found in " + spotData.getName();
               rt.show(rtName);
               ImagePlus siPlus = ij.WindowManager.getImage(spotData.title_);
               Frame frame = WindowManager.getFrame(rtName);
               if (frame != null && frame instanceof TextWindow && siPlus != null) {
                  win = (TextWindow) frame;
                  tp = win.getTextPanel();

                  // TODO: the following does not work, there is some voodoo going on here
                  for (MouseListener ms : tp.getMouseListeners()) {
                     tp.removeMouseListener(ms);
                  }
                  for (KeyListener ks : tp.getKeyListeners()) {
                     tp.removeKeyListener(ks);
                  }

                  ResultsTableListener myk = new ResultsTableListener(
                          MMStudio.getInstance(), spotData.dw_, siPlus,
                          rt, win, spotData.halfSize_);
                  tp.addKeyListener(myk);
                  tp.addMouseListener(myk);
                  frame.toFront();
               }
            }

            if (showImage) {
               ImagePlus sp = new ImagePlus("Errors in pairs");
               sp.setOpenAsHyperStack(true);
               sp.setStack(stack, 1, 1, stack.getSize());
               sp.setDisplayRange(0, 20);
               sp.setTitle(spotData.title_);

               ImageWindow w = new StackWindow(sp);
               w.setTitle("Error in " + spotData.getName());

               w.setImage(sp);
               w.setVisible(true);
            }

            if (savePairs) {
               try {
                  String fileName = filePath + File.separator
                          + spotData.getName() + "_Pairs.cvs";
                  rt.saveAs(fileName);
                  ij.IJ.log("Saved file: " + fileName);
               } catch (IOException ex) {
                  ReportingUtils.showError(ex, "Failed to save file");
               }
            }

         }
      };

      (new Thread(doWorkRunnable)).start();

   }

   /**
    * Fits a list of numbers to a Gaussian function using Maximum Likelihood
    *
    * @param input
    * @param max
    * @return fitresult
    * @throws FittingException
    */
   public static double[] fitGaussianToData(final double[] input, 
           final double max) throws FittingException {
      // fit vector distances with gaussian function

      Gaussian1DFitter gf = new Gaussian1DFitter(input, max);
      double avg = ListUtils.avg(input);
      gf.setStartParams(avg, ListUtils.stdDev(input, avg));
      return gf.solve();
   }

}