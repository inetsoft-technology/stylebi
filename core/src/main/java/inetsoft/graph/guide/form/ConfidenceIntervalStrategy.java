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
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates a confidence interval based on a confidence level
 */
public class ConfidenceIntervalStrategy extends TargetStrategy {
   /**
    * Convenience constructor.
    */
   public ConfidenceIntervalStrategy() {
      // Accept defaults
   }

   /**
    * Construct with the confidence level.
    */
   public ConfidenceIntervalStrategy(double confidenceLevel) {
      setConfidenceLevel(confidenceLevel);
   }

   /**
    * Generate the runtime boundary values in original order.
    */
   @Override
   protected double[] getRuntimeBoundaries(double[] data) {
      if(confidenceLevel <= 0) {
         return new double[0];
      }

      return getConfidenceInterval(data, confidenceLevel / 100);
   }

   /**
    * Generate labels for boundary lines using the line boundaries.
    */
   @Override
   protected String[] generateDefaultLabels(double[] bandBoundaries, boolean dateTarget,
                                            boolean timeTarget)
   {
      String[] labels = new String[bandBoundaries.length];

      // Every label should just say XX% Confidence interval
      for(int i = 0; i < labels.length; i++) {
         labels[i] = getGenericLabel();
      }

      return labels;
   }

   /**
    * Gets the generic confidence interval label.
    */
   public String getGenericLabel() {
      return getGenericLabel(confidenceLevel + "");
   }

   /**
    * Gets the label for the specified line.
    */
   public static String getGenericLabel(String confidenceLevel) {
      return Catalog.getCatalog().getString("Confidence Interval") +
         " (" + confidenceLevel + "%)";
   }

   /**
    * Set the confidence level.
    */
   public void setConfidenceLevel(double confidenceLevel) {
      this.confidenceLevel = confidenceLevel;
   }

   /**
    * @return the current confidence level.
    */
   public double getConfidenceLevel() {
      return confidenceLevel;
   }

   /**
    * Calculates a confidence interval for the population mean.  This method
    * assumes that the data given is a sample of a larger population with
    * unknown mean and unknown standard deviation.
    *
    * This method assumes a normal distribution of the population
    *
    * @param sample The sample data from which to derive the interval
    * @param confidenceLevel The desired level of confidence that the population
    *                        mean lies within the returned interval
    * @return A confidence interval for the population mean defined by upper
    * and lower bounds
    */
   private static double[] getConfidenceInterval(double[] sample, double confidenceLevel) {
      if(sample.length < 2 || Double.isNaN(confidenceLevel)) {
         // No confidence interval can be computed for an empty sample
         // Therefore, returning an empty set of lines is appropriate
         return new double[0];
      }

      if(confidenceLevel <= 0) {
         LOG.warn("Confidence level was 0% or less.  Using " + 0.1 + " instead.");
         confidenceLevel = 0.1;
      }
      else if(confidenceLevel >= 1) {
         LOG.warn("Confidence level was 100% or more.  Using " +
            Math.nextAfter(100, Double.NEGATIVE_INFINITY) + " instead.");
         confidenceLevel = Math.nextAfter(1, Double.NEGATIVE_INFINITY);
      }

      double[] bounds = new double[2];

      // Get t value for sample size and desired confidence level 't'
      double x0 = confidenceLevel + (1 - confidenceLevel) / 2;
      TDistribution tdist = new TDistribution(sample.length - 1);
      double tvalue = tdist.inverseCumulativeProbability(x0);

      // Get sample statistics mean and stdev
      SummaryStatistics s = new SummaryStatistics();

      for(double d :  sample) {
         if(d == -Double.MAX_VALUE) {
            continue;
         }

         s.addValue(d);
      }

      double sampleStdv = s.getStandardDeviation(); // sample std deviation 's'
      double sampleMean = s.getMean(); // sample mean 'm'
      double sqrtOfSampleSize = Math.sqrt(sample.length); // sqrt of sample size n

      // Get confidence interval as: m +/- t * (s / sqrt(n))
      double intermediateResult = tvalue * (sampleStdv / sqrtOfSampleSize);
      bounds[0] = sampleMean - intermediateResult; // Lower bound (-)
      bounds[1] = sampleMean + intermediateResult; // Upper bound (+)

      return  bounds;
   }

   private double confidenceLevel = 95;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG =
      LoggerFactory.getLogger(ConfidenceIntervalStrategy.class);
}
