package org.micromanager.plugins.rtintensities;

import java.io.IOException;
import java.util.Map;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Assert;
import org.junit.Test;


public class TestCVSSaving {

   /**
    * Comprehensive roundtrip testing.
    */
   @Test
   public void testRoundTripXYCollection() throws IOException {
      System.out.println("Starting roundtrip test...\n");

      // Create test data with various edge cases
      final XYSeriesCollection originalCollection = new XYSeriesCollection();

      // Series 1: Regular data points
      XYSeries series1 = new XYSeries("Temperature");
      series1.add(1.0, 20.5);
      series1.add(2.0, 21.3);
      series1.add(3.0, 22.7);
      originalCollection.addSeries(series1);

      // Series 2: Sparse data points with gaps
      XYSeries series2 = new XYSeries("Humidity");
      series2.add(1.0, 65.0);
      series2.add(4.0, 68.1);  // Note gap in x values
      originalCollection.addSeries(series2);

      // Series 3: Negative values and decimals
      XYSeries series3 = new XYSeries("Pressure");
      series3.add(-1.5, -1013.2);
      series3.add(0.0, 0.0);
      series3.add(2.5, 1014.1);
      originalCollection.addSeries(series3);

      // Convert to CSV
      String csv = XYSeriesCollectionConverter.toCSV(
            originalCollection,
            "Test Name", "Roundtrip Test",
            "Edge Cases", "Gaps, negatives, zeros"
      );

      System.out.println("Original CSV:");
      System.out.println(csv);

      // Parse back into XYSeriesCollection
      XYSeriesCollectionConverter.CSVParseResult result = XYSeriesCollectionConverter.fromCSV(csv);
      final XYSeriesCollection parsedCollection = result.getCollection();

      // Verify metadata
      System.out.println("\nParsed Metadata:");
      for (Map.Entry<String, String> entry : result.getMetadata().entrySet()) {
         System.out.printf("%s: %s%n", entry.getKey(), entry.getValue());
      }

      // Verify series count
      System.out.println("\nVerifying series count...");
      assert originalCollection.getSeriesCount() == parsedCollection.getSeriesCount() :
            "Series count mismatch!";
      System.out.println("✓ Series count matches");

      // Verify each series
      for (int i = 0; i < originalCollection.getSeriesCount(); i++) {
         XYSeries originalSeries = originalCollection.getSeries(i);
         XYSeries parsedSeries = parsedCollection.getSeries(i);

         System.out.printf("\nVerifying series '%s'...%n", originalSeries.getKey());

         // Verify series key
         Assert.assertEquals(originalSeries.getKey(), parsedSeries.getKey());
         // Verify item count
         Assert.assertEquals(originalSeries.getItemCount(), parsedSeries.getItemCount());

         // Verify each data point
         for (int j = 0; j < originalSeries.getItemCount(); j++) {
            double originalX = originalSeries.getX(j).doubleValue();
            double originalY = originalSeries.getY(j).doubleValue();
            double parsedX = parsedSeries.getX(j).doubleValue();
            double parsedY = parsedSeries.getY(j).doubleValue();

            assert originalX == parsedX && originalY == parsedY :
                  "Data point mismatch!";
            Assert.assertEquals(parsedX, originalX, 0.00001);
         }
         System.out.println("✓ All data points match");
      }

      System.out.println("\nAll tests passed successfully!");
   }

}
