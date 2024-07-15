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
import inetsoft.report.internal.Util;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.List;

/**
 * Moving calculate.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class MovingCalc extends AbstractCalc {
   /**
    * create calculator column.
    * @param column column to be created.
    * @return the created column.
    */
   @Override
   public CalcColumn createCalcColumn(String column) {
      MovingColumn calc = new MovingColumn(column, getPrefix() + column);
      calc.setFormula(Util.createFormula(null, aggregate));
      calc.setInnerDim(getInnerDim());
      calc.setPreCnt(previous);
      calc.setNextCnt(next);
      calc.setIncludeCurrent(includeCurrentValue);
      calc.setShowNull(nullIfNoEnoughValue);
      return calc;
   }

   @Override
   public void updateRefs(List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs) {
      // no-op
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefix0() {
      String prefix = "Moving " + aggregate + " of " +
         (previous + next + (includeCurrentValue ? 1 : 0));

      String column = getColumnView();

      return prefix + (column == null ? "" : " " + column);
   }

   /**
    * Get prefix.
    */
   @Override
   protected String getPrefixView0() {
      String prefix = catalog.getString("Moving") + " " + catalog.getString(aggregate);
      String suffix = " of " + (previous + next + (includeCurrentValue ? 1 : 0));
      prefix = prefix + suffix;
      String column = getColumnView();

      return prefix + (column == null ? "" : " " + column);
   }

   /**
    * Get type.
    * @return type.
    */
   @Override
   public int getType() {
      return MOVING;
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
    * Get label.
    * @return label.
    */
   public String getLabel() {
      return null;
   }

   /**
    * Get the inner dimension,
    * for chart, there's only one inner dimension.
    * for crosstab, row inner or column inner dimension.
    *    see AbstractCalc.ROW_INNER or AbstractCalc.COLUMN_INNER
    */
   public String getInnerDim() {
      return innerDim;
   }

   public void setInnerDim(String dim) {
      this.innerDim = dim;
   }

   /**
    * Get columnName.
    * @return columnName.
    */
   @Override
   public String getColumnName() {
      return innerDim;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof MovingCalc)) {
         return false;
      }

      MovingCalc calc = (MovingCalc) obj;
      return super.equals(obj) && Tool.equals(aggregate, calc.aggregate) &&
         Tool.equals(innerDim, ((MovingCalc) obj).innerDim) &&
         includeCurrentValue == calc.includeCurrentValue &&
         previous == calc.previous && next == calc.next &&
         nullIfNoEnoughValue == calc.nullIfNoEnoughValue;
   }

   /**
    * Get hash code of this object.
    */
   public int hashCode() {
      int res = aggregate == null ? 0 : aggregate.hashCode();
      res = 31 * res + (includeCurrentValue ? 0 : 1);
      res = 31 * res + (nullIfNoEnoughValue ? 0 : 1);
      res = 31 * res + previous;
      res = 31 * res + next;
      return res;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return getClass().getName() + ": " + hashCode() +
         " [type=" + getType() + ", alias=" + getAlias() +
         ", label=" + getLabel() + ", name=" + getName() +
         ", aggregate=" + aggregate + ", innerDim=" + innerDim +
         ", previous=" + getPrevious() + ", next=" + getNext() +
         ", includeCurrentValue=" + isIncludeCurrentValue() +
         ", nullIfNoEnoughValue=" + isNullIfNoEnoughValue() + "]";
   }

   @Override
   protected String getName0() {
      return "Moving(" + previous + "," + next + "," + aggregate + "," +
         includeCurrentValue + "," + nullIfNoEnoughValue + ")";
   }

   /**
    * Get view.
    */
   @Override
   protected String toView0() {
      return getPrefixView0();
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   protected void writeAttribute(PrintWriter writer) {
      super.writeAttribute(writer);
      writer.print(" includeCurrentValue=\"" + includeCurrentValue + "\"");
      writer.print(" next=\"" + next + "\"");
      writer.print(" previous=\"" + previous + "\"");
      writer.print(" nullIfNoEnoughValue=\"" + nullIfNoEnoughValue + "\"");

      if(aggregate != null) {
         writer.print(" aggregate=\"" + aggregate + "\"");
      }

      if(innerDim != null && !innerDim.isEmpty()) {
         writer.print(" innerDim=\"" + innerDim + "\"");
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseAttribute(Element root) throws Exception {
      super.parseAttribute(root);
      previous = Integer.parseInt(Tool.getAttribute(root, "previous"));
      next = Integer.parseInt(Tool.getAttribute(root, "next"));
      includeCurrentValue =
         Boolean.parseBoolean(Tool.getAttribute(root, "includeCurrentValue"));
      nullIfNoEnoughValue =
         Boolean.parseBoolean(Tool.getAttribute(root, "nullIfNoEnoughValue"));
      aggregate = Tool.getAttribute(root, "aggregate");
      innerDim = Tool.getAttribute(root, "innerDim");
   }

   @Override
   public Object clone() {
      MovingCalc calc = (MovingCalc) super.clone();
      calc.aggregate = aggregate;
      calc.innerDim = innerDim;
      calc.next = next;
      calc.previous = previous;
      calc.includeCurrentValue = includeCurrentValue;
      calc.nullIfNoEnoughValue = nullIfNoEnoughValue;

      return calc;
   }
   private String aggregate = XConstants.AVERAGE_FORMULA;
   private String innerDim = "";
   private boolean includeCurrentValue = true;
   private int next = 2;
   private boolean nullIfNoEnoughValue;
   private int previous = 2;
}
