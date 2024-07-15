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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue2;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;

/**
 * TableVSAssemblyInfo, the assembly info of a table assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TableVSAssemblyInfo extends TableDataVSAssemblyInfo {
   /**
    * Constructor.
    */
   public TableVSAssemblyInfo() {
      super();

      columns = new ColumnSelection();
      hcolumns = new ColumnSelection();
      setPixelSize(new Dimension(400, 240));
      formValue = new DynamicValue2("false", XSchema.BOOLEAN);
      insertValue = new DynamicValue2("false", XSchema.BOOLEAN);
      delValue = new DynamicValue2("false", XSchema.BOOLEAN);
      editValue = new DynamicValue2("false", XSchema.BOOLEAN);
      writeBackValue = new DynamicValue2("false", XSchema.BOOLEAN);

      try {
         maxrow = Util.getTableOutputMaxrow(100000);
      }
      catch(Exception ex) {
         // ignore
      }
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true, true);
      // @by billh, do not set default background, otherwise there is no chance
      // to apply table style
      // getFormat().getDefaultFormat().setBackgroundValue("0xffffff");
      // getFormat().getDefaultFormat().setAlpha(60);
   }

   @Override
   public void clearBinding() {
      super.clearBinding();
      columns = new ColumnSelection();
      hcolumns = new ColumnSelection();
   }

   /**
    * Get the column selection of the table.
    * @return the column selection of the table.
    */
   public ColumnSelection getColumnSelection() {
      return columns;
   }

   /**
    * Set the column selection of the table.
    * @param columns the specified column selection of the table.
    */
   public void setColumnSelection(ColumnSelection columns) {
      this.columns = columns == null ? new ColumnSelection() : columns;
   }

   /**
    * Get the hidden column selection of the table.
    * @return the column selection of the table.
    */
   public ColumnSelection getHiddenColumns() {
      return hcolumns;
   }

   /**
    * Set the column selection of the table.
    * @param hcolumns the specified column selection of the table.
    */
   public void setHiddenColumns(ColumnSelection hcolumns) {
      this.hcolumns = hcolumns == null ? new ColumnSelection() : hcolumns;
   }

   /**
    * Update header row heights.
    */
   public void updateHeaderRowHeights(boolean isWrappedHeader) {
      // No need to update
   }

   /**
    * Check if it is a summary table.
    * @return <tt>true</tt> if it is summary table, <tt>false</tt> otherwise.
    */
   public boolean isSummaryTable() {
      return summary;
   }

   /**
    * Set whether it is a summary table.
    * @param summary <tt>true</tt> if it is a summary table,
    * <tt>false</tt> otherwise.
    */
   public void setSummaryTable(boolean summary) {
      this.summary = summary;
   }

   /**
    * Set is form table.
    * @param form true to be editable.
    */
   public void setForm(boolean form) {
      formValue.setRValue(form);
   }

   /**
    * Check if the table is form table.
    * @return <tt>true</tt> if is form table, <tt>false</tt> otherwise.
    */
   public boolean isForm(){
      return Boolean.valueOf(formValue.getRuntimeValue(true) + "");
   }

   /**
    * If this option is true, the table is form table.
    * @param form true to be form table.
    */
   public void setFormValue(boolean form){
      this.formValue.setDValue(form + "");
   }

   /**
    * Check if the table is form table.
    * @return <tt>true</tt> if is form table, <tt>false</tt> otherwise.
    */
   public boolean getFormValue() {
      return formValue.getBooleanValue(true, false);
   }

   /**
    * Set insert.
    */
   public void setInsert(boolean insert) {
      insertValue.setRValue(insert);
   }

   /**
    * Get insert.
    */
   public boolean isInsert(){
      return Boolean.valueOf(insertValue.getRuntimeValue(true) + "");
   }

   /**
    * Set insert.
    */
   public void setInsertValue(boolean insert){
      this.insertValue.setDValue(insert + "");
   }

   /**
    * Get insert.
    */
   public boolean getInsertValue() {
      return insertValue.getBooleanValue(true, false);
   }

   /**
    * Set del.
    */
   public void setDel(boolean del) {
      delValue.setRValue(del);
   }

   /**
    * Get del.
    */
   public boolean isDel(){
      return Boolean.valueOf(delValue.getRuntimeValue(true) + "");
   }

   /**
    * Set insert.
    */
   public void setDelValue(boolean del){
      this.delValue.setDValue(del + "");
   }

   /**
    * Get insert.
    */
   public boolean getDelValue() {
      return delValue.getBooleanValue(true, false);
   }

   /**
    * Set edit.
    */
   public void setEdit(boolean edit) {
      editValue.setRValue(edit);
   }

   /**
    * Get edit.
    */
   public boolean isEdit(){
      return Boolean.valueOf(editValue.getRuntimeValue(true) + "");
   }

   /**
    * Set edit.
    */
   public void setEditValue(boolean edit){
      this.editValue.setDValue(edit + "");
   }

   /**
    * Get edit.
    */
   public boolean getEditValue() {
      return editValue.getBooleanValue(true, false);
   }

   /**
    * Set edit.
    */
   public void setWriteBack(boolean writeBackOpt) {
      writeBackValue.setRValue(writeBackOpt);
   }

   /**
    * Get edit.
    */
   public boolean isWriteBack(){
      return Boolean.valueOf(writeBackValue.getRuntimeValue(true) + "");
   }

   /**
    * Set edit.
    */
   public void setWriteBackValue(boolean writeBackOpt){
      this.writeBackValue.setDValue(writeBackOpt + "");
   }

   /**
    * Get edit.
    */
   public boolean getWriteBackValue() {
      return writeBackValue.getBooleanValue(true, false);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" summary=\"" + summary + "\"");
      writer.print(" embedded=\"" + embedded + "\"");
      writer.print(" form=\"" + isForm() + "\"");
      writer.print(" formValue=\"" + getFormValue() + "\"");
      writer.print(" insert=\"" + isInsert() + "\"");
      writer.print(" insertValue=\"" + getInsertValue() + "\"");
      writer.print(" del=\"" + isDel() + "\"");
      writer.print(" delValue=\"" + getDelValue() + "\"");
      writer.print(" edit=\"" + isEdit() + "\"");
      writer.print(" editValue=\"" + getEditValue() + "\"");
      writer.print(" writeBack=\"" + isWriteBack() + "\"");
      writer.print(" writeBackValue=\"" + getWriteBackValue() + "\"");
      writer.print(" maxRow=\"" + maxrow + "\"");

      if(preV113) {
         writer.print(" preV113=\"true\"");
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      summary = "true".equals(Tool.getAttribute(elem, "summary"));
      preV113 = "true".equals(Tool.getAttribute(elem, "preV113"));
      embedded = "true".equals(Tool.getAttribute(elem, "embedded"));

      String prop;

      if((prop = getAttributeStr(elem, "form", "false")) != null) {
         setFormValue(prop.equalsIgnoreCase("true"));
      }

      if((prop = getAttributeStr(elem, "insert", "false")) != null) {
         setInsertValue(prop.equalsIgnoreCase("true"));
      }

      if((prop = getAttributeStr(elem, "del", "false")) != null) {
         setDelValue(prop.equalsIgnoreCase("true"));
      }

      if((prop = getAttributeStr(elem, "edit", "false")) != null) {
         setEditValue(prop.equalsIgnoreCase("true"));
      }

      if((prop = getAttributeStr(elem, "writeBack", "false")) != null) {
         setWriteBackValue(prop.equalsIgnoreCase("true"));
      }

      if((prop = getAttributeStr(elem, "maxRow", "0")) != null) {
         maxrow = Integer.parseInt(prop);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(columns != null) {
         columns.writeXML(writer);
      }

      if(hcolumns != null) {
         hcolumns.writeXML(writer);
      }

      if(rowHyperlink != null) {
         rowHyperlink.writeXML(writer);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      NodeList nodes = Tool.getChildNodesByTagName(elem, "ColumnSelection");

      Element anode = (Element) nodes.item(0);

      if(anode != null) {
         columns = new ColumnSelection();
         columns.parseXML(anode);
      }

      anode = nodes.getLength() == 2 ? (Element) nodes.item(1) : null;

      if(anode != null) {
         hcolumns = new ColumnSelection();
         hcolumns.parseXML(anode);
      }

      Element node = Tool.getChildNodeByTagName(elem, "Hyperlink");

      if(node != null) {
         rowHyperlink = new Hyperlink();
         rowHyperlink.parseXML(node);
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public TableVSAssemblyInfo clone(boolean shallow) {
      try {
         TableVSAssemblyInfo info = (TableVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            info.columns = (ColumnSelection) columns.clone();
            info.hcolumns = (ColumnSelection) hcolumns.clone();
         }

         if(formValue != null) {
            info.formValue = (DynamicValue2) formValue.clone();
         }

         if(insertValue != null) {
            info.insertValue = (DynamicValue2) insertValue.clone();
         }

         if(delValue != null) {
            info.delValue = (DynamicValue2) delValue.clone();
         }

         if(editValue != null) {
            info.editValue = (DynamicValue2) editValue.clone();
         }

         if(writeBackValue != null) {
            info.writeBackValue = (DynamicValue2) writeBackValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TableVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, data or worksheet data.
    */
   @Override
   public int copyInfo(VSAssemblyInfo info) {
      TableHighlightAttr ohighlight = getHighlightAttr();

      if(ohighlight != null) {
         ohighlight = ohighlight.clone();
      }

      int hint = super.copyInfo(info);

      if(!Tool.equals(ohighlight, getHighlightAttr())) {
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      return hint;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) info;

      if(columns == null || !columns.equals(tinfo.columns, true) ||
         !tinfo.columns.equals(columns, true))
      {
         columns = tinfo.columns;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(hcolumns == null || !hcolumns.equals(tinfo.hcolumns, true) ||
         !tinfo.hcolumns.equals(hcolumns, true))
      {
         hcolumns = tinfo.hcolumns;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(summary != tinfo.summary) {
         summary = tinfo.summary;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(formValue , tinfo.formValue) ||
         isForm() !=  tinfo.isForm())
      {
         formValue = tinfo.formValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(insertValue , tinfo.insertValue) ||
         isInsert() !=  tinfo.isInsert())
      {
         insertValue = tinfo.insertValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(delValue , tinfo.delValue) ||
         isDel() !=  tinfo.isDel())
      {
         delValue = tinfo.delValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(editValue , tinfo.editValue) ||
         isEdit() !=  tinfo.isEdit())
      {
         editValue = tinfo.editValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(writeBackValue , tinfo.writeBackValue) ||
         isWriteBack() !=  tinfo.isWriteBack())
      {
         writeBackValue = tinfo.writeBackValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }


      if(maxrow != tinfo.maxrow) {
         maxrow = tinfo.maxrow;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(rowHyperlink, tinfo.rowHyperlink)) {
         rowHyperlink = tinfo.rowHyperlink;
      }

      return hint;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      formValue.setRValue(null);
      insertValue.setRValue(null);
      delValue.setRValue(null);
      editValue.setRValue(null);
      writeBackValue.setRValue(null);
   }

   /**
    * Check if the dynamic value of the format should be processed.
    */
   @Override
   protected boolean isProcessFormat(TableDataPath path) {
      return path.getType() != TableDataPath.DETAIL &&
         super.isProcessFormat(path);
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.TABLE;
   }

   /**
    * Check if the table is previous of 11.3, because from 11.3, table data
    * view's table data path changed.
    */
   public boolean isPreV113() {
      return preV113;
   }

   /**
    * Clear the version status.
    */
   public void clearVersion() {
      preV113 = false;
   }

   /**
    * Check if the table is an embedded table or not.
    */
   public boolean isEmbeddedTable() {
      return embedded;
   }

   /**
    * Set whether the table it is an embedded table.
    */
   public void setEmbeddedTable(boolean embedded) {
      this.embedded = embedded;
   }

   /**
    * Get the maximum rows.
    */
   public int getMaxRows() {
      // Feature #39140, always respect the global row limit
      return Util.getQueryLocalRuntimeMaxrow(maxrow);
   }

   /**
    * Set the maximum rows.
    */
   public void setMaxRows(int row) {
      this.maxrow = row;
   }

   /**
    * Get hyperlink for each row of the table.
    */
   @Override
   public Hyperlink getRowHyperlink() {
      return rowHyperlink;
   }

   /**
    * Set hyperlink for each row of the table.
    */
   @Override
   public void setRowHyperlink(Hyperlink rowHyperlink) {
      this.rowHyperlink = rowHyperlink;
   }

   /**
    * Get column count.
    */
   @Override
   public int getColumnCount() {
      return columns.getAttributeCount();
   }

   /**
    * Get visible column selection.
    */
   public ColumnSelection getVisibleColumns() {
      ColumnSelection columnselection = (ColumnSelection) columns.clone();
      ColumnSelection selection = new ColumnSelection();

      for(int i = 0; i < columnselection.getAttributeCount(); i++) {
         DataRef ref = columnselection.getAttribute(i);

         if(ref instanceof ColumnRef && ((ColumnRef) ref).isVisible()) {
            selection.addAttribute(ref);
         }
      }

      return selection;
   }

   private ColumnSelection columns;
   private ColumnSelection hcolumns;
   private boolean summary;
   private boolean preV113 = false;
   private boolean embedded = false;
   private DynamicValue2 formValue;
   private DynamicValue2 insertValue;
   private DynamicValue2 delValue;
   private DynamicValue2 editValue;
   private DynamicValue2 writeBackValue;
   private int maxrow; // max rows
   private Hyperlink rowHyperlink; // for each row of the table

   private static final Logger LOG =
      LoggerFactory.getLogger(TableVSAssemblyInfo.class);
}
