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
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import java.util.*;

/**
 * Calculates Standard Deviations
 */
public class StandardDeviationStrategy extends TargetStrategy{
   /**
    * Convenience constructor.
    */
   public StandardDeviationStrategy() {
   }

   /**
    * Set whether this is a sample or population standard deviation
    */
   public void setSample(boolean value) {
      isSample = value;
   }

   /**
    * Get whether this is a sample or population standard deviation
    */
   public boolean isSample() {
      return isSample;
   }

   /**
    * Build from a collection of Doubles
    */
   public StandardDeviationStrategy(Collection<Double> factors,
                                    boolean isSample)
   {
      this.factors.addAll(factors);
      this.isSample = isSample;
   }

   /**
    * Generate the runtime boundary values in original order.
    */
   @Override
   protected double[] getRuntimeBoundaries(double[] data) {
      return getStandardDeviations(data, reorderedFactors.toArray(new Double[0]), isSample);
   }

   @Override
   protected void reorderValues(Integer[] indices) {
      reorderedFactors = reorderList(indices, factors);
   }

   @Override
   protected void resetValues() {
      reorderedFactors = factors;
   }

   /**
    * Generate labels for boundary lines using the line boundaries.
    */
   @Override
   protected String[] generateDefaultLabels(double[] bandBoundaries, boolean dateTarget,
                                            boolean timeTarget)
   {
      String[] labels = new String[bandBoundaries.length];
      Double[] devs = new Double[reorderedFactors.size()];
      devs = reorderedFactors.toArray(devs);

      for(int i = 0; i < labels.length; i++) {
         labels[i] = Catalog.getCatalog().getString("Standard Deviation") + " (" + devs[i] + ")";
      }

      return labels;
   }

   /**
    * Calculates Bands based on multiples of standard deviation.
    * @param args the multiples to use.
    */
   private static double[] getStandardDeviations(double[] samples, double[] args,
                                                 boolean isBiasCorrected)
   {
      if(args.length == 0) {
         return new double[0];
      }

      double[] bounds = new double[args.length];

      SummaryStatistics s = new SummaryStatistics();
      Variance variance = (Variance) s.getVarianceImpl();
      variance.setBiasCorrected(isBiasCorrected);

      for(double sample : samples) {
         if(sample != -Double.MAX_VALUE) {
            s.addValue(sample);
         }
      }

      double standardDeviation = s.getStandardDeviation();
      double mean = s.getMean();

      for(int i = 0; i < args.length; i++) {
         bounds[i] = mean + args[i] * standardDeviation;
      }

      return bounds;
   }

   /**
    * Calculates Bands based on multiples of standard deviation.
    * @param args the multiples to use.
    */
   private static double[] getStandardDeviations(double[] samples, Double[] args, boolean isSample)
   {
      return getStandardDeviations(samples, TargetUtil.getNonNulls(args), isSample);
   }

   // The percentages we want to take.
   private List<Double> factors = new ArrayList<>();
   private List<Double> reorderedFactors = factors;

   /**
    * If true, this calculates a Population standard deviation.  Otherwise,
    * it calculates the Sample standard deviation
    */
   private boolean isSample = true;
   private static final long serialVersionUID = 1L;
}
