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
package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.CalcColumn;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.internal.DatePeriod;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ValueOfCalc extends AbstractCalc implements DynamicCalc {
   /**
    * Compute the difference between the current value and first value.
    */
   public static final int FIRST = 0;
   /**
    * Compute the difference between the current value and previous value.
    */
   public static final int PREVIOUS = 1;
   /**
    * Compute the difference between the current value and next value.
    */
   public static final int NEXT = 2;
   /**
    * Compute the difference between the current value and last value.
    */
   public static final int LAST = 3;
   /**
    * Change from the previous year on current column. Only applicable for date interval.
    * For example, if the column is Month, this compares the value from 12 months ago
    * instead of previous one month as in the case of PREVIOUS.
    */
   public static final int PREVIOUS_YEAR = 4;
   /**
    * Change from the previous quarter on current column. Only applicable for date interval.
    * For example, if the column is Month, this compares the value from 3 months ago
    * instead of previous one month as in the case of PREVIOUS.
    */
   public static final int PREVIOUS_QUARTER = 5;
   /**
    * Change from the previous week on the current column. Only applicable for day interval.
    */
   public static final int PREVIOUS_WEEK = 6;

   /**
    * Change from the previous month on the current column. .
    */
   public static final int PREVIOUS_MONTH = 7;

   /**
    * Change from the previous date range on the current column, it must work with offsetMap.
    */
   public static final int PREVIOUS_RANGE = 8;

   /**
    * create calculator column.
    * @param column column to be created.
    * @return the created column.
    */
   @Override
   public CalcColumn createCalcColumn(String column) {
      ValueOfColumn calc = new ValueOfColumn(column, getPrefix() + column);
      calc.setChangeType(from);
      calc.setDim("".equals(getColumnName()) ? null : getColumnName());
      calc.setDcPeriods(dcPeriods);
      calc.setDateComparisonDims(getComparisonDateDims());
      calc.setFirstWeek(isFirstWeek());
      calc.setDcTempGroups(dcTempGroups);

      return calc;
   }

   @Override
   public void updateRefs(List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs) {
      for(int i = 0; i < oldRefs.size(); i++) {
         final VSDimensionRef oldRef = oldRefs.get(i);

         if(oldRef.getFullName().equals(getColumnNameValue())) {
            setColumnName(newRefs.get(i).getFullName());
         }
      }
   }

   @Override
   public boolean supportSortByValue() {
      return from == PREVIOUS_YEAR || from == PREVIOUS_QUARTER || from == PREVIOUS_MONTH ||
         from == PREVIOUS_WEEK || from == PREVIOUS_RANGE;
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefix0() {
      String str = "Value";

      str += " of ";

      switch(from) {
         case FIRST:
            str += "first";
            break;
         case PREVIOUS:
            str += "previous";
            break;
         case NEXT:
            str += "next";
            break;
         case LAST:
            str += "last";
            break;
         case PREVIOUS_YEAR:
            str += "previous year of";
            break;
         case PREVIOUS_QUARTER:
            str += "previous quarter of";
            break;
         case PREVIOUS_MONTH:
            str += "previous month of";
            break;
         case PREVIOUS_WEEK:
            str += "previous week of";
            break;
         case PREVIOUS_RANGE:
            str += "previous range of";
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
      String str = "Value of ";

      switch(from) {
         case FIRST:
            str += "first";
            break;
         case PREVIOUS:
            str += "previous";
            break;
         case NEXT:
            str += "next";
            break;
         case LAST:
            str += "last";
            break;
         case PREVIOUS_YEAR:
            str += "previous year of";
            break;
         case PREVIOUS_QUARTER:
            str += "previous quarter of";
            break;
         case PREVIOUS_MONTH:
            str += "previous month of";
         break;
         case PREVIOUS_WEEK:
            str += "previous week of";
            break;
         case PREVIOUS_RANGE:
            str += "previous range of";
            break;
      }

      str = catalog.getString(str);
      String column = getColumnView();

      return str + (column == null ? "" : " " + column);
   }

   /**
    * Get type.
    * @return type.
    */
   @Override
   public int getType() {
      return VALUE;
   }

   /**
    * Get columnName.
    * @return columnName.
    */
   @Override
   public String getColumnName() {
      return getDynamicColumn(columnName.getRValue());
   }

   /**
    * Set column name.
    * @param columnName to be set.
    */
   public void setColumnName(String columnName) {
      this.columnName.setDValue(columnName);
   }

   public String getColumnNameValue() {
      return columnName.getDValue();
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

   @Override
   public boolean isPercent() {
      return isAsPercent();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      // ValueofCalc don't equals to ChangeCalc.
      if(!(obj instanceof ValueOfCalc) || !Tool.equals(this.getClass(), obj.getClass())) {
         return false;
      }

      ValueOfCalc calc = (ValueOfCalc) obj;
      return super.equals(obj) &&
         Tool.equals(columnName, calc.columnName) &&
         from == calc.from && asPercent == calc.asPercent;
   }

   /**
    * Get hash code of this object.
    */
   public int hashCode() {
      int res = isAsPercent() ? 0 : 1;
      res = 31 * res + (columnName == null ? 0 : columnName.hashCode());
      res = 31 * res + from;
      return res;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return getClass().getName() + ": " + hashCode() +
         " [type=" + getType() + ", alias=" + getAlias() +
         ", name=" + getName() + ", columnName" + getColumnName() +
         ", from=" + getFrom() + "]";
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   protected void writeAttribute(PrintWriter writer) {
      super.writeAttribute(writer);

      if(columnName.getDValue() != null) {
         writer.print(" columnName=\"" + columnName.getDValue() + "\"");
      }

      writer.print(" from=\"" + from + "\"");
      writer.print(" asPercent=\"" + asPercent + "\"");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseAttribute(Element root) throws Exception {
      super.parseAttribute(root);
      columnName.setDValue(Tool.getAttribute(root, "columnName"));
      from = Integer.parseInt(Tool.getAttribute(root, "from"));
      asPercent = Boolean.parseBoolean(Tool.getAttribute(root, "asPercent"));
   }

   @Override
   protected String getName0() {
      return catalog.getString("Change") + "(" + from +
         (columnName == null ? "" : "," + columnName.getRValue()) + "," + isAsPercent() + ")";
   }

   @Override
   protected String toView0() {
      return getPrefixView0();
   }
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(columnName);

      return list;
   }

   public void setDcPeriods(List<DatePeriod> dcPeriods) {
      this.dcPeriods = dcPeriods;
   }

   protected List<DatePeriod> getDcPeriods() {
      return dcPeriods;
   }

   public List<XDimensionRef> getComparisonDateDims() {
      return comparisonDateDims;
   }

   public void setComparisonDateDims(List<XDimensionRef> comparisonDateDims) {
      this.comparisonDateDims = comparisonDateDims;
   }

   public boolean isFirstWeek() {
      return firstWeek;
   }

   public void setFirstWeek(boolean firstWeek) {
      this.firstWeek = firstWeek;
   }

   public List<XDimensionRef> getDcTempGroups() {
      return dcTempGroups;
   }

   public void setDcTempGroups(List<XDimensionRef> dcTempGroups) {
      this.dcTempGroups = dcTempGroups;
   }

   @Override
   public Object clone() {
      ValueOfCalc calc = (ValueOfCalc) super.clone();

      if(columnName != null) {
         calc.columnName = (DynamicValue) columnName.clone();
      }

      calc.from = from;
      calc.asPercent = asPercent;

      return calc;
   }

   private DynamicValue columnName = new DynamicValue();
   private int from;
   private boolean asPercent;

   // for date comparison.
   private List<DatePeriod> dcPeriods;
   List<XDimensionRef> comparisonDateDims;
   private boolean firstWeek; // for date comparison week level;
   private List<XDimensionRef> dcTempGroups;

   public static final String ROW_SERIES = "Row Series";
   public static final String COLUMN_SERIES = "Column Series";
}
