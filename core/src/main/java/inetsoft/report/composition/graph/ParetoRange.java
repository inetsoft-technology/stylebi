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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.scale.LinearRange;
import inetsoft.graph.scale.StackRange;

/**
 * Pareto is a combination of normal range (bar) and stack range (line).
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ParetoRange extends StackRange {
   @Override
   public double[] calculate(DataSet data, String[] cols, GraphtDataSelector selector) {
      setStackNegative(false);
      double[] range = super.calculate(data, cols, selector);
      double[] range2 = new LinearRange().calculate(data, cols, selector);

      double min = Double.MAX_VALUE;
      double max = 0;

      if(!Double.isNaN(range[0])) {
         min = Math.min(min, range[0]);
         max = Math.max(max, range[1]);
      }

      if(!Double.isNaN(range2[0])) {
         min = Math.min(min, range2[0]);
         max = Math.max(max, range2[1]);
      }

      if(min == Double.MAX_VALUE) {
         return new double[] {Double.NaN, Double.NaN};
      }

      return new double[] {min, max};
   }
}
