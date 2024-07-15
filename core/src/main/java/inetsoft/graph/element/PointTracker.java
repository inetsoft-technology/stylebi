/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.graph.element;

import inetsoft.graph.GGraph;
import inetsoft.graph.scale.Scale;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * This class tracks points that fall on the same point.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class PointTracker {
   /**
    * @param maxCount the number of points that fall on the same position
    * before being ignored.
    */
   public PointTracker(GraphElement elem, GGraph graph, int maxCount) {
      this.maxCount = maxCount;

      if(elem.getDimCount() > 0) {
         Scale scale = graph.getScale(elem.getDim(elem.getDimCount() - 1));

         if(scale != null) {
            dimRange = scale.getMax() - scale.getMin();
         }
      }

      varRanges = new double[elem.getVarCount()];

      for(int i = 0; i < elem.getVarCount(); i++) {
         Scale scale = graph.getScale(elem.getVar(i));

         if(scale != null) {
            varRanges[i] = scale.getMax() - scale.getMin();
         }
         else {
            varRanges[i] = 1;
         }
      }

      points.defaultReturnValue(0);
   }

   /**
    * Check if the point should be included in the chart.
    * @param vidx var index.
    * @return true to include the point, and false to ignore.
    */
   public boolean checkPoint(double[] tuple, int vidx) {
      if(tuple.length < 2) {
         return true;
      }

      PointLoc loc = new PointLoc(tuple, vidx);
      int cnt = points.getInt(loc);

      // ignore point if the overlapping exceeds maxCount.
      // don't ignore if there are only small number of points
      if(cnt > maxCount && pointCount > 10000) {
         if(firstIgnored) {
            LOG.debug("Ignore over-crowded points on point chart");
            firstIgnored = false;
         }

         return false;
      }

      points.put(loc, cnt + 1);
      pointCount++;

      return true;
   }

   private class PointLoc {
      /**
       * @param vidx var index.
       */
      public PointLoc(double[] tuple, int vidx) {
         // round the point x/y to unit of 1000 per direction, so if points
         // are close enough, they are considered to be on the same location
         tuple = tuple.clone();
         tuple[tuple.length - 2] = (int) (tuple[tuple.length - 2] * 2000 / dimRange);
         tuple[tuple.length - 1] = (int) (tuple[tuple.length - 1] * 2000 / varRanges[vidx]);
         this.tuple = tuple;
      }

      @Override
      public boolean equals(Object obj) {
         PointLoc loc = (PointLoc) obj;
         return Arrays.equals(tuple, loc.tuple);
      }

      public int hashCode() {
         return Arrays.hashCode(tuple);
      }

      private double[] tuple;
   }

   private double dimRange = 1; // value range for the point dim
   private double[] varRanges = {}; // value range for the point vars
   private int maxCount;
   private int pointCount = 0;
   // loc -> count
   private Object2IntMap<PointLoc> points = new Object2IntOpenHashMap<>();
   private boolean firstIgnored = true;

   private static final Logger LOG = LoggerFactory.getLogger(PointTracker.class);
}
