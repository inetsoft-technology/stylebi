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
package inetsoft.web.binding.model.graph.calc;

import inetsoft.report.composition.graph.calc.MovingCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.CalculateInfo;

public class MovingCalcInfo extends CalculateInfo {
   /**
    * Load the calc info from Calculator
    * @param calc an object to be loaded into the calc info.
    */
   @Override
   protected void loadCalcInfo(Calculator calc) {
      if(!(calc instanceof MovingCalc)) {
         return;
      }

      MovingCalc mcalc = (MovingCalc) calc;
      setPrevious(mcalc.getPrevious());
      setNext(mcalc.getNext());
      setInnerDim(((MovingCalc) calc).getInnerDim());
      setIncludeCurrentValue(mcalc.isIncludeCurrentValue());
      setNullIfNoEnoughValue(mcalc.isNullIfNoEnoughValue());
      setAggregate(mcalc.getAggregate());
   }

   /**
    * Create calculator.
    */
   @Override
   protected Calculator toCalculator0() {
      MovingCalc mcalc = new MovingCalc();
      mcalc.setPrevious(getPrevious());
      mcalc.setNext(getNext());
      mcalc.setInnerDim(getInnerDim());
      mcalc.setIncludeCurrentValue(isIncludeCurrentValue());
      mcalc.setNullIfNoEnoughValue(isNullIfNoEnoughValue());
      mcalc.setAggregate(getAggregate());

      return mcalc;
   }

   /**
    * Get previous.
    * @return previous;
    */
   public int getPrevious() {
      return previous;
   }

   /**
    * Set previous.
    * @param previous to be set.
    */
   public void setPrevious(int previous) {
      this.previous = previous;
   }

   /**
    * Get next.
    * @return next;
    */
   public int getNext() {
      return next;
   }

   /**
    * Set next.
    * @param next to be set.
    */
   public void setNext(int next) {
      this.next = next;
   }

   /**
    * Get the inner dimension,
    * for chart, there's only one inner,
    * for crosstab, have row inner, and column inner.
    */
   public String getInnerDim() {
      return innerDim;
   }

   public void setInnerDim(String dim) {
      this.innerDim = dim;
   }

   /**
    * Get if include current value.
    * @return true if include current value.
    */
   public boolean isIncludeCurrentValue() {
      return includeCurrentValue;
   }

   /**
    * Set if should include current value or not.
    * @param includeCurrentValue to be set.
    */
   public void setIncludeCurrentValue(boolean includeCurrentValue) {
      this.includeCurrentValue = includeCurrentValue;
   }

   /**
    * Get if null if no enough value.
    * @return if null if no enough value.
    */
   public boolean isNullIfNoEnoughValue() {
      return nullIfNoEnoughValue;
   }

   /**
    * Set if should null if no enough value.
    * @param nullIfNoEnoughValue to be set.
    */
   public void setNullIfNoEnoughValue(boolean nullIfNoEnoughValue) {
      this.nullIfNoEnoughValue = nullIfNoEnoughValue;
   }

   /**
    * Get aggregate.
    * @return aggregate string.
    */
   public String getAggregate() {
      return aggregate;
   }

   /**
    * Set aggregate.
    * @param aggregate to be set.
    */
   public void setAggregate(String aggregate) {
      this.aggregate = aggregate;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof MovingCalcInfo)) {
         return false;
      }

      MovingCalcInfo calc = (MovingCalcInfo) obj;

      return super.equals(obj) && Tool.equals(aggregate, calc.aggregate) &&
         includeCurrentValue == calc.includeCurrentValue &&
         Tool.equals(innerDim, calc.innerDim) &&
         previous == calc.previous && next == calc.next &&
         nullIfNoEnoughValue == calc.nullIfNoEnoughValue;
   }

   private int previous;
   private int next;
   private String innerDim;
   private boolean includeCurrentValue;
   private boolean nullIfNoEnoughValue;
   private String aggregate;
}