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
package inetsoft.web.binding.model.graph.calc;

import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.CalculateInfo;

public class RunningTotalCalcInfo extends CalculateInfo {
   /**
    * Load the calc info from Calculator
    * @param calc an object to be loaded into the calc info.
    */
   @Override
   protected void loadCalcInfo(Calculator calc) {
      if(!(calc instanceof RunningTotalCalc)) {
         return;
      }

      RunningTotalCalc rcalc = (RunningTotalCalc) calc;
      setAggregate(rcalc.getAggregate());
      setResetLevel(rcalc.getResetLevel());
      setBreakBy(rcalc.getBreakByValue());
   }

   /**
    * Create calculator.
    */
   @Override
   protected Calculator toCalculator0() {
      RunningTotalCalc rcalc = new RunningTotalCalc();
      rcalc.setAggregate(getAggregate());
      rcalc.setResetLevel(getResetLevel());
      rcalc.setBreakBy(getBreakBy());

      return rcalc;
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
    * Get reset level.
    * @return reset level.
    */
   public int getResetLevel() {
      return resetlvl;
   }

   /**
    * Set reset level.
    * @param resetlvl to be set.
    */
   public void setResetLevel(int resetlvl) {
      this.resetlvl = resetlvl;
   }

   public String getBreakBy() {
      return breakBy;
   }

   public void setBreakBy(String breakBy) {
      this.breakBy = breakBy;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof RunningTotalCalcInfo)) {
         return false;
      }

      RunningTotalCalcInfo calc = (RunningTotalCalcInfo) obj;

      return super.equals(obj) && resetlvl == calc.resetlvl &&
         Tool.equals(aggregate, calc.aggregate) &&
         Tool.equals(breakBy, calc.breakBy);
   }

   private String aggregate;
   private int resetlvl;
   private String breakBy;
}