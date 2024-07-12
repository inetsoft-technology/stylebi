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
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class contains methods used to calculate the bounds of target bands
 * and target lines for the Graph Target feature
 */
class TargetUtil {
   /**
    * Calculates percentile values from the sample
    * @param sample the dataset
    * @param args the desired percentiles to calculate
    * @return The calculated percentile values
    */
   static double[] getPercentiles(double[] sample, double[] args) {
      if(sample.length == 0) {
         return new double[0];
      }

      double[] percentiles = new double[args.length];

      // Calculate the percentiles
      Percentile p = new Percentile();
      p.setData(sample);

      for(int i = 0; i < percentiles.length; i++) {
         if(args[i] <= 0.0 || args[i] > 100.0) {
            LOG.warn(Catalog.getCatalog().getString("designer.chartProp.percentileRange",
                                                       args[i]));
            args[i] = Math.max(0, Math.min(100, args[i]));
         }

         percentiles[i] = p.evaluate(args[i]);
      }

      return percentiles;
   }

   /**
    * This is a utility method used in processing data for several strategies
    * @return a primitive array of unique non nulls given the input array
    */
   static double[] getNonNulls(Double[] input) {
      List<Double> nonNulls = new ArrayList<>();

      // Extract unique non-null values
      for(Double objVal : input) {
         if(objVal != null) {
            nonNulls.add(objVal);
         }
      }

      double[] primitives = new double[nonNulls.size()];
      Iterator<Double> iter = nonNulls.iterator();

      // Add to primitives array
      for(int i = 0; i < primitives.length; i++) {
         primitives[i] = iter.next();
      }

      return primitives;
   }

   private static final Logger LOG = LoggerFactory.getLogger(TargetUtil.class);
}
