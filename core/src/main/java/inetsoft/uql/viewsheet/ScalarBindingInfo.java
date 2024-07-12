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
package inetsoft.uql.viewsheet;

import inetsoft.report.composition.execution.BoundTableNotFoundException;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * ScalarBindingInfo contains scalar binding information. The information will
 * be executed to fill the data consumer with scalar data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ScalarBindingInfo extends BindingInfo {
   /**
    * Constructor.
    */
   public ScalarBindingInfo() {
      super();

      columnValue = new DynamicValue();
      column2Value = new DynamicValue();
      nValue = new DynamicValue("0", XSchema.INTEGER);
      aggregateValue = new DynamicValue(XConstants.NONE_FORMULA, XSchema.STRING,
         AggregateFormula.getIdentifiers(true));
   }

   /**
    * Get the aggregate formula.
    * @return the aggregate formula of this scalar binding info.
    */
   public AggregateFormula getAggregateFormula() {
      Object value = aggregateValue.getRuntimeValue(true);
      String text = value.toString();

      return AggregateFormula.getFormula(text);
   }

   /**
    * Get the aggregate value.
    * @return the aggregate value of this scalar binding info.
    */
   public String getAggregateValue() {
      return aggregateValue.getDValue();
   }

   /**
    * Get the runtime aggregate value.
    * @return the runtime aggregate value of this scalar binding info.
    */
   public String getRuntimeAggregateValue() {
      return aggregateValue.getRValue() + "";
   }

   /**
    * Set the aggregate value to this scalar binding info.
    * @param aggregate the specified aggregate value.
    */
   public void setAggregateValue(String aggregate) {
      this.aggregateValue.setDValue(aggregate);
   }

   /**
    * Set the bound column.
    * @param column the bound column of this scalar binding info.
    */
   public void setColumn(DataRef column) {
      this.column = column;
   }

   /**
    * Get the bound column.
    * @return the bound column of this scalar binding info.
    */
   public DataRef getColumn() {
      return column;
   }

   /**
    * Get the bound column value.
    * @return the bound column of this scalar binding info.
    */
   public String getColumnValue() {
      return columnValue.getDValue();
   }

   /**
    * Get the bound runtime column value.
    * @return the bound column of this scalar binding info.
    */
   public String getRuntimeColumnValue() {
      return columnValue.getRValue() + "";
   }

   /**
    * Set the bound column value to this scalar binding info.
    * @param columnValue the specified bound column.
    */
   public void setColumnValue(String columnValue) {
      this.columnValue.setDValue(columnValue);
   }

   /**
    * Get the bound column datatype.
    * @return the bound column datatype of this scalar binding info.
    */
   public String getColumnType() {
      return this.columnType;
   }

   /**
    * Set the bound column datatype to this scalar binding info.
    * @param dataType the specified bound column datatype.
    */
   public void setColumnType(String dataType) {
      this.columnType = dataType;
   }

   /**
    * update column type according to aggregate formula.
    * @param formula aggregate formula
    * @param columnType column type
    */
   public void changeColumnType(String formula, String columnType) {
      setColumnType(columnType);

      if(formula != null && !formula.isEmpty() && !formula.equals("=")) {
         AggregateFormula af = AggregateFormula.getFormula(formula);
         String dataType = af == null ? null : af.getDataType();

         if(dataType != null && !dataType.isEmpty()) {
            setColumnType(dataType);
         }
      }
   }

   /**
    * Get the secondary column.
    * @return the secondary column of this scalar binding info.
    */
   public DataRef getSecondaryColumn() {
      return column2;
   }

   /**
    * Get the bound column value.
    * @return the bound column of this scalar binding info.
    */
   public String getColumn2Value() {
      return column2Value.getDValue();
   }

   /**
    * Get the bound runtime column value.
    * @return the bound column of this scalar binding info.
    */
   public String getRuntimeColumn2Value() {
      return column2Value.getRValue() + "";
   }

   /**
    * Set the bound column value to this scalar binding info.
    * @param column2Value the specified bound column.
    */
   public void setColumn2Value(String column2Value) {
      this.column2Value.setDValue(column2Value);
   }

   /**
    * Get the n value for nth and pth formula.
    */
   public String getNValue() {
      return nValue.getDValue();
   }

   /**
    * Get the runtime n value for nth and pth formula.
    */
   public String getRuntimeNValue() {
      return nValue.getRValue() + "";
   }

   /**
    * Set the n value for nth and pth formula.
    */
   public void setNValue(String nval) {
      this.nValue.setDValue(nval);
   }

   /**
    * Get the runtime value for N.
    */
   public int getN() {
      Integer n = (Integer) nValue.getRuntimeValue(true);
      return n == null ? 0 : n;
   }

   /**
    * Set the runtime value for N.
    */
   public void setN(int n) {
      this.nValue.setRValue(n);
   }

   /**
    * Get the dynamic values.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      ArrayList<DynamicValue> list = new ArrayList<>();
      list.add(columnValue);
      list.add(column2Value);
      list.add(nValue);
      list.add(aggregateValue);
      return list;
   }

   /**
    * Set the scale of the result. The result of the binding is divided by
    * the scale to be used for display.
    */
   public void setScale(int scale) {
      this.scale = scale;
   }

   /**
    * Get the scale.
    */
   public int getScale() {
      return scale;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, columnValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, column2Value, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, nValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, aggregateValue, vs);
   }

   private String toView() {
      Catalog catalog = Catalog.getCatalog();
      AggregateFormula formula = getAggregateFormula();
      DataRef column = getColumn();

      if(column == null) {
         return null;
      }

      DataRef column2 = getSecondaryColumn();

      if(formula == null || AggregateFormula.NONE.equals(formula)) {
         return column.getAttribute();
      }

      String view = catalog.getString(formula.getFormulaName()) + "(" +
	      column.getAttribute();

	   if(column2 != null && formula.isTwoColumns()) {
	      view += ", ";
	      view += column2.getAttribute();
	   }

	   if(formula.hasN()) {
	      view += getN();
      }

      view += ")";
      return view;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.print("<scale>" + scale + "</scale>");

      writer.print("<aggregateValue>");
      writer.print("<![CDATA[" + aggregateValue.getDValue() + "]]>");
      writer.println("</aggregateValue>");

      writer.print("<columnValue>");
      writer.print("<![CDATA[" + columnValue.getDValue() + "]]>");
      writer.println("</columnValue>");

      writer.print("<columnType>");
      writer.print("<![CDATA[" + columnType + "]]>");
      writer.println("</columnType>");

      writer.print("<column2Value>");
      writer.print("<![CDATA[" + column2Value.getDValue() + "]]>");
      writer.println("</column2Value>");

      writer.print("<nValue>");
      writer.print("<![CDATA[" + nValue.getDValue() + "]]>");
      writer.println("</nValue>");

      String view = toView();

      if(view != null) {
         writer.print("<view>");
         writer.print("<![CDATA[" + view + "]]>");
         writer.println("</view>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element anode = Tool.getChildNodeByTagName(elem, "aggregateValue");

      if(anode != null) {
         aggregateValue.setDValue(Tool.getValue(anode));
      }

      Element bnode = Tool.getChildNodeByTagName(elem, "columnValue");

      if(bnode != null) {
         columnValue.setDValue(Tool.getValue(bnode));
      }

      Element tnode = Tool.getChildNodeByTagName(elem, "columnType");

      if(tnode != null) {
         setColumnType(Tool.getValue(tnode));
      }

      Element cnode = Tool.getChildNodeByTagName(elem, "column2Value");

      if(cnode != null) {
         column2Value.setDValue(Tool.getValue(cnode));
      }

      cnode = Tool.getChildNodeByTagName(elem, "nValue");

      if(cnode != null) {
         nValue.setDValue(Tool.getValue(cnode));
      }

      Element snode = Tool.getChildNodeByTagName(elem, "scale");

      if(snode != null) {
         scale = Integer.parseInt(Tool.getValue(snode));
      }
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "ScalarBindingInfo: [" + getTableName() + ", " + column + ", " +
             aggregateValue + ", " + scale + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ScalarBindingInfo info = (ScalarBindingInfo) super.clone();

         if(column != null) {
            info.column = (DataRef) column.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ScalarbindingInfo", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof ScalarBindingInfo)) {
         return false;
      }

      ScalarBindingInfo info = (ScalarBindingInfo) obj;

      return Tool.equals(columnValue, info.columnValue) &&
         Tool.equals(column2Value, info.column2Value) &&
         Tool.equals(nValue, info.nValue) &&
         Tool.equals(aggregateValue, info.aggregateValue) &&
         scale == info.scale;
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    */
   public void update(Viewsheet vs) {
      String table = getTableName();

      if(table == null || table.length() == 0) {
         return;
      }

      Worksheet ws = vs.getBaseWorksheet();
      Object obj = ws == null ? null : ws.getAssembly(table);

      if(!(obj instanceof TableAssembly)) {
         throw new BoundTableNotFoundException(Catalog.getCatalog().getString
            ("common.notTable", table));
      }

      TableAssembly assembly = (TableAssembly) obj;
      ColumnSelection cols = assembly.getColumnSelection(true);

      Object cobj = columnValue.getRuntimeValue(true);
      Object c2obj = column2Value.getRuntimeValue(true);

      String ctext = cobj == null ? null : cobj.toString();
      String c2text = c2obj == null ? null : c2obj.toString();

      DataRef colRef = cols.getAttribute(ctext);
      DataRef colRef2 = cols.getAttribute(c2text);

      // @by stephenwebster, For Bug #9172
      // Search on user created calc field only if attribute is not found.
      // Doing it in if/else sequence closes off a small window where the instance
      // variables can be incorrectly set to null.
      column = colRef != null ? colRef : vs.getCalcField(table, ctext);
      column2 = colRef2 != null ? colRef2 : vs.getCalcField(table, c2text);

      if(column == null && !VSUtil.isDynamicValue(columnValue.getDValue())) {
         // column could be hidden by vpm
         /*
         throw new RuntimeException(Catalog.getCatalog().getString
            ("common.columnNotFound", ctext));
         */
         LOG.warn(Catalog.getCatalog().getString("common.columnNotFound", ctext));
      }
   }

   private DynamicValue columnValue;
   private String columnType = XSchema.STRING;
   private DynamicValue column2Value;
   private DynamicValue nValue;
   private DynamicValue aggregateValue;
   private int scale = 1; // display value in thousands (1000), millions, ...
   // runtime
   private DataRef column;
   private DataRef column2;

   private static final Logger LOG =
      LoggerFactory.getLogger(ScalarBindingInfo.class);
}
