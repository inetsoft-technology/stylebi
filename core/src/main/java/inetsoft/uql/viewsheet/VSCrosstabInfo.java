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
package inetsoft.uql.viewsheet;

import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;

import java.io.PrintWriter;
import java.util.*;

import static java.util.stream.Collectors.toList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * VSCrosstabInfo contains crosstab binding information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSCrosstabInfo implements AssetObject, DateComparisonBinding {
   /**
    * Constructor.
    */
   public VSCrosstabInfo() {
      super();

      colTotalVisibleValue = new DynamicValue(null, XSchema.BOOLEAN);
      rowTotalVisibleValue = new DynamicValue(null, XSchema.BOOLEAN);
      fillWithZeroValue = new DynamicValue2("false", XSchema.BOOLEAN);
      sideBySideValue = new DynamicValue2("false", XSchema.BOOLEAN);
      mergeSpanValue = new DynamicValue2("true", XSchema.BOOLEAN);
      sortOthersLast = new DynamicValue2("true", XSchema.BOOLEAN);
      calculateTotal= new DynamicValue2("false", XSchema.BOOLEAN);
      percentageByValue = new DynamicValue(
         null, XSchema.INTEGER,
         new int[] {XConstants.PERCENTAGE_BY_COL,
                    XConstants.PERCENTAGE_BY_ROW},
         new String[] {"columns", "rows"});

      aggrs = new DataRef[0];
      cols = new DataRef[0];
      rows = new DataRef[0];
      aggrs2 = new DataRef[0];
      cols2 = new DataRef[0];
      rows2 = new DataRef[0];
   }

   /**
    * Get the aggregate columns of this crosstab.
    * @return the aggregate columns of this crosstab.
    */
   public DataRef[] getDesignAggregates() {
      return aggrs;
   }

   /**
    * Set the aggregate columns of this crosstab.
    * @param aggrs the aggregate columns of this crosstab.
    */
   public void setDesignAggregates(DataRef[] aggrs) {
      this.aggrs = aggrs == null ? new DataRef[0] : aggrs;
      updateAggregateRuntimeID();
      designRefsChanged = true;
   }

   /**
    * Get the column headers of this crosstab.
    * @return the column headers of this crosstab.
    */
   public DataRef[] getDesignColHeaders() {
      return cols;
   }

   /**
    * Set the column headers of this crosstab.
    * @param cols the column headers of this crosstab.
    */
   public void setDesignColHeaders(DataRef[] cols) {
      this.cols = cols == null ? new DataRef[0] : cols;
      designRefsChanged = true;
      updateDimensionRuntimeID();
   }

   /**
    * Get the row headers of this crosstab.
    * @return the row headers of this crosstab.
    */
   public DataRef[] getDesignRowHeaders() {
      return rows;
   }

   /**
    * Set the row headers of this crosstab.
    * @param rows the row headers of this crosstab.
    */
   public void setDesignRowHeaders(DataRef[] rows) {
      this.rows = rows == null ? new DataRef[0] : rows;
      designRefsChanged = true;
      updateDimensionRuntimeID();
   }

   /**
    * Check if contains the aggregate data ref.
    * @param ref the specified data ref.
    * @return <tt>true</tt> if contains the data ref, <tt>false</tt> otherwise.
    */
   public boolean containsDesignAggregate(DataRef ref) {
      for(int i = 0; i < aggrs.length; i++) {
         if(aggrs[i].equals(ref)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains the header data ref.
    * @param ref the specified data ref.
    * @return <tt>true</tt> if contains the data ref, <tt>false</tt> otherwise.
    */
   public boolean containsDesignHeader(DataRef ref) {
      for(int i = 0; i < cols.length; i++) {

         if(cols[i].equals(ref)) {
            return true;
         }
      }

      for(int i = 0; i < rows.length; i++) {
         if(rows[i].equals(ref)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the aggregate columns of this crosstab.
    * @return the aggregate columns of this crosstab.
    */
   public DataRef[] getRuntimeAggregates() {
      return aggrs2;
   }

   /**
    * Set the aggregate columns of this crosstab.
    * @param aggrs the aggregate columns of this crosstab.
    */
   public void setRuntimeAggregates(DataRef[] aggrs) {
      this.aggrs2 = aggrs == null ? new DataRef[0] : aggrs;
      updateAggregateRuntimeID();
   }

   /**
    * Get the column headers of this crosstab.
    * @return the column headers of this crosstab.
    */
   public DataRef[] getRuntimeColHeaders() {
      return cols2;
   }

   /**
    * Set the column headers of this crosstab.
    * @param cols the column headers of this crosstab.
    */
   public void setRuntimeColHeaders(DataRef[] cols) {
      this.cols2 = cols == null ? new DataRef[0] : cols;
      updateDimensionRuntimeID();
   }

   /**
    * Get the row headers of this crosstab.
    * @return the row headers of this crosstab.
    */
   public DataRef[] getRuntimeRowHeaders() {
      return rows2;
   }

   /**
    * Get the runtime row headers with period.
    */
   public DataRef[] getPeriodRuntimeRowHeaders() {
      return rows3 == null ? rows2 : rows3;
   }

   /**
    * Set the period runtime row headers.
    */
   public void setPeriodRuntimeRowHeaders(DataRef[] rows) {
      rows3 = rows;
   }

   /**
    * Set the row headers of this crosstab.
    * @param rows the row headers of this crosstab.
    */
   public void setRuntimeRowHeaders(DataRef[] rows) {
      this.rows2 = rows == null ? new DataRef[0] : rows;
      updateDimensionRuntimeID();
   }

   /**
    * Check if it is row subtotal visible.
    * @return <tt>true</tt> if it is row subtotal visible, <tt>false</tt>
    *         otherwise.
    */
   public boolean isRowTotalVisible() {
      Boolean value = (Boolean) rowTotalVisibleValue.getRuntimeValue(true);
      return value.booleanValue();
   }

   /**
    * Get the row subtotal visible value of this dimension reference.
    * @return the row subtotal visible value of this dimension reference.
    */
   public String getRowTotalVisibleValue() {
      return rowTotalVisibleValue.getDValue();
   }

   /**
    * Set the row subtotal visible value of this dimension reference.
    * @param visible the row subtotal visible value of this dimension reference.
    */
   public void setRowTotalVisibleValue(String visible) {
      this.rowTotalVisibleValue.setDValue(visible);
   }

   /**
    * Check if it is col subtotal visible.
    * @return <tt>true</tt> if it is col subtotal visible, <tt>false</tt>
    *         otherwise.
    */
   public boolean isColTotalVisible() {
      Boolean value = (Boolean) colTotalVisibleValue.getRuntimeValue(true);
      return value.booleanValue();
   }

   /**
    * Get the col subtotal visible value of this dimension reference.
    * @return the col subtotal visible value of this dimension reference.
    */
   public String getColTotalVisibleValue() {
      return colTotalVisibleValue.getDValue();
   }

   /**
    * Set the col subtotal visible value of this dimension reference.
    * @param visible the col subtotal visible value of this dimension reference.
    */
   public void setColTotalVisibleValue(String visible) {
      this.colTotalVisibleValue.setDValue(visible);
   }

   /**
    * Get the percentage by option.
    * @return the percentage by option.
    */
   public int getPercentageByOption() {
      Integer value = (Integer) percentageByValue.getRuntimeValue(true);
      return value.intValue();
   }

   /**
    * Get the percentage by value of this dimension reference.
    * @return the percentage by value of this dimension reference.
    */
   public String getPercentageByValue() {
      return percentageByValue.getDValue();
   }

   /**
    * Set the col subtotal visible value of this dimension reference.
    * @param percentage the col subtotal visible value of this dimension reference.
    */
   public void setPercentageByValue(String percentage) {
      this.percentageByValue.setDValue(percentage);
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname,
         colTotalVisibleValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname,
         rowTotalVisibleValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname,
         percentageByValue, vs);

      for(int i = 0; cols != null && i < cols.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) cols[i];
         dref.renameDepended(oname, nname, vs);
      }

      for(int i = 0; rows != null && i < rows.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) rows[i];
         dref.renameDepended(oname, nname, vs);
      }

      for(int i = 0; aggrs != null && i < aggrs.length; i++) {
         VSAggregateRef dref = (VSAggregateRef) aggrs[i];
         dref.renameDepended(oname, nname, vs);
      }
   }

   /**
    * Get the dynamic property values.
    * @return the dynamic values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(colTotalVisibleValue);
      list.add(rowTotalVisibleValue);
      list.add(percentageByValue);

      for(int i = 0; cols != null && i < cols.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) cols[i];
         list.addAll(dref.getDynamicValues());
      }

      for(int i = 0; rows != null && i < rows.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) rows[i];
         list.addAll(dref.getDynamicValues());
      }

      for(int i = 0; aggrs != null && i < aggrs.length; i++) {
         VSAggregateRef dref = (VSAggregateRef) aggrs[i];
         list.addAll(dref.getDynamicValues());
      }

      return list;
   }

   public void fixAggregateRefs() {
      DataRef[] aggs = getAggregates();

      for(DataRef agg : aggs) {
         modifiedCalculator(((XAggregateRef) agg));
      }
   }

   private void modifiedCalculator(XAggregateRef ref) {
      if(ref == null) {
         return;
      }

      Calculator calc = ref.getCalculator();

      if(calc == null) {
         return;
      }

      int calcType = calc.getType();

      switch(calcType) {
         case Calculator.RUNNINGTOTAL :
            modifiedRunningTotal(((RunningTotalCalc) calc));
            break;
         case Calculator.CHANGE :
         case Calculator.VALUE :
            modifiedColumnName((ValueOfCalc) calc);
            break;
      }
   }

   private void modifiedRunningTotal(RunningTotalCalc calc) {
      String columnName = calc.getColumnName();

      if(Tool.equals(AbstractCalc.ROW_INNER, columnName) ||
         Tool.equals(AbstractCalc.COLUMN_INNER, columnName))
      {
         return;
      }

      if(findDimRef(columnName)) {
         return;
      }

      calc.setBreakBy(null);
   }

   private void modifiedColumnName(ValueOfCalc ccalc) {
      String columnName = ccalc.getColumnName();

      if(Tool.equals(AbstractCalc.ROW_INNER, columnName) ||
         Tool.equals(AbstractCalc.COLUMN_INNER, columnName))
      {
         return;
      }

      if(findDimRef(columnName)) {
         return;
      }

      ccalc.setColumnName(null);
   }

   private boolean findDimRef(String columnName) {
      DataRef[] rows = getRowHeaders();
      boolean find = false;

      for(DataRef dataRef : rows) {
         if(Tool.equals(columnName, dataRef.getName()) ||
            (dataRef instanceof VSDimensionRef &&
               Tool.equals(columnName, ((VSDimensionRef) dataRef).getFullName())))
         {
            find = true;
            return find;
         }
      }

      DataRef[] cols = getColHeaders();

      for(DataRef dataRef : cols) {
         if(Tool.equals(columnName, dataRef.getName())) {
            find = true;
            return find;
         }
      }

      return find;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "VSCrosstabInfo:" + hashCode() + "[" + colTotalVisibleValue +
         ", " + rowTotalVisibleValue + ", " + isFillBlankWithZero() +  ", " +
         isSummarySideBySide() + ", " + Tool.arrayToString(aggrs) +
         ", " + Tool.arrayToString(rows) + ", " +
         Tool.arrayToString(cols) + "]";
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      String attr;

      if((attr = getAttributeStr(elem, "fillWithZero", "false")) != null) {
         setFillBlankWithZeroValue(attr.equalsIgnoreCase("true"));
      }

      if((attr = getAttributeStr(elem, "sideBySide", "false")) != null) {
         setSummarySideBySideValue(attr.equalsIgnoreCase("true"));
      }

      setMergeSpan(
         !"false".equalsIgnoreCase(getAttributeStr(elem, "mergeSpan", "true")));
      setMergeSpanValue(
         !"false".equalsIgnoreCase(getAttributeStr(elem, "mergeSpanValue", "true")));

      setSortOthersLast(
         !"false".equalsIgnoreCase(getAttributeStr(elem, "sortOthersLast", "true")));
      setSortOthersLastValue(
         !"false".equalsIgnoreCase(getAttributeStr(elem, "sortOthersLastValue", "true")));
      setCalculateTotalValue(
         !"false".equalsIgnoreCase(getAttributeStr(elem, "calculateTotalValue", "false")));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element anode = Tool.getChildNodeByTagName(elem, "ainfo");

      if(anode != null) {
         ainfo = new AggregateInfo();
         ainfo.parseXML(Tool.getFirstChildNode(anode));
      }

      Element node = Tool.getChildNodeByTagName(elem, "colTotalVisibleValue");
      colTotalVisibleValue.setDValue(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(elem, "rowTotalVisibleValue");
      rowTotalVisibleValue.setDValue(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(elem, "percentageByValue");
      percentageByValue.setDValue(Tool.getValue(node));

      Element aggrsNode = Tool.getChildNodeByTagName(elem, "aggregates");

      if(aggrsNode != null) {
         NodeList aggrsList =
            Tool.getChildNodesByTagName(aggrsNode, "dataRef");

         if(aggrsList != null && aggrsList.getLength() > 0) {
            aggrs = new VSAggregateRef[aggrsList.getLength()];

            for(int i = 0; i < aggrsList.getLength(); i++) {
               aggrs[i] = new VSAggregateRef();
               aggrs[i].parseXML((Element) aggrsList.item(i));
            }
         }
      }

      Element colsNode = Tool.getChildNodeByTagName(elem, "colheaders");

      if(colsNode != null) {
         NodeList colsList =
            Tool.getChildNodesByTagName(colsNode, "dataRef");

         if(colsList != null && colsList.getLength() > 0) {
            cols = new VSDimensionRef[colsList.getLength()];

            for(int i = 0; i < colsList.getLength(); i++) {
               cols[i] = new VSDimensionRef();
               cols[i].parseXML((Element) colsList.item(i));
            }
         }
      }

      Element rowsNode = Tool.getChildNodeByTagName(elem, "rowheaders");

      if(rowsNode != null) {
         NodeList rowsList =
            Tool.getChildNodesByTagName(rowsNode, "dataRef");

         if(rowsList != null && rowsList.getLength() > 0) {
            rows = new VSDimensionRef[rowsList.getLength()];

            for(int i = 0; i < rowsList.getLength(); i++) {
               rows[i] = new VSDimensionRef();
               rows[i].parseXML((Element) rowsList.item(i));
            }
         }
      }

      Element aggrs2Node = Tool.getChildNodeByTagName(elem, "runtime_aggregates");

      if(aggrs2Node != null) {
         NodeList aggrsList =
            Tool.getChildNodesByTagName(aggrs2Node, "dataRef");

         if(aggrsList != null && aggrsList.getLength() > 0) {
            aggrs2 = new VSAggregateRef[aggrsList.getLength()];

            for(int i = 0; i < aggrsList.getLength(); i++) {
               aggrs2[i] = new VSAggregateRef();
               aggrs2[i].parseXML((Element) aggrsList.item(i));
            }
         }
      }

      Element cols2Node = Tool.getChildNodeByTagName(elem, "runtime_colheaders");

      if(cols2Node != null) {
         NodeList colsList =
            Tool.getChildNodesByTagName(cols2Node, "dataRef");

         if(colsList != null && colsList.getLength() > 0) {
            cols2 = new VSDimensionRef[colsList.getLength()];

            for(int i = 0; i < colsList.getLength(); i++) {
               cols2[i] = new VSDimensionRef();
               cols2[i].parseXML((Element) colsList.item(i));
            }
         }
      }

      Element rows2Node = Tool.getChildNodeByTagName(elem, "runtime_rowheaders");

      if(rows2Node != null) {
         NodeList rowsList =
            Tool.getChildNodesByTagName(rows2Node, "dataRef");

         if(rowsList != null && rowsList.getLength() > 0) {
            rows2 = new VSDimensionRef[rowsList.getLength()];

            for(int i = 0; i < rowsList.getLength(); i++) {
               rows2[i] = new VSDimensionRef();
               rows2[i].parseXML((Element) rowsList.item(i));
            }
         }
      }

      Element dcRefNode = Tool.getChildNodeByTagName(elem, "rDateComparisonRefs");
      rDateComparisonRefs = null;

      if(dcRefNode != null) {
         NodeList nodes = Tool.getChildNodesByTagName(dcRefNode, "dataRef");

         if(nodes != null) {
            rDateComparisonRefs = new DataRef[nodes.getLength()];
         }

         for(int i = 0; i < nodes.getLength(); i++) {
            rDateComparisonRefs[i] = AbstractDataRef.createDataRef((Element) nodes.item(i));
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<VSCrosstabInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</VSCrosstabInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" fillWithZero=\"" + isFillBlankWithZero() + "\"");
      writer.print(" fillWithZeroValue=\"" + getFillBlankWithZeroValue() + "\"");
      writer.print(" sideBySide=\"" + isSummarySideBySide() + "\"");
      writer.print(" sideBySideValue=\"" + getSummarySideBySideValue() + "\"");
      writer.print(" mergeSpan=\"" + isMergeSpan() + "\"");
      writer.print(" mergeSpanValue=\"" + getMergeSpanValue() + "\"");
      writer.print(" sortOthersLast=\"" + isSortOthersLast() + "\"");
      writer.print(" calculateTotal=\"" + isCalculateTotal() + "\"");
      writer.print(" sortOthersLastValue=\"" + getSortOthersLastValue() + "\"");
      writer.print(" calculateTotalValue=\"" + getCalculateTotalValue() + "\"");

      if(rows2 != null && rows3 != null && rows3.length == rows2.length + 1) {
         writer.print(" period=\"true\"");
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(ainfo != null) {
         writer.println("<ainfo>");
         ainfo.writeXML(writer);
         writer.println("</ainfo>");
      }

      if(colTotalVisibleValue.getDValue() != null) {
         writer.print("<colTotalVisibleValue>");
         writer.print("<![CDATA[" + colTotalVisibleValue.getDValue() + "]]>");
         writer.println("</colTotalVisibleValue>");
      }

      if(colTotalVisibleValue.getRuntimeValue(true) != null) {
         writer.print("<colTotalVisibleRValue>");
         writer.print("<![CDATA[" + colTotalVisibleValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</colTotalVisibleRValue>");
      }

      if(rowTotalVisibleValue.getDValue() != null) {
         writer.print("<rowTotalVisibleValue>");
         writer.print("<![CDATA[" + rowTotalVisibleValue.getDValue() + "]]>");
         writer.println("</rowTotalVisibleValue>");
      }

      if(rowTotalVisibleValue.getRuntimeValue(true) != null) {
         writer.print("<rowTotalVisibleRValue>");
         writer.print("<![CDATA[" + rowTotalVisibleValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</rowTotalVisibleRValue>");
      }

      if(percentageByValue.getDValue() != null) {
         writer.print("<percentageByValue>");
         writer.print("<![CDATA[" + percentageByValue.getDValue() + "]]>");
         writer.println("</percentageByValue>");
      }

      if(percentageByValue.getRuntimeValue(true) != null) {
         writer.print("<percentageByRValue>");
         writer.print("<![CDATA[" + percentageByValue.getRuntimeValue(true) +
                      "]]>");
         writer.println("</percentageByRValue>");
      }

      int[] rids = null;

      if(aggrs != null && aggrs.length > 0) {
         rids = new int[aggrs.length];
         Arrays.fill(rids, -1);
         writer.print("<aggregates>");

         for(int i = 0; i < aggrs.length; i++) {
            if(aggrs[i] instanceof VSAggregateRef) {
               rids[i] = i;

               if(updateRuntimeID) {
                  ((VSAggregateRef) aggrs[i]).setRuntimeID(rids[i]);
               }
            }

            aggrs[i].writeXML(writer);
         }

         writer.println("</aggregates>");
      }

      updateDimensionRuntimeID();

      if(cols != null && cols.length > 0) {
         writer.print("<colheaders>");

         for(int i = 0; i < cols.length; i++) {
            cols[i].writeXML(writer);
         }

         writer.println("</colheaders>");
      }

      if(rows != null && rows.length > 0) {
         writer.print("<rowheaders>");

         for(int i = 0; i < rows.length; i++) {
            rows[i].writeXML(writer);
         }

         writer.println("</rowheaders>");
      }

      if(aggrs2 != null && aggrs2.length > 0) {
         writer.print("<runtime_aggregates>");

         for(int i = 0; i < aggrs2.length; i++) {
            if(updateRuntimeID && aggrs2[i] instanceof VSAggregateRef &&
               rids != null && i < rids.length)
            {
               ((VSAggregateRef) aggrs2[i]).setRuntimeID(rids[i]);
            }

            aggrs2[i].writeXML(writer);
         }

         writer.println("</runtime_aggregates>");
      }

      if(cols2 != null && cols2.length > 0) {
         writer.print("<runtime_colheaders>");

         for(int i = 0; i < cols2.length; i++) {
            cols2[i].writeXML(writer);
         }

         writer.println("</runtime_colheaders>");
      }

      if(rows2 != null && rows2.length > 0) {
         writer.print("<runtime_rowheaders>");

         for(int i = 0; i < rows2.length; i++) {
            rows2[i].writeXML(writer);
         }

         writer.println("</runtime_rowheaders>");
      }

      if(rDateComparisonRefs != null) {
         writer.println("<rDateComparisonRefs>");

         for(DataRef ref : rDateComparisonRefs) {
            if(ref == null) {
               continue;
            }

            ref.writeXML(writer);
         }

         writer.println("</rDateComparisonRefs>");
      }
   }

   private void updateDimensionRuntimeID() {
      int[] rids0 = null;

      if(cols != null && rows != null && updateRuntimeID) {
         rids0 = new int[rows.length + cols.length];
         Arrays.fill(rids0, -1);

         for(int i = 0; i < cols.length; i++) {
            rids0[i] = i;

            if(cols[i] instanceof VSDimensionRef) {
               ((VSDimensionRef) cols[i]).setRuntimeID(rids0[i]);
            }
         }

         for(int j = 0; j < rows.length; j++) {
            rids0[j + cols.length] = j + cols.length;

            if(rows[j] instanceof VSDimensionRef) {
               ((VSDimensionRef) rows[j]).setRuntimeID(rids0[j + cols.length]);
            }
         }
      }

      if(cols2 != null && cols2.length > 0) {
         for(int i = 0; i < cols2.length; i++) {
            if(updateRuntimeID && cols2[i] instanceof VSDimensionRef &&
               rids0 != null && i < rids0.length)
            {
               ((VSDimensionRef) cols2[i]).setRuntimeID(rids0[i]);
            }
         }
      }

      if(rows2 != null && rows2.length > 0) {
         for(int i = 0; i < rows2.length; i++) {
            int colLen = cols2 == null ? 0 : cols2.length;

            if(updateRuntimeID && rows2[i] instanceof VSDimensionRef &&
               rids0 != null && i < rids0.length - colLen)
            {
               ((VSDimensionRef) rows2[i]).setRuntimeID(rids0[i + colLen]);
            }
         }
      }
   }

   public void updateRuntimeId() {
      updateTwoListRuntimeId(cols, rows);
      updateTwoListRuntimeId(cols2, rows2);
      updateAggregateRuntimeID();
   }

   private void updateTwoListRuntimeId(DataRef[] firstRefs, DataRef[] secondRefs) {
      int[] rids;

      if((firstRefs != null || secondRefs != null) && updateRuntimeID) {
         int rowCount = secondRefs != null ? secondRefs.length : 0;
         int colCount = firstRefs != null ? firstRefs.length : 0;
         rids = new int[rowCount + colCount];
         Arrays.fill(rids, -1);

         for(int i = 0; i < colCount; i++) {
            rids[i] = i;

            if(firstRefs[i] instanceof VSDimensionRef) {
               ((VSDimensionRef) firstRefs[i]).setRuntimeID(rids[i]);
            }
         }

         for(int j = 0; j < rowCount; j++) {
            rids[j + colCount] = j + colCount;

            if(secondRefs[j] instanceof VSDimensionRef) {
               ((VSDimensionRef) secondRefs[j]).setRuntimeID(rids[j + colCount]);
            }
         }
      }
   }

   private void updateAggregateRuntimeID() {
      int[] rids = null;

      if(aggrs != null && aggrs.length > 0) {
         rids = new int[aggrs.length];
         Arrays.fill(rids, -1);

         for(int i = 0; i < aggrs.length; i++) {
            if(aggrs[i] instanceof VSAggregateRef) {
               rids[i] = i;

               if(updateRuntimeID) {
                  ((VSAggregateRef) aggrs[i]).setRuntimeID(rids[i]);
               }
            }
         }
      }

      if(aggrs2 != null && aggrs2.length > 0) {
         for(int i = 0; i < aggrs2.length; i++) {
            if(updateRuntimeID && aggrs2[i] instanceof VSAggregateRef &&
               rids != null && i < rids.length)
            {
               ((VSAggregateRef) aggrs2[i]).setRuntimeID(rids[i]);
            }
         }
      }
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         VSCrosstabInfo info = (VSCrosstabInfo) super.clone();

         if(ainfo != null) {
            info.ainfo = (AggregateInfo) ainfo.clone();
         }

         if(aggrs != null && aggrs.length > 0) {
            info.aggrs = new DataRef[aggrs.length];

            for(int i = 0; i < aggrs.length; i++) {
               info.aggrs[i] = (DataRef) aggrs[i].clone();
            }
         }

         if(cols != null && cols.length > 0) {
            info.cols = new DataRef[cols.length];

            for(int i = 0; i < cols.length; i++) {
               info.cols[i] = (DataRef) cols[i].clone();
            }
         }

         if(rows != null && rows.length > 0) {
            info.rows = new DataRef[rows.length];

            for(int i = 0; i < rows.length; i++) {
               info.rows[i] = (DataRef) rows[i].clone();
            }
         }

         if(aggrs2 != null && aggrs2.length > 0) {
            info.aggrs2 = new DataRef[aggrs2.length];

            for(int i = 0; i < aggrs2.length; i++) {
               info.aggrs2[i] = (DataRef) aggrs2[i].clone();
            }
         }

         if(cols2 != null && cols2.length > 0) {
            info.cols2 = new DataRef[cols2.length];

            for(int i = 0; i < cols2.length; i++) {
               info.cols2[i] = (DataRef) cols2[i].clone();
            }
         }

         if(rows2 != null && rows2.length > 0) {
            info.rows2 = new DataRef[rows2.length];

            for(int i = 0; i < rows2.length; i++) {
               info.rows2[i] = (DataRef) rows2[i].clone();
            }
         }

         if(fillWithZeroValue != null) {
            info.fillWithZeroValue = (DynamicValue2) fillWithZeroValue.clone();
         }

         if(sideBySideValue != null) {
            info.sideBySideValue = (DynamicValue2) sideBySideValue.clone();
         }

         if(mergeSpanValue != null) {
            info.mergeSpanValue = (DynamicValue2) mergeSpanValue.clone();
         }

         if(sortOthersLast != null) {
            info.sortOthersLast = (DynamicValue2) sortOthersLast.clone();
         }

         if(calculateTotal != null) {
            info.calculateTotal = (DynamicValue2) calculateTotal.clone();
         }

         info.colTotalVisibleValue = (DynamicValue) colTotalVisibleValue.clone();
         info.rowTotalVisibleValue = (DynamicValue) rowTotalVisibleValue.clone();
         info.percentageByValue = (DynamicValue) percentageByValue.clone();

         if(rDateComparisonRefs != null && rDateComparisonRefs.length > 0) {
            info.rDateComparisonRefs = new DataRef[rDateComparisonRefs.length];

            for(int i = 0; i < rDateComparisonRefs.length; i++) {
               info.rDateComparisonRefs[i] = (DataRef) rDateComparisonRefs[i].clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSCrosstabInfo", ex);
      }

      return null;
   }

   /**
    * Check if equals another object if ignore sorting.
    */
   public boolean equalsIgnoreSorting(Object obj) {
      if(!(obj instanceof VSCrosstabInfo)) {
         return false;
      }

      VSCrosstabInfo info = (VSCrosstabInfo) obj;

      if(!Tool.equalsContent(ainfo, info.ainfo)) {
         return false;
      }

      if(!Tool.equals(colTotalVisibleValue, info.colTotalVisibleValue) ||
         !Tool.equals(rowTotalVisibleValue, info.rowTotalVisibleValue) ||
         !Tool.equals(percentageByValue, info.percentageByValue))
      {
         return false;
      }

      if(!Tool.equals(fillWithZeroValue, info.fillWithZeroValue) ||
         !Tool.equals(sideBySideValue, info.sideBySideValue) ||
         !Tool.equals(mergeSpanValue, info.mergeSpanValue) ||
         fillWithZeroValue.getRValue() != info.fillWithZeroValue.getRValue() ||
         sideBySideValue.getRValue() != info.sideBySideValue.getRValue() ||
         mergeSpanValue.getRValue() != info.mergeSpanValue.getRValue() ||
         sortOthersLast.getRValue() != info.sortOthersLast.getRValue() ||
         !Tool.equals(calculateTotal.getRValue(), info.calculateTotal.getRValue()))
      {
         return false;
      }

      if(aggrs.length != info.aggrs.length || cols.length != info.cols.length ||
         rows.length != info.rows.length)
      {
         return false;
      }

      for(int i = 0; i < aggrs.length; i++) {
         VSAggregateRef aref = (VSAggregateRef) aggrs[i];

         if(!aref.equalsContent(info.aggrs[i])) {
            return false;
         }
      }

      for(int i = 0; i < cols.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) cols[i];

         if(!dref.equalsContentIgnoreSorting(info.cols[i])) {
            return false;
         }
      }

      for(int i = 0; i < rows.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) rows[i];

         if(!dref.equalsContentIgnoreSorting(info.rows[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSCrosstabInfo)) {
         return false;
      }

      VSCrosstabInfo info = (VSCrosstabInfo) obj;

      if(!Tool.equalsContent(ainfo, info.ainfo)) {
         return false;
      }

      if(!Tool.equals(colTotalVisibleValue, info.colTotalVisibleValue) ||
         !Tool.equals(rowTotalVisibleValue, info.rowTotalVisibleValue) ||
         !Tool.equals(percentageByValue, info.percentageByValue))
      {
         return false;
      }

      if(!Tool.equals(fillWithZeroValue, info.fillWithZeroValue) ||
         !Tool.equals(sideBySideValue, info.sideBySideValue) ||
         !Tool.equals(mergeSpanValue, info.mergeSpanValue) ||
         fillWithZeroValue.getRValue() != info.fillWithZeroValue.getRValue() ||
         sideBySideValue.getRValue() != info.sideBySideValue.getRValue() ||
         mergeSpanValue.getRValue() != info.mergeSpanValue.getRValue() ||
         sortOthersLast.getRValue() != info.sortOthersLast.getRValue() ||
         !Tool.equals(calculateTotal.getRValue(), info.calculateTotal.getRValue()))
      {
         return false;
      }

      if(aggrs.length != info.aggrs.length || cols.length != info.cols.length ||
         rows.length != info.rows.length)
      {
         return false;
      }

      for(int i = 0; i < aggrs.length; i++) {
         VSAggregateRef aref = (VSAggregateRef) aggrs[i];

         if(!aref.equalsContent(info.aggrs[i])) {
            return false;
         }
      }

      for(int i = 0; i < cols.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) cols[i];

         if(!dref.equalsContent(info.cols[i])) {
            return false;
         }
      }

      for(int i = 0; i < rows.length; i++) {
         VSDimensionRef dref = (VSDimensionRef) rows[i];

         if(!dref.equalsContent(info.rows[i])) {
            return false;
         }
      }

      DataRef[] dcRef = info.getRuntimeDateComparisonRefs();
      DataRef[] dcRef2 = getRuntimeDateComparisonRefs();

      if(!Tool.equalsContent(dcRef, dcRef2)) {
         return false;
      }
      
      return true;
   }

   /**
    * Check if is runtime.
    * @return <tt>true</tt> if runtime, <tt>false</tt> otherwise.
    */
   public boolean isRuntime() {
      return runtime;
   }

   /**
    * Set whether is runtime.
    * @param runtime <tt>true</tt> if runtime, <tt>false</tt> otherwise.
    */
   public void setRuntime(boolean runtime) {
      this.runtime = runtime;
   }

   private String fixCol(String col) {
      VSAggregateRef find = null;

      for(DataRef ref : aggrs) {
         if(ref instanceof VSAggregateRef) {
            if(((VSAggregateRef) ref).getFullName(false).equals(col)) {
               find = (VSAggregateRef) ref;
               break;
            }
         }
      }

      return find == null || find.isApplyAlias() ? col : find.getVSName();
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   public void update(Viewsheet vs, ColumnSelection columns, XCube cube, boolean aalias,
                      String source, DateComparisonInfo dcInfo)
   {
      runtime = true;
      dcTempGroups = null;
      List<DataRef> list = new ArrayList<>();
      DataRef[] rtAggrs = this.aggrs2;
      DataRef[] rtRowHeaders = this.rows2;
      DataRef[] rtColHeaders = this.cols2;

      try {
         for(int i = 0; i < aggrs.length; i++) {
            VSAggregateRef aggr = (VSAggregateRef) aggrs[i];

            // apply alias to support multiple aggregates on the same column
            applyAlias(aggr, aalias);

            try {
               list.addAll(aggr.update(vs, columns));
            }
            catch(ColumnNotFoundException ex) {
               if(aggr.isScript() || aggr.isVariable()) {
                  list.add(aggr);

                  if(aggr.isScript()) {
                     throw ex;
                  }
               }
            }
         }

         VSAggregateRef[] aggrs2 = new VSAggregateRef[list.size()];
         list.toArray(aggrs2);
         rtAggrs = aggrs2;
         list = new ArrayList<>();

         for(int i = 0; i < cols.length; i++) {
            VSDimensionRef dim = (VSDimensionRef) cols[i];
            String rcol = dim.getRankingCol();
            rcol = fixCol(rcol);
            dim.setRankingCol(rcol);
            String scol = dim.getSortByCol();
            scol = fixCol(scol);
            dim.setSortByCol(scol);

            try {
               List<DataRef> refs = dim.update(vs, columns);
               dim.updateRanking(aggrs2);

               if(dim.getRankingCondition() == null) {
                  dim.updateRanking(columns);
               }

               updateDimensionRanking(refs, dim);

               for(DataRef ref : refs) {
                  if(!containsInList(list, ref)) {
                     list.add(ref);
                  }
               }
            }
            catch(ColumnNotFoundException ex) {
               if(dim.isDynamic()) {
                  list.add(dim);
                  throw ex;
               }
            }
         }

         VSDimensionRef[] cols2 = new VSDimensionRef[list.size()];
         list.toArray(cols2);

         if(!Tool.equalsContent(rtColHeaders, cols2) && !designRefsChanged) {
            copyRuntimeProperties(rtColHeaders, cols2);
         }

         rtColHeaders = cols2;
         list = new ArrayList<>();

         for(int i = 0; i < rows.length; i++) {
            VSDimensionRef dim = (VSDimensionRef) rows[i];
            String rcol = dim.getRankingCol();
            rcol = fixCol(rcol);
            dim.setRankingCol(rcol);
            String scol = dim.getSortByCol();
            scol = fixCol(scol);
            dim.setSortByCol(scol);

            try {
               List<DataRef> refs = dim.update(vs, columns);
               dim.updateRanking(aggrs2);

               if(dim.getRankingCondition() == null) {
                  dim.updateRanking(columns);
               }

               updateDimensionRanking(refs, dim);

               for(DataRef ref : refs) {
                  if(!isCalcTableTempCrosstab() || !containsInList(list, ref)) {
                     list.add(ref);
                  }
               }
            }
            catch(ColumnNotFoundException ex) {
               if(dim.isDynamic()) {
                  list.add(dim);
                  throw ex;
               }
            }
         }

         VSDimensionRef[] rows2 = new VSDimensionRef[list.size()];
         list.toArray(rows2);

         if(!Tool.equalsContent(rtRowHeaders, rows2) && !designRefsChanged) {
            copyRuntimeProperties(rtRowHeaders, rows2);
         }

         rtRowHeaders = rows2;
         this.rows3 = null;

         if(dcInfo != null &&
            DateComparisonUtil.crosstabSupportDateComparison(rtRowHeaders, rtColHeaders, rtAggrs))
         {
            CrosstabDcProcessor dcProcessor =
               new CrosstabDcProcessor(this, dcInfo, rtRowHeaders, rtColHeaders, rtAggrs);
            dcProcessor.process(source, vs);
         }
         else {
            setRuntimeDateComparisonRefs(null);

            synchronized(this) {
               this.aggrs2 = rtAggrs;
               this.rows2 = rtRowHeaders;
               this.cols2 = rtColHeaders;
            }
         }
      }
      finally {
         for(int i = 0; i < aggrs.length; i++) {
            VSAggregateRef aggr = (VSAggregateRef) aggrs[i];
            aggr.setApplyAlias(false);
         }

         designRefsChanged = false;
      }
   }

   private void updateDimensionRanking(List<DataRef> refs, VSDimensionRef dim) {
      for(int i = 0; i < refs.size(); i++) {
         DataRef ref = refs.get(i);

         if(ref instanceof VSDimensionRef && Tool.equals(dim.getFullName(),
            ((VSDimensionRef) ref).getFullName()))
         {
            ((VSDimensionRef) ref).setRankingCondition(dim.getRankingCondition());
         }
      }
   }

   // make sure runtime settings (named group) is not lost when it's set on dynamically
   // generated columns. (49736)
   private void copyRuntimeProperties(DataRef[] from, VSDimensionRef[] to) {
      for(final VSDimensionRef dref : to) {
         if(VSUtil.isDynamicValue(dref.getGroupColumnValue())) {
            Arrays.stream(from)
               .map(a -> (VSDimensionRef) a)
               .filter(a -> a.getDataRef().equals(dref.getDataRef()))
               .forEach(a -> {
                  dref.setNamedGroupInfo(a.getNamedGroupInfo());
                  dref.setOrder(a.getOrder());
                  dref.setGroupType(a.getGroupType());
               });
         }
      }
   }

   /**
    * Set option to fill blank cell with zero. By default blank cells
    * are left blank. If this is true, the blank cells are filled with zero.
    * @param fill true to fill blank cell with zero.
    */
   public void setFillBlankWithZero(boolean fill) {
      fillWithZeroValue.setRValue(fill);
   }

   /**
    * Check if fill blank cell with zero.
    * @return <tt>true</tt> if should fill blank cell with zero,
    * <tt>false</tt> otherwise.
    */
   public boolean isFillBlankWithZero() {
      return Boolean.valueOf(fillWithZeroValue.getRuntimeValue(true) + "");
   }

   /**
    * Set option to fill blank cell with zero. By default blank cells
    * are left blank. If this is true, the blank cells are filled with zero.
    * @param fill true to fill blank cell with zero.
    */
   public void setFillBlankWithZeroValue(boolean fill) {
      this.fillWithZeroValue.setDValue(fill + "");
   }

   /**
    * Check if fill blank cell with zero.
    * @return <tt>true</tt> if should fill blank cell with zero,
    * <tt>false</tt> otherwise.
    */
   public boolean getFillBlankWithZeroValue() {
      return fillWithZeroValue.getBooleanValue(true, false);
   }

   /**
    * If this option is true, and there are multiple summary cells, they are
    * arranged side by side in the table. Otherwise they are arranged vertically.
    * Defaults to false.
    */
   public void setSummarySideBySide(boolean horizontal) {
      sideBySideValue.setRValue(horizontal);
   }

   /**
    * Check if summary cells are put side by side.
    * @return <tt>true</tt> if summary cells are put side by side,
    * <tt>false</tt> otherwise.
    */
   public boolean isSummarySideBySide() {
      return  Boolean.valueOf(sideBySideValue.getRuntimeValue(true) + "");
   }

   /**
    * If this option is true, and there are multiple summary cells, they are
    * arranged side by side in the table. Otherwise they are arranged vertically.
    * Defaults to false.
    */
   public void setSummarySideBySideValue(boolean horizontal) {
      sideBySideValue.setDValue(horizontal + "");
   }

   /**
    * Check if summary cells are put side by side.
    * @return <tt>true</tt> if summary cells are put side by side,
    * <tt>false</tt> otherwise.
    */
   public boolean getSummarySideBySideValue() {
      return sideBySideValue.getBooleanValue(true, true);
   }

   /**
    * Check whether merge  up/down is enabled.
    */
   public boolean isMergeSpan() {
      return Boolean.valueOf(mergeSpanValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether drill up/down is enabled.
    */
   public void setMergeSpan(boolean drill) {
      mergeSpanValue.setRValue(drill);
   }

   /**
    * Get whether drill up/down is enabled.
    */
   public boolean getMergeSpanValue() {
      return mergeSpanValue.getBooleanValue(true, true);
   }

   /**
    * Set whether drill up/down is enabled.
    */
   public void setMergeSpanValue(boolean merge) {
      mergeSpanValue.setDValue(merge + "");
   }

   /**
    * Check if 'Others' group should always be sorted as the last item.
    */
   public boolean isSortOthersLast() {
      return Boolean.parseBoolean(sortOthersLast.getRuntimeValue(true) + "");
   }

   /**
    * Set if 'Others' group should always be sorted as the last item.
    */
   public void setSortOthersLast(boolean sortOthersLast) {
      this.sortOthersLast.setRValue(sortOthersLast);
   }

   /**
    * Check if 'Others' group should always be sorted as the last item.
    */
   public boolean getSortOthersLastValue() {
      return sortOthersLast.getBooleanValue(true, true);
   }

   /**
    * Set if 'Others' group should always be sorted as the last item.
    */
   public void setSortOthersLastValue(boolean sortOthersLast) {
      this.sortOthersLast.setDValue(sortOthersLast + "");
   }

   /**
    * Check if calculate Total for the Trend and Comparison.
    */
   public boolean isCalculateTotal() {
      return Boolean.parseBoolean(calculateTotal.getRuntimeValue(true) + "");
   }

   /**
    * Set if calculate Total for the Trend and Comparison.
    */
   public void setCalculateTotal(boolean calculateTotal) {
      this.calculateTotal.setRValue(calculateTotal);
   }

   /**
    * Check if calculate Total for the Trend and Comparison.
    */
   public boolean getCalculateTotalValue() {
      return calculateTotal.getBooleanValue(true, false);
   }

   /**
    * Set if calculate Total for the Trend and Comparison.
    */
   public void setCalculateTotalValue(boolean sortOthersLast) {
      this.calculateTotal.setDValue(sortOthersLast + "");
   }

   /**
    * Get aggregate info.
    */
   public AggregateInfo getAggregateInfo() {
      return ainfo;
   }

   /**
    * Set aggregate info.
    */
   public void setAggregateInfo(AggregateInfo ainfo) {
      this.ainfo = ainfo;
   }

   /**
    * Reset runtime values.
    */
   public void resetRuntimeValues() {
      fillWithZeroValue.setRValue(null);
      sideBySideValue.setRValue(null);
      mergeSpanValue.setRValue(null);
      sortOthersLast.setRValue(null);
      calculateTotal.setRValue(null);
   }

   public void removeFormulaFields(Set<String> calcFieldsRefs) {
      DataRef[] cols = getColHeaders();

      if(cols != null && cols.length > 0) {
         List<DataRef> updateCols = Arrays.stream(cols)
            .filter(col -> !calcFieldsRefs.contains(col.getName()))
            .collect(toList());

         setDesignColHeaders(updateCols.toArray(new DataRef[updateCols.size()]));
      }

      DataRef[] rows = getRowHeaders();

      if(rows != null && rows.length > 0) {
         List<DataRef> updateRows = Arrays.stream(rows)
            .filter(row -> !calcFieldsRefs.contains(row.getName()))
            .collect(toList());

         setDesignRowHeaders(updateRows.toArray(new DataRef[updateRows.size()]));
      }

      DataRef[] aggs = getAggregates();

      if(aggs != null && aggs.length > 0) {
         List<DataRef> updateAggs = Arrays.stream(aggs)
            .filter(agg -> !calcFieldsRefs.contains(agg.getName()))
            .collect(toList());

        setDesignAggregates(updateAggs.toArray(new DataRef[updateAggs.size()]));
      }
   }

   /**
    * Parse attribute properly.
    * @param elem the specified xml element.
    * @param prop the old property name.
    * @param def the default value.
    */
   private String getAttributeStr(Element elem, String prop, String def) {
      String attr = Tool.getAttribute(elem, prop + "Value");
      attr = attr == null ? Tool.getAttribute(elem, prop) : attr;

      return attr == null ? def : attr;
   }

   /**
    * Apply alias if necessary.
    */
   private void applyAlias(VSAggregateRef aggRef, boolean aalias) {
      if(aalias && (aggRef.getRefType() & DataRef.CUBE) == DataRef.CUBE) {
         AggregateFormula formula = aggRef.getFormula();
         aalias = !AggregateFormula.NONE.equals(formula);
      }

      aggRef.setApplyAlias(aalias);
   }

   /**
    * Get the row header count.
    */
   public int getHeaderColCount() {
      int hcols = getRowHeaders().length;
      return getHeaderColCount0(hcols);
   }

   /**
    * Get the runtime row header count.
    */
   public int getRuntimeHeaderColCount() {
      int hcols = getRuntimeRowHeaders().length;
      return getHeaderColCount0(hcols);
   }

   /**
    * Get the row header count with period.
    */
   public int getHeaderColCountWithPeriod() {
      int hcols = rows3 != null ? rows3.length : getRowHeaders().length;
      return getHeaderColCount0(hcols);
   }

   /**
    * Get the runtime row header count with period.
    */
   public int getRuntimeHeaderColCountWithPeriod() {
      int hcols = rows3 != null ? rows3.length : getRuntimeRowHeaders().length;
      return getRuntimeHeaderColCount0(hcols);
   }

   private int getRuntimeHeaderColCount0(int hcols) {
      // we always add one col as header unless there is only aggregate
      if(hcols == 0 && getRuntimeColHeaders().length > 0) {
         hcols = 1;
      }

      // now there is no property to indicate if show summary headers, summary
      // headers are always shown if aggreate count is large than 1
      if(getRuntimeRowHeaders().length > 0 && getRuntimeAggregates().length > 1 &&
         !isSummarySideBySide())
      {
         hcols++;
      }

      return hcols;
   }

   /**
    * Get the row header count.
    */
   private int getHeaderColCount0(int hcols) {
      // we always add one col as header unless there is only aggregate
      if(hcols == 0 && getColHeaders().length > 0) {
         hcols = 1;
      }

      // now there is no property to indicate if show summary headers, summary
      // headers are always shown if aggreate count is large than 1
      if(getRowHeaders().length > 0 && getAggregates().length > 1 &&
         !isSummarySideBySide())
      {
         hcols++;
      }

      return hcols;
   }

   /**
    * Get the column header count.
    */
   public int getHeaderRowCount() {
      int cnt = getRuntimeColHeaders().length;

      // add the col header count only if aggregate count is large than 1
      if(isSummarySideBySide() && getAggregates().length > 1) {
         cnt++;
      }

      return Math.max(1, cnt);
   }

   public DataRef[] getRowHeaders() {
      return rows;
   }

   public DataRef[] getColHeaders() {
      return cols;
   }

   public DataRef[] getAggregates() {
      return aggrs;
   }

   public boolean isSortOthersLastEnabled() {
      DataRef[][] dataRefs = new DataRef[][] {rows2, cols2};
      List<DataRef> refs = new ArrayList<>();

      for(DataRef[] dataRef : dataRefs) {
         if(dataRef != null) {
            refs.addAll(Arrays.asList(dataRef));
         }
      }

      return refs.stream().anyMatch(ref ->
         ref instanceof XDimensionRef && ((XDimensionRef) ref).isRankingGroupOthers());
   }

   public void updateRuntimeID(boolean update) {
      updateRuntimeID = update;
   }

   /**
    * Remove the crosstable binding fields.
    */
   public void removeFields() {
      ainfo = new AggregateInfo();
      aggrs = new DataRef[0];
      cols = new DataRef[0];
      rows = new DataRef[0];
      aggrs2 = new DataRef[0];
      cols2 = new DataRef[0];
      rows2 = new DataRef[0];
   }

   /**
    * Get the runtime date comparison fields.
    */
   public DataRef[] getRuntimeDateComparisonRefs() {
      return rDateComparisonRefs;
   }

   /**
    * Set the runtime date comparison fields.
    */
   public void setRuntimeDateComparisonRefs(DataRef[] rDateComparisonRefs) {
      this.rDateComparisonRefs = rDateComparisonRefs != null ? rDateComparisonRefs : new DataRef[0];
   }

   public void setDateComparisonRef(VSDataRef ref) {
      this.dateComparisonRef = ref;
   }

   public VSDataRef getDateComparisonRef() {
      return dateComparisonRef;
   }

   public boolean isDateComparisonOnRow() {
      return dateComparisonOnRow;
   }

   /**
    * Set whether date comparison dimension is on row header.
    */
   public void setDateComparisonOnRow(boolean dateComparisonOnRow) {
      this.dateComparisonOnRow = dateComparisonOnRow;
   }

   public boolean isCalcTableTempCrosstab() {
      return calcTableTempCrosstab;
   }

   public void setCalcTableTempCrosstab(boolean calcTableTempCrosstab) {
      this.calcTableTempCrosstab = calcTableTempCrosstab;
   }

   private static boolean containsInList(List<DataRef> list, DataRef ref) {
      if(ref == null || list == null || list.size() == 0) {
         return false;
      }

      for(int i = 0; i < list.size(); i++) {
         if(Tool.equals(((VSDimensionRef) list.get(i)).getFullName(),
            ((VSDimensionRef) ref).getFullName()))
         {
            return true;
         }
      }

      return false;
   }

   @Override
   public XDimensionRef[] getDcTempGroups() {
      return dcTempGroups == null ? new XDimensionRef[0] : dcTempGroups;
   }

   @Override
   public void setDcTempGroups(XDimensionRef[] dcTempGroups) {
      this.dcTempGroups = dcTempGroups;
   }

   @Override
   public boolean isAppliedDateComparison() {
      return rDateComparisonRefs != null && rDateComparisonRefs.length > 0;
   }

   private DynamicValue colTotalVisibleValue;
   private DynamicValue rowTotalVisibleValue;
   private DynamicValue percentageByValue;
   private AggregateInfo ainfo = null;
   private DynamicValue2 fillWithZeroValue;
   private DynamicValue2 sideBySideValue;
   private DynamicValue2 mergeSpanValue;
   private DynamicValue2 sortOthersLast;
   private DynamicValue2 calculateTotal;
   private DataRef[] aggrs;
   private DataRef[] cols;
   private DataRef[] rows;
   // runtime
   private boolean runtime;
   private DataRef[] aggrs2;
   private DataRef[] cols2;
   private DataRef[] rows2;
   private DataRef[] rows3;
   private VSDataRef dateComparisonRef;
   private DataRef[] rDateComparisonRefs;
   private boolean dateComparisonOnRow;
   private transient boolean updateRuntimeID = true;
   private boolean designRefsChanged = false;
   private boolean calcTableTempCrosstab;
   private XDimensionRef[] dcTempGroups;

   private static final Logger LOG = LoggerFactory.getLogger(VSCrosstabInfo.class);
}
