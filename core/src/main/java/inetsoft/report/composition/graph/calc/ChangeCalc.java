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
import inetsoft.uql.viewsheet.VSDimensionRef;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.List;

/**
 * Change Calculator.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ChangeCalc extends ValueOfCalc {
   /**
    * create calculator column.
    * @param column column to be created.
    * @return the created column.
    */
   @Override
   public CalcColumn createCalcColumn(String column) {
      ChangeColumn calc = new ChangeColumn(column, getPrefix() + column);
      calc.setChangeType(getFrom());
      calc.setAsPercent(isAsPercent());
      calc.setDim("".equals(getColumnName()) ? null : getColumnName());
      calc.setDcPeriods(getDcPeriods());
      calc.setDateComparisonDims(getComparisonDateDims());
      calc.setFirstWeek(isFirstWeek());
      calc.setDcTempGroups(getDcTempGroups());
      return calc;
   }

   @Override
   public void updateRefs(List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs) {
      super.updateRefs(oldRefs, newRefs);
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefix0() {
      String str = "Change";

      if(isAsPercent()) {
         str = "% " + str;
      }

      str += " from ";

      switch(getFrom()) {
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
      String str = "Change from ";

      switch(getFrom()) {
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

      if(isAsPercent()) {
         str = "% " + str;
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
      return CHANGE;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof ChangeCalc)) {
         return false;
      }

      ChangeCalc calc = (ChangeCalc) obj;
      return super.equals(obj);
   }

   /**
    * Get hash code of this object.
    */
   @Override
   public int hashCode() {
      int res = isAsPercent() ? 0 : 1;
      res = 31 * res + (getColumnName() == null ? 0 : getColumnName().hashCode());
      res = 31 * res + getFrom();
      return res;
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      return getClass().getName() + ": " + hashCode() +
         " [type=" + getType() + ", alias=" + getAlias() +
         ", name=" + getName() +
         ", asPercent=" + isAsPercent() + ", columnName" + getColumnName() +
         ", from=" + getFrom() + "]";
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   protected void writeAttribute(PrintWriter writer) {
      super.writeAttribute(writer);
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseAttribute(Element root) throws Exception {
      super.parseAttribute(root);
   }

   @Override
   protected String getName0() {
      return catalog.getString("Change") + "(" + getFrom() +
         (getColumnName() == null ? "" : "," + getColumnName()) + "," + isAsPercent() + ")";
   }

   @Override
   protected String toView0() {
      return getPrefixView0();
   }
 }
