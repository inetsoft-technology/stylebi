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
package inetsoft.web.binding.model.graph.calc;

import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.CalculateInfo;

public class ValueOfCalcInfo extends CalculateInfo {
   /**
    * Load the calc info from Calculator
    * @param calc an object to be loaded into the calc info.
    */
   @Override
   protected void loadCalcInfo(Calculator calc) {
      if(!(calc instanceof ValueOfCalc)) {
         return;
      }

      ValueOfCalc vcalc = (ValueOfCalc) calc;
      setColumnName(vcalc.getColumnNameValue());
      setFrom(vcalc.getFrom());
   }

   /**
    * Create calculator.
    */
   @Override
   protected Calculator toCalculator0() {
      ValueOfCalc ccalc = new ValueOfCalc();
      ccalc.setColumnName(getColumnName());
      ccalc.setFrom(getFrom());

      return ccalc;
   }

   /**
    * Get columnName.
    * @return columnName.
    */
   public String getColumnName() {
      return columnName;
   }

   /**
    * Set column name.
    * @param columnName to be set.
    */
   public void setColumnName(String columnName) {
      this.columnName = columnName;
   }

   /**
    * Get from array.
    * @return from array.
    */
   public int getFrom() {
      return from;
   }

   /**
    * Set from array.
    * @param from to be set.
    */
   public void setFrom(int from) {
      this.from = from;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ValueOfCalcInfo)) {
         return false;
      }

      ValueOfCalcInfo calc = (ValueOfCalcInfo) obj;

      return super.equals(obj) && Tool.equals(columnName, calc.columnName) && from == calc.from;
   }

   private String columnName;
   private int from;
}
