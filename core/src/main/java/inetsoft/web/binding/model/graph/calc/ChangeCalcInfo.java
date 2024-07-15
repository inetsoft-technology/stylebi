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

import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.uql.viewsheet.graph.Calculator;

public class ChangeCalcInfo extends ValueOfCalcInfo {
   /**
    * Load the calc info from Calculator
    * @param calc an object to be loaded into the calc info.
    */
   @Override
   protected void loadCalcInfo(Calculator calc) {
      if(!(calc instanceof ChangeCalc)) {
         return;
      }

      ChangeCalc ccalc = (ChangeCalc) calc;
      setColumnName(ccalc.getColumnNameValue());
      setAsPercent(ccalc.isAsPercent());
      setFrom(ccalc.getFrom());
   }

   /**
    * Create calculator.
    */
   @Override
   protected Calculator toCalculator0() {
      ChangeCalc ccalc = new ChangeCalc();
      ccalc.setColumnName(getColumnName());
      ccalc.setAsPercent(isAsPercent());
      ccalc.setFrom(getFrom());

      return ccalc;
   }

   /**
    * Get asPercent.
    * @return asPercent.
    */
   public boolean isAsPercent() {
      return asPercent;
   }

   /**
    * Set if should as percent.
    * @param asPercent to be set.
    */
   public void setAsPercent(boolean asPercent) {
      this.asPercent = asPercent;
   }


   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ChangeCalcInfo)) {
         return false;
      }

      ChangeCalcInfo calc = (ChangeCalcInfo) obj;

      return super.equals(obj) && asPercent == calc.asPercent;
   }

   private boolean asPercent;
}