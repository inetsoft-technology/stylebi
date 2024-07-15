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

      LinearRange r2 = new LinearRange();
      double[] range2 = r2.calculate(data, cols, selector);

      return new double[] {Math.min(range[0], range2[0]),
                           Math.max(range[1], range2[1])};
   }
}
