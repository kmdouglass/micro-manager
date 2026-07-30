package org.micromanager.plugins.isim;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;

/**
 * Draws the iSIM alignment overlay on the live view.
 * - A cyan crosshair: one reference line and a second line perpendicular to it
 * - Red cross markers at detected spot positions
 */
public class AlignmentOverlay extends AbstractOverlay {
   private static final Color REFERENCE_LINE_COLOR = Color.CYAN;
   private static final Color SPOT_COLOR = Color.RED;
   private static final BasicStroke OVERLAY_STROKE = new BasicStroke(1.5f);
   private static final int CROSS_ARM_PX = 5;

   private final AlignmentModel model_;

   // Cache for reference-line endpoints in image coordinates.
   // The cache key is (alpha, offsetA, offsetB, imageWidth, imageHeight).
   // All reads and writes occur on the EDT.
   private List<double[][]> cachedLines_ = null; // each element: {{x1,y1},{x2,y2}}
   private double cacheAlpha_ = Double.NaN;
   private double cacheOffsetA_ = Double.NaN;
   private double cacheOffsetB_ = Double.NaN;
   private int cacheImgW_ = -1;
   private int cacheImgH_ = -1;

   // Volatile: written from EDT (via updateSpots), read from EDT (in paintOverlay).
   private volatile List<Point2D.Double> detectedSpots_ = Collections.emptyList();

   public AlignmentOverlay(AlignmentModel model) {
      model_ = model;
   }

   @Override
   public String getTitle() {
      return "iSIM Alignment";
   }

   /**
    * Updates the detected spot positions and triggers a repaint.
    * Must be called on the EDT.
    */
   public void updateSpots(List<Point2D.Double> spots) {
      detectedSpots_ = Collections.unmodifiableList(new ArrayList<>(spots));
      fireOverlayConfigurationChanged();
   }

   /**
    * Triggers a repaint of the overlay. Called by AlignmentPanel when
    * reference line parameters change.
    */
   public void notifyChanged() {
      fireOverlayConfigurationChanged();
   }

   @Override
   public void paintOverlay(
         java.awt.Graphics2D g,
         Rectangle screenRect,
         DisplaySettings displaySettings,
         List<Image> images,
         Image primaryImage,
         Rectangle2D.Float imageViewPort) {

      if (primaryImage == null) {
         return;
      }
      if (imageViewPort.width == 0 || imageViewPort.height == 0) {
         return;
      }

      int imageWidth = primaryImage.getWidth();
      int imageHeight = primaryImage.getHeight();

      double scaleX = screenRect.width / (double) imageViewPort.width;
      double scaleY = screenRect.height / (double) imageViewPort.height;

      g.setStroke(OVERLAY_STROKE);

      drawReferenceLines(g, screenRect, imageViewPort, imageWidth, imageHeight,
            scaleX, scaleY);
      drawSpotMarkers(g, screenRect, imageViewPort, scaleX, scaleY);
   }

   /**
    * Draws the cyan crosshair (two perpendicular lines) across the image.
    *
    * <p>Line endpoints in image coordinates are cached by model parameters and image
    * dimensions, since these rarely change between frames. Only the final screen-coordinate
    * transform is recomputed each frame.
    */
   private void drawReferenceLines(
         java.awt.Graphics2D g,
         Rectangle screenRect,
         Rectangle2D.Float imageViewPort,
         int imageWidth,
         int imageHeight,
         double scaleX,
         double scaleY) {

      double alpha = model_.getAngleRad();
      double offsetA = model_.getOffsetA();
      double offsetB = model_.getOffsetB();

      // Recompute image-space endpoints only when the geometry-defining inputs change.
      if (cachedLines_ == null
            || Double.compare(alpha, cacheAlpha_) != 0
            || Double.compare(offsetA, cacheOffsetA_) != 0
            || Double.compare(offsetB, cacheOffsetB_) != 0
            || imageWidth != cacheImgW_
            || imageHeight != cacheImgH_) {

         cachedLines_ = computeLineEndpoints(alpha, offsetA, offsetB, imageWidth, imageHeight);
         cacheAlpha_ = alpha;
         cacheOffsetA_ = offsetA;
         cacheOffsetB_ = offsetB;
         cacheImgW_ = imageWidth;
         cacheImgH_ = imageHeight;
      }

      g.setColor(REFERENCE_LINE_COLOR);
      for (double[][] line : cachedLines_) {
         int sx1 = toScreenX(line[0][0], screenRect, imageViewPort, scaleX);
         int sy1 = toScreenY(line[0][1], screenRect, imageViewPort, scaleY);
         int sx2 = toScreenX(line[1][0], screenRect, imageViewPort, scaleX);
         int sy2 = toScreenY(line[1][1], screenRect, imageViewPort, scaleY);
         g.drawLine(sx1, sy1, sx2, sy2);
      }
   }

