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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * InputVSAssemblyInfo, the assembly info of an input assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class InputVSAssemblyInfo extends VSAssemblyInfo {
   /**
    * Constructor.
    */
   public InputVSAssemblyInfo() {
      super();

      dtype = XSchema.STRING;
      columnValue = new DynamicValue();
      rowValue = new DynamicValue(null, XSchema.INTEGER);
      variable = false;
      submitOnChange = new DynamicValue("true", XSchema.BOOLEAN);
      writeBackValue = new DynamicValue2("false", XSchema.BOOLEAN);
   }

   /**
    * Get the target column.
    * @return the target column of this assembly info.
    */
   public DataRef getColumn() {
      return column;
   }

   /**
    * Set the target column to this assembly info.
    * @param column the specified target column.
    */
   public void setColumn(DataRef column) {
      this.column = column;
   }

    /**
    * Get the target column value.
    * @return the target column of this assembly info.
    */
   public String getColumnValue() {
      return columnValue.getDValue();
   }

   /**
    * Set the target column value to this assembly info.
    * @param columnValue the specified target column.
    */
   public void setColumnValue(String columnValue) {
      this.columnValue.setDValue(columnValue);
   }

   /**
    * Get the target data type.
    * @return the target data type of this assembly info.
    */
   public String getDataType() {
      return dtype;
   }

   /**
    * Set the data type to this assembly info.
    * @param dtype the specified data type.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype == null ? XSchema.STRING : dtype;
   }

   /**
    * Get the target row.
    * @return the target row of this assembly info.
    */
   public int getRow() {
      Integer val = (Integer) rowValue.getRuntimeValue(true);
      return val == null ? -1 : val;
   }

   /**
    * Get the target rowValue.
    * @return the target row of this assembly info.
    */
   public String getRowValue() {
      return rowValue.getDValue();
   }

   /**
    * Set the target rowValue to this assembly info.
    * @param rowValue the specified target row.
    */
   public void setRowValue(String rowValue) {
      this.rowValue.setDValue(rowValue);
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   public String getTableName() {
      return table;
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   public void setTableName(String table) {
      this.table = table;
   }

   /**
    * Check whether to refresh viewsheet on submit.
    */
   public boolean isRefresh() {
      return Boolean.valueOf(autoRefresh.getRuntimeValue(true) + "");
   }

   /**
    * Set whether to refresh viewsheet on submit.
    */
   public void setRefresh(boolean refresh) {
      autoRefresh.setRValue(refresh);
   }

   /**
    * Check whether to refresh viewsheet on submit.
    */
   public String getRefreshValue() {
      return autoRefresh.getDValue();
   }

   /**
    * Set whether to refresh viewsheet on submit.
    */
   public void setRefreshValue(String refresh) {
      autoRefresh.setDValue(refresh);
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();

      if(getTableName() != null) {
         list.add(columnValue);
         list.add(rowValue);
      }

      list.add(autoRefresh);

      return list;
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
      super.renameDepended(oname, nname, vs);

      if(getTableName() != null) {
         VSUtil.renameDynamicValueDepended(oname, nname, columnValue, vs);
         VSUtil.renameDynamicValueDepended(oname, nname, rowValue, vs);
      }

      VSUtil.renameDynamicValueDepended(oname, nname, autoRefresh, vs);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" dataType=\"" + dtype + "\"");
      writer.print(" variable=\"" + variable + "\"");
      writer.print(" submit=\"" + isSubmitOnChange() + "\"");
      writer.print(" submitValue=\"" + getSubmitOnChangeValue() + "\"");
      writer.print(" writeBackValue=\"" + getWriteBackValue() + "\"");
      writer.print(" strictNull=\"true\"");
      writer.print(" refreshValue=\"" + getRefreshValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      this.dtype = Tool.getAttribute(elem, "dataType");
      this.variable = "true".equals(Tool.getAttribute(elem, "variable"));
      this.strictNull = "true".equalsIgnoreCase(Tool.getAttribute(elem, "strictNull"));

      // for bc
      if(Tool.getAttribute(elem, "submitValue") != null) {
         setSubmitOnChangeValue(Tool.getAttribute(elem, "submitValue"));
      }

      if(Tool.getAttribute(elem, "writeBackValue") != null) {
         setWriteBackValue("true".equalsIgnoreCase(Tool.getAttribute(elem, "writeBackValue")));
      }

      if(Tool.getAttribute(elem, "refreshValue") != null) {
         setRefreshValue(Tool.getAttribute(elem, "refreshValue"));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(column != null) {
         column.writeXML(writer);
      }

      if(table != null) {
         writer.print("<table>");
         writer.print("<![CDATA[" + table + "]]>");
         writer.println("</table>");
      }

      if(columnValue.getDValue() != null) {
         writer.print("<columnValue>");
         writer.print("<![CDATA[" + columnValue.getDValue() + "]]>");
         writer.println("</columnValue>");
      }

      if(rowValue.getDValue() != null) {
         writer.print("<rowValue>");
         writer.print("<![CDATA[" + rowValue.getDValue() + "]]>");
         writer.println("</rowValue>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element colnode = Tool.getChildNodeByTagName(elem, "dataRef");

      if(colnode != null) {
         column = AbstractDataRef.createDataRef(colnode);
      }

      Element tnode = Tool.getChildNodeByTagName(elem, "table");

      if(tnode != null) {
         table = Tool.getValue(tnode);
      }

      Element cnode = Tool.getChildNodeByTagName(elem, "columnValue");

      if(cnode != null) {
         columnValue.setDValue(Tool.getValue(cnode));
      }

      Element rnode = Tool.getChildNodeByTagName(elem, "rowValue");

      if(rnode != null) {
         rowValue.setDValue(Tool.getValue(rnode));
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public InputVSAssemblyInfo clone(boolean shallow) {
      try {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(column != null) {
               info.column = (DataRef) column.clone();
            }

            if(columnValue != null) {
               info.columnValue = (DynamicValue) columnValue.clone();
            }

            if(rowValue != null) {
               info.rowValue = (DynamicValue) rowValue.clone();
            }

            if(submitOnChange != null) {
               info.submitOnChange = (DynamicValue) submitOnChange.clone();
            }

            if(autoRefresh != null) {
               info.autoRefresh = (DynamicValue) autoRefresh.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone InputVSAssemblyInfo", ex);
      }

      return null;
   }

   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      InputVSAssemblyInfo cinfo = (InputVSAssemblyInfo) info;

      if(!Tool.equals(autoRefresh, cinfo.autoRefresh)) {
         autoRefresh.setDValue(cinfo.autoRefresh.getDValue());
         result = true;
      }

      return result;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      InputVSAssemblyInfo iinfo = (InputVSAssemblyInfo) info;

      if(!Tool.equals(table, iinfo.table)) {
         table = iinfo.table;
         fireBindingEvent();
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(dtype, iinfo.dtype)) {
         dtype = iinfo.dtype;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(columnValue, iinfo.columnValue)) {
         columnValue.setDValue(iinfo.columnValue.getDValue());
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(rowValue, iinfo.rowValue)) {
         rowValue.setDValue(iinfo.rowValue.getDValue());
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(variable, iinfo.variable)) {
         variable = iinfo.variable;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(submitOnChange, iinfo.submitOnChange)) {
         submitOnChange.setDValue(iinfo.submitOnChange.getDValue());
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   @Override
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      super.update(vs, columns);
      String tname = getTableName();

      if(tname == null || tname.length() == 0) {
         return;
      }

      if(tname.startsWith("$(") && tname.endsWith(")")) {
         tname = tname.substring(2, tname.length() - 1);
      }

      Worksheet ws = vs.getBaseWorksheet();
      Object obj = ws == null ? null : ws.getAssembly(tname);

      if(ws != null && obj == null && tname.endsWith("_O")) {
         tname = tname.substring(0, tname.length() -2);
         obj = ws.getAssembly(tname);
      }

      if(obj instanceof VariableAssembly) {
         variable = true;
         return;
      }

      ArrayList<UserVariable> variableList = new ArrayList<>();

      if(ws != null) {
         Viewsheet.mergeVariables(variableList, ws.getAllVariables());
      }

      if(vs != null) {
         Viewsheet.mergeVariables(variableList, vs.getAllVariables());
      }

      for(UserVariable var : variableList) {
         if(var != null && tname.equals(var.getName())) {
            variable = true;
            return;
         }
      }

      variable = false;

      if(obj == null) {
         throw new RuntimeException(Catalog.getCatalog().
            getString("assembly can not found", "\"" + tname + "\""));
      }

      if(!(obj instanceof EmbeddedTableAssembly)) {
         throw new RuntimeException(Catalog.getCatalog().
            getString("common.viewsheet.embeddedOnly", getName(), tname));
      }

      EmbeddedTableAssembly assembly = (EmbeddedTableAssembly) obj;
      ColumnSelection cols = assembly.getColumnSelection(false);

      obj = columnValue.getRuntimeValue(true);
      String ctext = obj == null ? null : obj.toString();
      column = cols.getAttribute(ctext);

      // invalid column
      if(column == null) {
         throw new ColumnNotFoundException(Catalog.getCatalog().
            getString("common.viewsheet.columnNotFound", getName(), ctext));
      }

      XEmbeddedTable table = assembly.getEmbeddedData();
      int row = getRow();

      // invalid row
      if(row < 0 || row > table.getRowCount()) {
         throw new RuntimeException(Catalog.getCatalog().
            getString("common.viewsheet.rowInvalid", getName()) + row);
      }

      int col = AssetUtil.findColumn(table, column);

      if(col < 0) {
         throw new RuntimeException(Catalog.getCatalog().
            getString("common.viewsheet.colInvalid", getName(), ctext));
      }

      // fetch data type and default data
      dtype = table.getDataType(col);
   }

   /**
    * Get whether the assembly is variable input.
    * @return true if the assembly is variable input.
    */
   public boolean isVariable() {
      return variable;
   }

   /**
    * Set the assembly is variable input.
    * @param variable the specified type of the assembly.
    */
   public void setVariable(boolean variable) {
      this.variable = variable;
   }

   /**
    * Get the text label corresponding to the selected object.
    */
   public abstract String getSelectedLabel();

   /**
    * Get the text labels corresponding to the selected objects.
    */
   public abstract String[] getSelectedLabels();

   /**
    * Get the selected object of this assembly, to be overriden by
    * single input assemblies.
    */
   public abstract Object getSelectedObject();

   /**
    * Set the selected object.
    */
   public abstract int setSelectedObject(Object val);

   /**
    * Get the selected objects of this assembly, to be overriden by
    * composite input assemblies.
    */
   public abstract Object[] getSelectedObjects();

   /**
    * Set the selected objects.
    */
   public abstract int setSelectedObjects(Object[] val);

   /**
    * Check whether submit on change.
    * @return true if submit on change, otherwise false.
    */
   public boolean isSubmitOnChange() {
      return Boolean.valueOf(submitOnChange.getRuntimeValue(true) + "");
   }

   /**
    * Set the submit on change.
    * @param submit true if submit on change, otherwise false.
    */
   public void setSubmitOnChange(boolean submit) {
      submitOnChange.setRValue(submit);
   }

   /**
    * Get whether submit on change.
    * @return true if submit on change, otherwise false.
    */
   public String getSubmitOnChangeValue() {
      return submitOnChange.getDValue();
   }

   /**
    * Set the submit on change.
    * @param submit true if submit on change, otherwise false.
    */
   public void setSubmitOnChangeValue(String submit) {
      submitOnChange.setDValue(submit);
   }

   /**
    * Check whether support to write the change to the data input table.
    */
   public boolean isWriteBack() {
      return Boolean.valueOf(writeBackValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether support to write the change to the data input table.
    */
   public void setWriteBack(boolean writeBack) {
      writeBackValue.setRValue(writeBack);
   }

   /**
    * Check whether support to write the change to the data input table.
    */
   public boolean getWriteBackValue() {
      return writeBackValue.getBooleanValue(true, false);
   }

   /**
    * Set whether support to write the change to the data input table.
    */
   public void setWriteBackValue(boolean writeBack) {
      writeBackValue.setDValue(writeBack + "");
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(scaleRatio.x, 1);
   }

   public Object getPersistentData(String type, String val) {
      return strictNull ? Tool.getPersistentData(type, val) : Tool.getData(type, val);
   }

   /**
    * Set if should use strict rule to parse the data, this is used to handle bc issue.
    */
   public void setStrictNull(boolean strictNull) {
      this.strictNull = strictNull;
   }

   /**
    * Check if should use strict rule to parse the data, this is used to handle bc issue.
    */
   public boolean isStrictNull() {
      return strictNull;
   }

   // input data
   private String table;
   private DynamicValue columnValue;
   private DynamicValue rowValue;
   private String dtype;
   private DynamicValue autoRefresh = new DynamicValue(
      SreeEnv.getProperty("input.autoRefresh.defaultValue", "true"), XSchema.BOOLEAN);
   // runtime
   private DataRef column;
   private boolean variable = false;
   private boolean strictNull = true; // for bc
   private DynamicValue submitOnChange;
   private DynamicValue2 writeBackValue;

   private static final Logger LOG =
      LoggerFactory.getLogger(InputVSAssemblyInfo.class);
}
