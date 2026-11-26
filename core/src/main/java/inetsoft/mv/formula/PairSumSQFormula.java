/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.mv.formula;

/**
 * The sum of squares of the first column over the rows where both columns are not null.
 *
 * @author InetSoft Technology Corp
 */
public class PairSumSQFormula extends AbstractPairFormula {
   public PairSumSQFormula() {
      super();
   }

   public PairSumSQFormula(int col) {
      super(col);
   }

   @Override
   protected void add(double x) {
      total += x * x;
   }

   @Override
   public String getName() {
      return "PairSumSQ";
   }
}
