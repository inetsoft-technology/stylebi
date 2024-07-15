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

import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.CalculateInfo;

public class PercentCalcInfo extends CalculateInfo {
   /**
    * Get level.
    * @return level.
    */
   public int getLevel() {
      return level;
   }

   /**
    * Set level.
    * @param level to set.
    */
   public void setLevel(int level) {
      this.level = level;
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
    * Load the calc info from Calculator
    * @param calc an object to be loaded into the calc info.
    */
   @Override
   protected void loadCalcInfo(Calculator calc) {
      if(!(calc instanceof PercentCalc)) {
         return;
      }

      PercentCalc pcalc = (PercentCalc) calc;
      setColumnName(pcalc.getColumnNameValue());
      setLevel(pcalc.getLevel());
      setByRow(pcalc.isByRow());
      setByColumn(pcalc.isByColumn());
   }

   /**
    * Create calculator.
    * don't need to set percentagedirection,
    * this will be handled by percentageDirection of crosstab option.
    */
   @Override
   protected Calculator toCalculator0() {
      PercentCalc pcalc = new PercentCalc();
      pcalc.setColumnName(getColumnName());
      pcalc.setLevel(getLevel());

      return pcalc;
   }

   public boolean isByRow() {
      return byRow;
   }

   public void setByRow(boolean byRow) {
      this.byRow = byRow;
   }

   public boolean isByColumn() {
      return byColumn;
   }

   public void setByColumn(boolean byColumn) {
      this.byColumn = byColumn;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof PercentCalcInfo)) {
         return false;
      }

      PercentCalcInfo calc = (PercentCalcInfo) obj;

      return super.equals(obj) && level == calc.level &&
         Tool.equals(columnName, calc.columnName) &&
         byRow == calc.byRow && byColumn == calc.byColumn;
   }

   private int level;
   private String columnName;
   private boolean byRow;
   private boolean byColumn;
}