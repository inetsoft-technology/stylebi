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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.CalcColumn;

public class CompoundGrowthCalc extends RunningTotalCalc {
   @Override
   public CalcColumn createCalcColumn(String column) {
      CompoundGrowthColumn calc = new CompoundGrowthColumn(column, getPrefix() + column);
      calc.setResetLevel(resetlvl);

      if(breakBy.getRValue() != null) {
         calc.setBreakBy(String.valueOf(breakBy.getRValue()));
      }

      return calc;
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefix0() {
      String str = "Compound Growth";

      switch(RunningTotalCalc.getDatePriority(resetlvl)) {
      case NONE_PRE:
         break;
      case YEAR_PRE:
         str += " of year";
         break;
      case QUARTER_PRE:
         str += " of quarter";
         break;
      case MONTH_PRE:
         str += " of month";
         break;
      case WEEK_PRE:
         str += " of week";
         break;
      case DAY_PRE:
         str += " of day";
         break;
      case HOUR_PRE:
         str += " of hour";
         break;
      case MINUTE_PRE:
         str += " of minute";
         break;
      }

      String column = getColumnView();

      return str + (column == null ? "" : " " + column);
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefixView0() {
      String str = catalog.getString("Compound Growth");
      String resetLvlStr = null;

      switch(RunningTotalCalc.getDatePriority(resetlvl)) {
      case YEAR_PRE:
         resetLvlStr = catalog.getString("of year");
         break;
      case QUARTER_PRE:
         resetLvlStr = catalog.getString("of quarter");
         break;
      case MONTH_PRE:
         resetLvlStr = catalog.getString("of month");
         break;
      case WEEK_PRE:
         resetLvlStr = catalog.getString("of week");
         break;
      case DAY_PRE:
         resetLvlStr = catalog.getString("of day");
         break;
      case HOUR_PRE:
         resetLvlStr = catalog.getString("of hour");
         break;
      case MINUTE_PRE:
         resetLvlStr = catalog.getString("of minute");
         break;
      }

      str = resetLvlStr != null ? str + " " + resetLvlStr : str;
      String column = getColumnView();

      return str + (column == null ? "" : " " + column);
   }

   /**
    * Get type.
    * @return type.
    */
   @Override
   public int getType() {
      return COMPOUNDGROWTH;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof CompoundGrowthCalc)) {
         return false;
      }

      return super.equals(obj);
   }

   @Override
   protected String getName0() {
      return catalog.getString("CompoundGrowth") + "(" + resetlvl + ")";
   }

   @Override
   public boolean isPercent() {
      return true;
   }

   @Override
   public Object clone() {
      CompoundGrowthCalc calc = (CompoundGrowthCalc) super.clone();
      return calc;
   }
}