   /**
    * Computes image-space endpoints for the two crosshair lines that cross the image.
    *
    * <p>Each line is defined in image coordinates by the equation:
    *   x * nx + y * ny = offset
    * where (nx, ny) = (-sin(angle), cos(angle)) is the line normal. Line A uses
    * {@code alpha} and {@code offsetA}; line B is perpendicular to line A (angle
    * {@code alpha + PI/2}) and uses {@code offsetB}. Each line is intersected with
    * all four image edges to find its two endpoints.
    *
    * @return list of line endpoint pairs, each element {{x1,y1},{x2,y2}}; a line is
    *     omitted if it does not cross the image (fewer than two edge intersections)
    */
   private List<double[][]> computeLineEndpoints(
         double alpha, double offsetA, double offsetB,
         int imageWidth, int imageHeight) {

      List<double[][]> lines = new ArrayList<>(2);
      double[][] lineA = computeSingleLineEndpoints(alpha, offsetA, imageWidth, imageHeight);
      if (lineA != null) {
         lines.add(lineA);
      }
      double[][] lineB = computeSingleLineEndpoints(
            alpha + Math.PI / 2, offsetB, imageWidth, imageHeight);
      if (lineB != null) {
         lines.add(lineB);
      }
      return lines;
   }

   /**
    * Computes the image-space endpoints of a single line of the given angle and offset,
    * or {@code null} if the line does not cross the image.
    */
   private double[][] computeSingleLineEndpoints(
         double angle, double offset, int imageWidth, int imageHeight) {

      double nx = -Math.sin(angle);
      double ny = Math.cos(angle);

      // Intersect line (x*nx + y*ny = offset) with the four image edges.
      List<double[]> intersections = new ArrayList<>(4);
      addEdgeIntersection(intersections, nx, ny, offset,
            0, 0, imageWidth, 0);                      // top edge: y=0
      addEdgeIntersection(intersections, nx, ny, offset,
            0, imageHeight, imageWidth, imageHeight);   // bottom edge: y=H
      addEdgeIntersection(intersections, nx, ny, offset,
            0, 0, 0, imageHeight);                     // left edge: x=0
      addEdgeIntersection(intersections, nx, ny, offset,
            imageWidth, 0, imageWidth, imageHeight);    // right edge: x=W

      if (intersections.size() < 2) {
         return null;
      }

      // Use the first and last intersection points to get a consistent pair.
      double[] p1 = intersections.get(0);
      double[] p2 = intersections.get(intersections.size() - 1);
      return new double[][]{{p1[0], p1[1]}, {p2[0], p2[1]}};
   }

   /**
    * Finds the intersection of the line (x*nx + y*ny = lineD) with an image
    * edge segment and adds it to the list if the intersection lies on the segment.
    *
    * @param intersections accumulator
    * @param nx line normal
    * @param ny line normal
    * @param lineD line offset
    * @param x1 edge segment endpoints (axis-aligned)
    * @param y1 edge segment endpoints (axis-aligned)
    * @param x2 edge segment endpoints (axis-aligned)
    * @param y2 edge segment endpoints (axis-aligned)
    */
   private void addEdgeIntersection(
         List<double[]> intersections,
         double nx, double ny, double lineD,
         double x1, double y1, double x2, double y2) {

      // Horizontal edge (y is constant): x = (lineD - ny*y) / nx
      if (y1 == y2 && Math.abs(nx) > 1e-10) {
         double x = (lineD - ny * y1) / nx;
         if (x >= Math.min(x1, x2) && x <= Math.max(x1, x2)) {
            intersections.add(new double[]{x, y1});
         }
      }
      // Vertical edge (x is constant): y = (lineD - nx*x) / ny
      if (x1 == x2 && Math.abs(ny) > 1e-10) {
         double y = (lineD - nx * x1) / ny;
         if (y >= Math.min(y1, y2) && y <= Math.max(y1, y2)) {
            intersections.add(new double[]{x1, y});
         }
      }
   }

   /**
    * Draws red cross markers at detected spot positions.
    */
   private void drawSpotMarkers(
         java.awt.Graphics2D g,
         Rectangle screenRect,
         Rectangle2D.Float imageViewPort,
         double scaleX,
         double scaleY) {

      g.setColor(SPOT_COLOR);
      List<Point2D.Double> spots = detectedSpots_;

      for (Point2D.Double spot : spots) {
         int sx = toScreenX(spot.x, screenRect, imageViewPort, scaleX);
         int sy = toScreenY(spot.y, screenRect, imageViewPort, scaleY);
         g.drawLine(sx - CROSS_ARM_PX, sy, sx + CROSS_ARM_PX, sy);
         g.drawLine(sx, sy - CROSS_ARM_PX, sx, sy + CROSS_ARM_PX);
      }
   }

   private int toScreenX(double imageX, Rectangle screenRect,
         Rectangle2D.Float imageViewPort, double scaleX) {
      return (int) Math.round(screenRect.x + (imageX - imageViewPort.x) * scaleX);
   }

   private int toScreenY(double imageY, Rectangle screenRect,
         Rectangle2D.Float imageViewPort, double scaleY) {
      return (int) Math.round(screenRect.y + (imageY - imageViewPort.y) * scaleY);
   }
}
