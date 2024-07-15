/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.guide.form;

import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates percentiles
 */
public class PercentileStrategy extends TargetStrategy {
   /**
    * Convenience Constructor.
    */
   public PercentileStrategy() {
   }

   /**
    * Build from an array of Strings.
    */
   public PercentileStrategy(List<Double> percentiles) {
      this.percentiles.addAll(percentiles);
   }

   /**
    * Calculates percentile values from the sample.
    * @param sample the dataset.
    * @param args the desired percentiles to calculate.
    * @return The calculated percentile values.
    */
   private static double[] getPercentiles(double[] sample, Double[] args) {
      return TargetUtil.getPercentiles(sample, TargetUtil.getNonNulls(args));
   }

   /**
    * Generate the runtime boundary values in original order.
    */
   @Override
   protected double[] getRuntimeBoundaries(double[] data) {
      return getPercentiles(data, reorderedPercentiles.toArray(new Double[0]));
   }

   @Override
   protected void reorderValues(Integer[] indices) {
      reorderedPercentiles = reorderList(indices, percentiles);
   }

   @Override
   protected void resetValues() {
      reorderedPercentiles = percentiles;
   }

   /**
    * Generate labels for boundary lines using the line boundaries.
    */
   @Override
   protected String[] generateDefaultLabels(double[] bandBoundaries, boolean dateTarget,
                                            boolean timeTarget)
   {
      String[] labels = new String[bandBoundaries.length];
      Double[] pctles = new Double[reorderedPercentiles.size()];
      pctles = reorderedPercentiles.toArray(pctles);

      for(int i = 0; i < labels.length; i++){
         labels[i] = Tool.toString(pctles[i]) + " " +
            Catalog.getCatalog().getString("Percentile");
      }

      return labels;
   }

   private List<Double> percentiles = new ArrayList<>();
   private List<Double> reorderedPercentiles = percentiles;
   private static final long serialVersionUID = 1L;
}
