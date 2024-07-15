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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.internal.Util;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Running total calculate.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class RunningTotalCalc extends AbstractCalc implements DynamicCalc {
   /**
    * All labels of "reset At" comboBox.
    */
   public static final String[] ALL_RESETAT_LABELS = new String[] {
      catalog.getString("None"),
      catalog.getString("Year"),
      catalog.getString("Quarter"),
      catalog.getString("Month"),
      catalog.getString("Week"),
      catalog.getString("Day"),
      catalog.getString("Hour"),
      catalog.getString("Minute")};

   /**
    * All types of "reset At" comboBox.
    */
   public static final int[] ALL_RESETAT_TYPES = new int[] {
      RunningTotalColumn.NONE, RunningTotalColumn.YEAR,
      RunningTotalColumn.QUARTER, RunningTotalColumn.MONTH,
      RunningTotalColumn.WEEK, RunningTotalColumn.DAY,
      RunningTotalColumn.HOUR, RunningTotalColumn.MINUTE
   };

   /**
    * create calculator column.
    * @param column column to be created.
    * @return the created column.
    */
   @Override
   public CalcColumn createCalcColumn(String column) {
      RunningTotalColumn calc = new RunningTotalColumn(column, getPrefix() + column);
      calc.setFormula(Util.createFormula(null, aggregate));
      calc.setResetLevel(resetlvl);

      if(breakBy.getRValue() != null) {
         calc.setBreakBy(String.valueOf(breakBy.getRValue()));
      }

      return calc;
   }

   @Override
   public void updateRefs(List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs) {
      for(int i = 0; i < oldRefs.size(); i++) {
         final VSDimensionRef oldRef = oldRefs.get(i);

         if(oldRef.getFullName().equals(getBreakByValue())) {
            setBreakBy(newRefs.get(i).getFullName());
         }
      }
   }

   @Override
   public String getColumnName() {
      return getBreakBy();
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefix0() {
      String str = "Running " + aggregate;

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
      String str = "Running";

      switch(RunningTotalCalc.getDatePriority(resetlvl)) {
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
      case NONE_PRE:
      default:
         str += " " + catalog.getString(aggregate);
         break;
      }

      String column = getColumnView();

      return str + (column == null ? "" : " " + column);
   }

   /**
    * Get type.
    * @return type.
    */
   @Override
   public int getType() {
      return RUNNINGTOTAL;
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
      return getDynamicColumn(breakBy.getRValue());
   }

   public void setBreakBy(String breakBy) {
      this.breakBy.setDValue(breakBy);
   }

   public String getBreakByValue() {
      return breakBy.getDValue();
   }

   /**
    * Get label.
    * @return label.
    */
   public String getLabel() {
      return null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return getClass().getName() + ": " + hashCode() +
         " [type=" + getType() + ", alias=" + getAlias() +
         ", label=" + getLabel() + ", name=" + getName() +
         ", aggregate=" + aggregate +
         ", resetLevel" + getResetLevel() +
         ", breakBy"  + getBreakBy() + "]";
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(obj == null || getClass() != obj.getClass()) {
         return false;
      }

      RunningTotalCalc calc = (RunningTotalCalc) obj;
      return super.equals(obj) && resetlvl == calc.resetlvl &&
         Tool.equals(aggregate, calc.aggregate) &&
         Tool.equals(breakBy, calc.breakBy);
   }

   /**
    * Get hash code.
    */
   public int hashCode() {
      int res = (aggregate == null ? 0 : aggregate.hashCode());
      res = 31 * res + resetlvl;
      return res;
   }

   /**
    * Get the priority of the given date flag.
    */
   public static int getDatePriority(int date) {
      switch(date) {
      case RunningTotalColumn.YEAR :
         return YEAR_PRE;
      case RunningTotalColumn.QUARTER :
         return QUARTER_PRE;
      case RunningTotalColumn.MONTH :
         return MONTH_PRE;
      case RunningTotalColumn.WEEK :
         return WEEK_PRE;
      case RunningTotalColumn.DAY :
         return DAY_PRE;
      case RunningTotalColumn.HOUR:
         return HOUR_PRE;
      case RunningTotalColumn.MINUTE :
         return MINUTE_PRE;
      case RunningTotalColumn.SECOND :
         return SECOND_PRE;
      default :
         return NONE_PRE;
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   protected void writeAttribute(PrintWriter writer) {
      super.writeAttribute(writer);
      writer.print(" resetlvl=\"" + resetlvl + "\"");

      if(aggregate != null) {
         writer.print(" aggregate=\"" + aggregate + "\"");
      }

      if(breakBy.getDValue() != null) {
         writer.print(" breakBy=\"" + breakBy.getDValue() + "\"");
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseAttribute(Element root) throws Exception {
      super.parseAttribute(root);
      resetlvl = Integer.parseInt(Tool.getAttribute(root, "resetlvl"));
      aggregate = Tool.getAttribute(root, "aggregate");

      String breakByVal = Tool.getAttribute(root, "breakBy");

      if(breakByVal != null) {
         breakBy.setDValue(breakByVal);
      }
   }

   @Override
   protected String getName0() {
      return catalog.getString("RunningTotal") +
         "(" + aggregate + "," + resetlvl + ")";
   }

   /**
    * Get view.
    */
   @Override
   protected String toView0() {
      return getPrefixView0();
   }

   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(breakBy);

      return list;
   }

   @Override
   public Object clone() {
      RunningTotalCalc calc = (RunningTotalCalc) super.clone();
      calc.aggregate = aggregate;
      calc.resetlvl = resetlvl;

      if(breakBy != null) {
         calc.breakBy = (DynamicValue) breakBy.clone();
      }

      return calc;
   }

   protected static final int NONE_PRE = 8; // priority of None
   protected static final int YEAR_PRE = 7; // priority of year
   protected static final int QUARTER_PRE = 6; // priority of quarter
   protected static final int MONTH_PRE = 5; // priority of month
   protected static final int WEEK_PRE = 4; // priority of week
   protected static final int DAY_PRE = 3; // priority of day
   protected static final int HOUR_PRE = 2; // priority of hour
   protected static final int MINUTE_PRE = 1; // priority of minute
   private static final int SECOND_PRE = 0; // priority of second

   protected String aggregate = XConstants.SUM_FORMULA;
   protected int resetlvl;
   protected DynamicValue breakBy = new DynamicValue();
}
