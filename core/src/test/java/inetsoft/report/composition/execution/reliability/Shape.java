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
package inetsoft.report.composition.execution.reliability;

/**
 * The worksheet pipeline shape a script runs in (Testing #77123).
 */
public enum Shape {
   /** an expression column of a FormulaTableLens */
   FTL,
   /** an expression column read through PostProcessor.filter's condition filter */
   FTL_UNDER_CF2,
   /** the JavaScript value of a condition */
   CONDITION,
   /** a calc field over aggregates of 50-row groups */
   CALC_FIELD;

   /**
    * @return whether the shape runs its script per row of a formula lens, the only place
    * that reads batchRows / maxBatchRows and whose script runs depend on the read order. A
    * condition runs its script once per ConditionGroup when it is built, a calc field once per
    * group in the aggregation's own order.
    */
   public boolean rowScripted() {
      return this == FTL || this == FTL_UNDER_CF2;
   }
}
