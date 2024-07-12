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

import inetsoft.report.filter.MaxFormula;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.util.*;

/**
 * Calculates percentiles
 */
public class PercentageStrategy extends TargetStrategy {
   /**
    * Convenience Constructor
    */
   public PercentageStrategy() {
      aggregate.setFormula(new MaxFormula());
   }

   /**
    * Collection constructor
    */
   public PercentageStrategy(Collection<Double> percentages,
                             TargetParameter aggregate)
   {
      this();
      this.percentages.addAll(percentages);

      if(aggregate != null) {
         this.aggregate = aggregate;
      }
   }

   /**
    * Generate the runtime boundary values in original order
    *
    * @return the calculated boundaries of the target bands
    */
   @Override
   protected double[] getRuntimeBoundaries(double[] data) {
      double total = aggregate.getRuntimeValue(data);
      Double[] runtimeVals = reorderedPercentages.toArray(new Double[0]);

      return getPercentages(runtimeVals, total);
   }

   @Override
   protected void reorderValues(Integer[] indices) {
      reorderedPercentages = reorderList(indices, percentages);
   }

   @Override
   protected void resetValues() {
      reorderedPercentages = percentages;
   }

   /**
    * Generate labels for boundary lines using the line boundaries
    */
   @Override
   protected String[] generateDefaultLabels(double[] bandBoundaries, boolean dateTarget,
                                            boolean timeTarget)
   {
      String[] labels = new String[bandBoundaries.length];
      Double[] pctgs = new Double[reorderedPercentages.size()];
      pctgs = reorderedPercentages.toArray(pctgs);

      for(int i = 0; i < labels.length; i++){
         labels[i] = Tool.toString(pctgs[i]) + "%"
         + " " + Catalog.getCatalog().getString("of") + " " + aggregate;
      }

      return labels;
   }

   /**
    * Calculates percentages of the given aggregate value.
    * @param args The desired percentages to calculate, and the aggregate value
    *             in the first slot
    * @return Array of calculated values
    */
   private static double[] getPercentages(double[] args, double aggregate) {
      double[] percentages = new double[args.length];

      // Percentages are simply a percentage of the aggregate value.
      for(int i = 0; i < args.length; i++) {
         percentages[i] = (args[i] / 100) * aggregate;
      }

      return percentages;
   }

   /**
    * Calculates percentages of the given aggregate value.
    * @param args The desired percentages to calculate, and the aggregate value
    *             in the first slot
    * @return Array of calculated values
    */
   private static double[] getPercentages(Double[] args, double aggregate) {
      return getPercentages(TargetUtil.getNonNulls(args), aggregate);
   }

   // The percentages we want to take.
   private List<Double> percentages = new ArrayList<>();
   private List<Double> reorderedPercentages = percentages;
   // The value from which we are taking percentages.
   private TargetParameter aggregate = new TargetParameter();
   private static final long serialVersionUID = 1L;
}
