package org.micromanager.plugins.isim;

import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Data model for the alignment tool. All fields are persisted via the MM user
 * profile and loaded/saved explicitly.
 */
public class AlignmentModel {
   private static final String KEY_ANGLE_DEG = "angleDeg";
   private static final String KEY_OFFSET_A = "offsetLineAPx";
   private static final String KEY_OFFSET_B = "offsetLineBPx";
   private static final String KEY_DETECTION_ENABLED = "detectionEnabled";
   private static final String KEY_THRESHOLD = "threshold";
   private static final String KEY_WINDOW_PX = "windowPx";

   private static final double DEFAULT_ANGLE_DEG = -4.13;
   private static final boolean DEFAULT_DETECTION_ENABLED = false;
   private static final int DEFAULT_THRESHOLD = 500;
   private static final int DEFAULT_WINDOW_PX = 20;

   private final MutablePropertyMapView settings_;

   private double angleDeg_;
   private double angleRad_;
   private double offsetA_;
   private double offsetB_;
   private boolean detectionEnabled_;
   // Volatile: written from EDT (spinner listeners), read from detection thread.
   private volatile int threshold_;
   private volatile int windowPx_;

   /**
    * Constructs the alignment model, loading persisted settings.
    *
    * @param settings persisted settings view
    * @param defaultImageWidth current camera image width in pixels, used only to compute
    *     the center-crossing default offset the first time each line's offset is read
    *     (i.e. when no value has been persisted yet)
    * @param defaultImageHeight current camera image height in pixels, used only for the
    *     same first-time default computation
    */
   public AlignmentModel(MutablePropertyMapView settings,
         int defaultImageWidth, int defaultImageHeight) {
      settings_ = settings;
      angleDeg_ = settings_.getDouble(KEY_ANGLE_DEG, DEFAULT_ANGLE_DEG);
      angleRad_ = Math.toRadians(angleDeg_);
      double defaultOffsetA =
            centerOffsetForAngle(angleRad_, defaultImageWidth, defaultImageHeight);
      offsetA_ = settings_.getDouble(KEY_OFFSET_A, defaultOffsetA);
      double defaultOffsetB =
            centerOffsetForAngle(angleRad_ + Math.PI / 2, defaultImageWidth, defaultImageHeight);
      offsetB_ = settings_.getDouble(KEY_OFFSET_B, defaultOffsetB);
      detectionEnabled_ = settings_.getBoolean(KEY_DETECTION_ENABLED, DEFAULT_DETECTION_ENABLED);
      threshold_ = settings_.getInteger(KEY_THRESHOLD, DEFAULT_THRESHOLD);
      windowPx_ = settings_.getInteger(KEY_WINDOW_PX, DEFAULT_WINDOW_PX);
   }

   /**
    * Returns the perpendicular distance from the image origin to the line at the given
    * angle that passes through the center of an image of the given size.
    */
   private static double centerOffsetForAngle(double angleRad, int imageWidth, int imageHeight) {
      double nx = -Math.sin(angleRad);
      double ny = Math.cos(angleRad);
      return (imageWidth / 2.0) * nx + (imageHeight / 2.0) * ny;
   }

   public void save() {
      settings_.putDouble(KEY_ANGLE_DEG, angleDeg_);
      settings_.putDouble(KEY_OFFSET_A, offsetA_);
      settings_.putDouble(KEY_OFFSET_B, offsetB_);
      settings_.putBoolean(KEY_DETECTION_ENABLED, detectionEnabled_);
      settings_.putInteger(KEY_THRESHOLD, threshold_);
      settings_.putInteger(KEY_WINDOW_PX, windowPx_);
   }

   public double getAngleDeg() {
      return angleDeg_;
   }

   public void setAngleDeg(double angleDeg) {
      angleDeg_ = angleDeg;
      angleRad_ = Math.toRadians(angleDeg);
   }

   public double getAngleRad() {
      return angleRad_;
   }

   public double getOffsetA() {
      return offsetA_;
   }

   public void setOffsetA(double offsetA) {
      offsetA_ = offsetA;
   }

   public double getOffsetB() {
      return offsetB_;
   }

   public void setOffsetB(double offsetB) {
      offsetB_ = offsetB;
   }

   public boolean isDetectionEnabled() {
      return detectionEnabled_;
   }

   public void setDetectionEnabled(boolean detectionEnabled) {
      detectionEnabled_ = detectionEnabled;
   }

   public int getThreshold() {
      return threshold_;
   }

   public void setThreshold(int threshold) {
      threshold_ = threshold;
   }

   public int getWindowPx() {
      return windowPx_;
   }

   public void setWindowPx(int windowPx) {
      windowPx_ = windowPx;
   }
}
