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

import inetsoft.graph.GraphConstants;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.List;

/**
 * ComboBoxVSAssemblyInfo stores basic combobox assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ComboBoxVSAssemblyInfo extends ListInputVSAssemblyInfo {
   /**
    * Constructor.
    */
   public ComboBoxVSAssemblyInfo() {
      super();
      setPixelSize(new Dimension(AssetUtil.defw, AssetUtil.defh));
      rowCountValue = new DynamicValue2("5", XSchema.INTEGER);
   }

   /**
    * Get the text label corresponding to the selected object.
    */
   @Override
   public String getSelectedLabel() {
      if(isCalendar() && XSchema.isDateType(getDataType())) {
         Object obj = getSelectedObject();
         return obj == null ? null : XUtil.format(null, obj);
      }

      return super.getSelectedLabel();
   }

   /**
    * Get the selected object of this assembly info.
    * @return the selected object of this assembly info.
    */
   @Override
   public Object getSelectedObject() {
      return selectedObject == null ? autoSelectValue : selectedObject;
   }

   /**
    * Get the values of this assembly info.
    * @return the values of this assembly info.
    */
   @Override
   public Object[] getValues() {
      if(isCalendar() && XSchema.isDateType(getDataType())) {
         return new Object[] {getSelectedObject()};
      }

      return super.getValues();
   }

   /**
    * Get the labels of this assembly info.
    * @return the labels of this assembly info.
    */
   @Override
   public String[] getLabels() {
      if(isCalendar() && XSchema.isDateType(getDataType())) {
         return new String[] {getSelectedLabel()};
      }

      return super.getLabels();
   }

   /**
    * Set the values of this assembly info.
    * @param values the values of this assembly info.
    */
   @Override
   public void setValues(Object[] values) {
      super.setValues(values);
      validate();
   }

   /**
    * Validate the selected object.
    */
   @Override
   public void validate() {
      if(textEditable) {
         return;
      }

      //If the type of the value changes, selectObject should be null
      if(selectedObject != null && !Tool.equals(Tool.getDataType(selectedObject), getDataType())) {
         selectedObject = null;
      }

      if(isCalendar() && XSchema.isDateType(getDataType()) && selectedObject == null) {
         autoSelectValue = Tool.getPersistentDataString(defaultValue,getDataType());
         autoSelectValue = autoSelectValue == null ? null : Tool.getData(getDataType(), autoSelectValue);
         return;
      }

      Object[] values = getValues();

      // if value from variable don't clear
      if(isVariable() && values.length == 0) {
         return;
      }

      for(int i = 0; i < values.length; i++) {
         if(values[i] == null) {
            values[i] = "";
         }
      }

      boolean contained = false;
      boolean containedDefault =false;

      for(int i = 0; i < values.length; i++) {
         if(isSelectedObjectEqual(selectedObject, values[i])) {
            selectedObject = values[i];
            contained = true;
            break;
         }

         if(isSelectedObjectEqual(defaultValue, values[i])) {
            containedDefault = true;
         }
      }

      if(!contained && (getListData() != null && getListData().isBinding() || values.length > 0)) {
         if(containedDefault) {
            autoSelectValue = Tool.getPersistentDataString(defaultValue,getDataType());
         }
         else if(values.length > 0) {
            selectedObject = null;
            autoSelectValue = values[0];
         }
      }
   }

   /**
    * Set the selected object of this assembly info.
    * @param obj the selected object of this assembly info.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObject(Object obj) {
      if(Tool.equals(obj, this.selectedObject)) {
         return VSAssembly.NONE_CHANGED;
      }

      if(isSelectedObjectEqual(obj, defaultValue)) {
         this.selectedObject = null;
         return VSAssembly.NONE_CHANGED;
      }

      this.selectedObject = obj;

      return VSAssembly.OUTPUT_DATA_CHANGED;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" textEditable=\"" + textEditable + "\"");
      writer.print(" calendar=\"" + calendar + "\"");
      writer.print(" serverTZ=\"" + serverTZ + "\"");
      writer.print(" rowCount=\"" + getRowCount() + "\"");
      writer.print(" rowCountValue=\"" + getRowCountValue() + "\"");

      if(getDefaultValue() == null) {
         writer.print(" defaultValue=\"" + Tool.NULL_PARAMETER_VALUE + "\"");
      }
      else {
         writer.print(" defaultValue=\"" + getDefaultValue() + "\"");
      }

   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      textEditable = "true".equals(Tool.getAttribute(elem, "textEditable"));
      calendar = "true".equals(Tool.getAttribute(elem, "calendar"));
      serverTZ = "true".equals(Tool.getAttribute(elem, "serverTZ"));
      String text = getAttributeStr(elem, "rowCount", "5");
      setRowCountValue(Integer.parseInt(text));
      defaultValue = getAttributeStr(elem, "defaultValue", "");

      if(defaultValue.equals(Tool.NULL_PARAMETER_VALUE)) {
         defaultValue = null;
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.print("<selectedObject>");
      writer.print("<![CDATA[" + Tool.getPersistentDataString(selectedObject,
         getDataType()) + "]]>");
      writer.print("</selectedObject>");

      if(minDate.getDValue() != null) {
         writer.print("<minDate>");
         writer.print("<![CDATA[" + minDate.getDValue() + "]]>");
         writer.println("</minDate>");
      }

      if(maxDate.getDValue() != null) {
         writer.print("<maxDate>");
         writer.print("<![CDATA[" + maxDate.getDValue() + "]]>");
         writer.println("</maxDate>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element snode = Tool.getChildNodeByTagName(elem, "selectedObject");
      selectedObject = getPersistentData(getDataType(), Tool.getValue(snode));

      minDate.setDValue(getContentsStr(elem, "minDate", ""));
      maxDate.setDValue(getContentsStr(elem, "maxDate", ""));
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public ComboBoxVSAssemblyInfo clone(boolean shallow) {
      try {
         ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo) super.clone(shallow);

         if(rowCountValue != null) {
            info.rowCountValue = (DynamicValue2) rowCountValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ComboBoxVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      ComboBoxVSAssemblyInfo cinfo = (ComboBoxVSAssemblyInfo) info;

      if(textEditable != cinfo.textEditable) {
         this.textEditable = cinfo.textEditable;
         result = true;
      }

      if(calendar != cinfo.calendar) {
         this.calendar = cinfo.calendar;
         result = true;
      }

      if(!Tool.equals(minDate, cinfo.minDate)) {
         this.minDate = cinfo.minDate;
         result = true;
      }

      if(!Tool.equals(maxDate, cinfo.maxDate)) {
         this.maxDate = cinfo.maxDate;
         result = true;
      }

      if(serverTZ != cinfo.serverTZ) {
         this.serverTZ = cinfo.serverTZ;
         result = true;
      }

      if(!Tool.equals(rowCountValue, cinfo.rowCountValue) ||
         !Tool.equals(getRowCount(), cinfo.getRowCount()))
      {
         rowCountValue = cinfo.rowCountValue;
         result = true;
      }

      return result;
   }

   /**
    * Copy the output data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyOutputDataInfo(VSAssemblyInfo info) {
      boolean result = super.copyOutputDataInfo(info);
      ComboBoxVSAssemblyInfo cinfo = (ComboBoxVSAssemblyInfo) info;

      if(!Tool.equals(selectedObject, cinfo.selectedObject)) {
         selectedObject = cinfo.selectedObject;
         result = true;
      }

      return result;
   }

   /**
    * Set the default vsobject format.
    * @param border border color.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      VSCompositeFormat format = new VSCompositeFormat();
      // avoid text being clipped in default size
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.PLAIN, 11));
      format.getDefaultFormat().setForegroundValue("0x2b2b2b");
      format.getDefaultFormat().setBackgroundValue("0xffffff");
      format.getDefaultFormat().setBordersValue(
         new Insets(GraphConstants.THIN_LINE, GraphConstants.THIN_LINE,
                    GraphConstants.THIN_LINE, GraphConstants.THIN_LINE));
      format.getDefaultFormat().setBorderColorsValue(
         new BorderColors(new Color(0xc0c0c0), new Color(0xc0c0c0),
                          new Color(0xc0c0c0), new Color(0xc0c0c0)));

      format.getCSSFormat().setCSSType(getObjCSSType());
      setFormat(format);
      setCSSDefaults();
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.COMBOBOX;
   }

   /**
    * Set the combobox is editable.
    */
   public void setTextEditable(boolean editable) {
      this.textEditable = editable;
   }

   /**
    * Check whether combobox is editable.
    */
   public boolean isTextEditable() {
      return textEditable;
   }

   /**
    * Set whether to use a calendar to select date value.
    */
   public void setCalendar(boolean calendar) {
      this.calendar = calendar;
   }

   /**
    * Check whether to use a calendar to select date value.
    */
   public boolean isCalendar() {
      return calendar;
   }

   public String getDefaultValue() {
      return defaultValue;
   }

   public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
   }

   /**
    * Set whether to use a server timezone on GUI.
    */
   public void setServerTimeZone(boolean serverTZ) {
      this.serverTZ = serverTZ;
   }

   /**
    * Check whether to use a server timezone on GUI.
    */
   public boolean isServerTimeZone() {
      return serverTZ;
   }

   /**
    * Set the minimum date for calendar combo boxes
    */
   public void setMinDate(Timestamp minDate) {
      this.minDate.setRValue(minDate);
   }

   /**
    * Get the minimum date for calendar combo boxes
    */
   public Timestamp getMinDate() {
      return (Timestamp) this.minDate.getRuntimeValue(true);
   }

   /**
    * Set the maximum date for calendar combo boxes
    */
   public void setMaxDate(Timestamp maxDate) {
      this.maxDate.setRValue(maxDate);
   }

   /**
    * Get the maximum date for calendar combo boxes
    */
   public Timestamp getMaxDate() {
      return (Timestamp) this.maxDate.getRuntimeValue(true);
   }

   /**
    * Set the minimum date value for calendar combo boxes
    */
   public void setMinDateValue(String minDate) {
      this.minDate.setDValue(minDate);
   }

   /**
    * Get the minimum date value for calendar combo boxes
    */
   public String getMinDateValue() {
      return this.minDate.getDValue();
   }

   /**
    * Set the maximum date value for calendar combo boxes
    */
   public void setMaxDateValue(String maxDate) {
      this.maxDate.setDValue(maxDate);
   }

   /**
    * Get the maximum date value for calendar combo boxes
    */
   public String getMaxDateValue() {
      return this.maxDate.getDValue();
   }

   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);

      list.add(minDate);
      list.add(maxDate);

      return list;
   }

   /**
    * Set the run time count of row to show.
    */
   public void setRowCount(int rowCount) {
      if((rowCount > 0) && (rowCount < Integer.MAX_VALUE)) {
         rowCountValue.setRValue(rowCount);
      }
   }

   /**
    * Get the run time count of row to show.
    */
   public int getRowCount() {
      return rowCountValue.getIntValue(false, 5);
   }

   /**
    * Set the design time count of row to show.
    */
   public void setRowCountValue(int rowCount) {
      rowCountValue.setDValue(rowCount + "");
   }

   /**
    * Get the design time count of row to show.
    */
   public int getRowCountValue() {
      return rowCountValue.getIntValue(true, 5);
   }

   /**
    * Clear the selected objects.
    */
   @Override
   public void clearSelectedObjects() {
      selectedObject = null;
   }

   /**
    * Date combo has not list values.
    */
   @Override
   public ListData getListData() {
      if(isCalendar() && XSchema.isDateType(getDataType())) {
         return new ListData();
      }

      return super.getListData();
   }

   private DynamicValue2 rowCountValue; //the number of rows to show
   // output data
   private Object selectedObject;
   private boolean textEditable;
   private boolean calendar = false;
   private boolean serverTZ = false;
   private String defaultValue;
   private Object autoSelectValue;
   private DynamicValue maxDate = new DynamicValue(null, XSchema.TIME_INSTANT);
   private DynamicValue minDate = new DynamicValue(null, XSchema.TIME_INSTANT);
   public static final int TIME_INSTANT_DEFAULT_WIDTH = 180;
   public static final int TIME_DEFAULT_WIDTH = 125;

   private static final Logger LOG =
      LoggerFactory.getLogger(ComboBoxVSAssemblyInfo.class);
}
