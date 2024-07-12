/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.guide.form;

import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates Quantiles
 */
public class QuantileStrategy extends TargetStrategy {
   /**
    * Convenience constructor.
    */
   public QuantileStrategy() {
      // Accept Defaults
   }

   /**
    * Initialize with a number of quantiles.
    */
   public QuantileStrategy(int numberOfQuantiles) {
      this.numberOfQuantiles = numberOfQuantiles;
   }

   /**
    * Generate the runtime boundary values in original order.
    */
   @Override
   protected double[] getRuntimeBoundaries(double[] data) {
      int qCount = numberOfQuantiles;

      return getQuantiles(data, qCount);
   }

   /**
    * Gets the label(for description).
    */
   public static String getGenericLabel() {
      return Catalog.getCatalog().getString("Quantiles");
   }

   /**
    * Generate labels for boundary lines using the line boundaries.
    */
   @Override
   protected String[] generateDefaultLabels(double[] bandBoundaries, boolean dateTarget,
                                            boolean timeTarget)
   {
      String[] labels = new String[bandBoundaries.length];

      // Put labels such that they reside inside the appropriate quantile
      // Remember Labels are drawn above the line
      for(int i = 0; i < labels.length; i++){
         labels[i] = Catalog.getCatalog().getString("Quantile") +
            " (" + (i + 1) + ")";
      }

      return labels;
   }

   /**
    * @return the number of quantiles the data will be split into.
    */
   public int getNumberOfQuantiles() {
      return numberOfQuantiles;
   }

   /**
    * Set the number of qunatiles to split the data into.
    */
   public void setNumberOfQuantiles(int numberOfQuantiles) {
      this.numberOfQuantiles = numberOfQuantiles;
   }

   /**
    * Calculates quantile boundaries by splitting the 0-100 percentile field
    * into a number of equally spaced quantiles, and calculating the percentiles
    * @param sample the dataset to split.
    * @param quantiles the number of equally sized quantiles.
    * @return Quantile boundaries.
    */
   private static double[] getQuantiles(double[] sample, int quantiles) {
      if(quantiles < MIN_QUANTILE_COUNT) {
         return new double[0];
      }
      else if(quantiles > MAX_QUANTILE_COUNT) {
         LOG.warn(
            "Number of quantiles requested was greater than " +
            MAX_QUANTILE_COUNT + ". Generating only " + MAX_QUANTILE_COUNT +
            " quantiles.");
         quantiles = MAX_QUANTILE_COUNT;
      }

      double[] percentiles = new double[quantiles - 1];
      double quantileSize = 100.0 / quantiles;

      // Quantiles are equivalent to equally spaced Percentiles
      for(int i = 0; i < percentiles.length; i++) {
         percentiles[i] = quantileSize * (i + 1);
      }

      // Use the equally spaced percentiles to generate Quantiles
      return TargetUtil.getPercentiles(sample, percentiles);
   }

   // The number of quantiles to split the data into
   private int numberOfQuantiles = 4;
   private static final int MAX_QUANTILE_COUNT = 10;
   private static final int MIN_QUANTILE_COUNT = 2;
   private static final long serialVersionUID = 1L;
   private static Logger LOG = LoggerFactory.getLogger(QuantileStrategy.class);
}
