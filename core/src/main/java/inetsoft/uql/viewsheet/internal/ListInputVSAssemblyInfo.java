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

import inetsoft.report.TableDataPath;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.BitSet;

/**
 * ListInputVSAssemblyInfo, the assembly info of a list input assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class ListInputVSAssemblyInfo extends InputVSAssemblyInfo
   implements ListBindableVSAssemblyInfo, ListValueInfo
{
   /**
    * Constructor.
    */
   public ListInputVSAssemblyInfo() {
      super();
      this.stype = ListInputVSAssembly.NONE_SOURCE;

      values = new String[0];
      labels = new String[0];
   }

   /**
    * Get the list data.
    * @return the list data of this assembly info.
    */
   @Override
   public ListData getListData() {
      return data;
   }

   /**
    * Set the list data to this assembly info.
    * @param data the specified list data.
    */
   @Override
   public void setListData(ListData data) {
      this.data = data;
   }

   /**
    * Get the runtime list data.
    * @return the list data of this assembly info.
    */
   public ListData getRListData() {
      return rdata;
   }

   /**
    * Set the runtime list data to this assembly info.
    * @param rdata the runtime list data.
    */
   public void setRListData(ListData rdata) {
      this.rdata = rdata;
   }

   /**
    * Get the source type.
    * @return the source type of this assembly info.
    */
   @Override
   public int getSourceType() {
      return stype;
   }

   /**
    * Set the source type to this assembly info.
    * @param stype the specified source type.
    */
   @Override
   public void setSourceType(int stype) {
      this.stype = stype;
   }

   /**
    * Check if is sort by value
    */
   public boolean isSortByValue() {
      return sortByValue;
   }

   /**
    * Set sort by value
    */
   public void setSortByValue(boolean sortByValue) {
      this.sortByValue = sortByValue;
   }

   /**
    * Set the run time sort type.
    * @param type the sort type, one of the following: XConstants.SORT_ASC,
    * XConstants.SORT_DESC, XConstants.SORT_NONE.
    */
   public void setSortType(int type) {
      sortTypeValue.setRValue(type);
   }

   /**
    * Get the run time sort type.
    * @return the sort type of this assembly info.
    */
   public int getSortType() {
      return sortTypeValue.getIntValue(false, XConstants.SORT_ASC);
   }

   /**
    * Get the design time sort type.
    * @return the sort type of this assembly info.
    */
   public int getSortTypeValue() {
      return sortTypeValue.getIntValue(true, XConstants.SORT_ASC);
   }

   /**
    * Set the design time sort type.
    * @param type the sort type, one of the following: XConstants.SORT_ASC,
    * XConstants.SORT_DESC, XConstants.SORT_NONE.
    */
   public void setSortTypeValue(int type) {
      sortTypeValue.setDValue(type + "");
   }

   /**
    * Set the checkbox/radiobutton height.
    */
   public void setCellHeight(int h) {
      this.cellHeight = h;
   }

   /**
    * Get the checkbox/radiobutton height.
    */
   public int getCellHeight() {
      return this.cellHeight;
   }

   /**
    * Get the run time embedded data top or bottom.
    */
   public boolean isEmbeddedDataDown() {
      return edataDownValue.getBooleanValue(false, false);
   }

   /**
    * Set the run time embedded data top or bottom.
    */
   public void setEmbeddedDataDown(boolean edataDown) {
      edataDownValue.setRValue(edataDown);
   }

   /**
    * Get the design time embedded data top or bottom.
    */
   public boolean isEmbeddedDataDownValue() {
      return edataDownValue.getBooleanValue(true, false);
   }

   /**
    * Set the design time embedded data top or bottom.
    */
   public void setEmbeddedDataDownValue(boolean edataDown) {
      edataDownValue.setDValue(edataDown + "");
   }

   public void setSelectFirstItem(boolean value) {
      selectFirstItem.setRValue(value);
   }

   public boolean isSelectFirstItem() {
      return selectFirstItem.getBooleanValue(false, false);
   }

   public void setSelectFirstItemValue(boolean value) {
      selectFirstItem.setDValue(value + "");
   }

   public boolean getSelectFirstItemValue() {
      return selectFirstItem.getBooleanValue(true, false);
   }

   public String getDefaultValue() {
      return defaultValue;
   }

   public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
   }

   /**
    * Get the binding info.
    * @return the binding info of this assembly info.
    */
   @Override
   public BindingInfo getBindingInfo() {
      return binding;
   }

   /**
    * Get the list binding info.
    * @return the list binding info of this assembly info.
    */
   @Override
   public ListBindingInfo getListBindingInfo() {
      return binding;
   }

   /**
    * Set the list binding info to this assembly info.
    * @param binding the specified list binding info.
    */
   @Override
   public void setListBindingInfo(ListBindingInfo binding) {
      this.binding = binding;
   }

   /**
    * Get the values of this assembly info.
    * @return the values of this assembly info.
    */
   public Object[] getValues() {
      return values;
   }

   /**
    * Set the values of this assembly info.
    * @param values the values of this assembly info.
    */
   public void setValues(Object[] values) {
      this.values = values == null ? new Object[0] : values;

      for(int i = 0; i < this.values.length; i++) {
         this.values[i] = Tool.getData(getDataType(), values[i]);

         if(getDataType().equals("boolean") && this.values[i] == null){
            this.values[i] = "";
         }
      }
   }

   /**
    * Get the labels of this assembly info.
    * @return the labels of this assembly info.
    */
   public String[] getLabels() {
      return labels;
   }

   /**
    * Set the labels of this assembly info.
    * @param labels the labels of this assembly info.
    */
   public void setLabels(String[] labels) {
      this.labels = labels == null ? new String[0] : labels;
   }

   /**
    * Get the viewsheet formats.
    * @return the viewsheet formats.
    */
   public VSCompositeFormat[] getFormats() {
      return formats;
   }

   /**
    * Set the viewsheet formats.
    * @param formats the specified viewsheet formats.
    */
   public void setFormats(VSCompositeFormat[] formats) {
      this.formats = formats == null ? new VSCompositeFormat[0] : formats;
   }

   /**
    * Get the data type.
    * @return the data type of this list data.
    */
   @Override
   public String getDataType() {
      ListBindingInfo info = getListBindingInfo();
      String dtype = null;

      if(info != null && !info.isEmpty()) {
         dtype = info.getDataType();
      }

      // use this.data directly without getListData(), otherwise there
      // is an infinite recursion
      //ListData data = getListData();

      if(dtype == null && data != null && !data.isEmpty()) {
         dtype = data.getDataType();
      }

      if(dtype == null) {
         dtype = super.getDataType();
      }

      dtype = dtype == null ? XSchema.STRING : dtype;

      return dtype;
   }

   /**
    * Bug 19601 need to get embedded data type explicitly
    * Get the embedded data type.
    * @return the data type of this list data.
    */
   public String getEmbeddedDataType() {
      String dtype = XSchema.STRING;

      // use this.data directly without getListData(), otherwise there
      // is an infinite recursion
      //ListData data = getListData();

      if(XSchema.STRING.equals(dtype) && data != null && !data.isEmpty()) {
         dtype = data.getDataType();
      }

      if(XSchema.STRING.equals(dtype)) {
         dtype = super.getDataType();
      }

      return dtype;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" sourceType=\"" + stype + "\"");
      writer.print(" cellHeight=\"" + cellHeight + "\"");
      writer.print(" form=\"" + form + "\"");
      writer.print(" sortType=\"" + getSortType() + "\"");
      writer.print(" sortTypeValue=\"" + sortTypeValue.getDValue() + "\"");
      writer.print(" edataDown=\"" + isEmbeddedDataDown() + "\"");
      writer.print(" edataDownValue=\"" + edataDownValue.getDValue() + "\"");
      writer.print(" selectFirstItem=\"" + selectFirstItem.getDValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      this.stype = Integer.parseInt(Tool.getAttribute(elem, "sourceType"));
      this.form = "true".equals(Tool.getAttribute(elem, "form"));
      sortTypeValue.setDValue(
         VSUtil.getAttributeStr(elem, "sortType", XConstants.SORT_ASC + ""));
      edataDownValue.setDValue(
         VSUtil.getAttributeStr(elem, "edataDown", "false"));
      selectFirstItem.setDValue(VSUtil.getAttributeStr(elem, "selectFirstItem", "false"));

      String str = Tool.getAttribute(elem, "cellHeight");

      if(str != null) {
         this.cellHeight = Integer.parseInt(str);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(data != null) {
         data.setDataType(getEmbeddedDataType());
         data.writeXML(writer);
      }

      if(binding != null) {
         binding.writeXML(writer);
      }

      if(values != null && values.length > 0) {
         writer.print("<values>");

         for(int i = 0; i < values.length; i++) {
            writer.print("<value>");
            writer.print("<![CDATA[" + Tool.getDataString(values[i],
                         getDataType()) + "]]>");
            writer.print("</value>");
         }

         writer.println("</values>");
      }

      if(labels != null && labels.length > 0) {
         writer.print("<labels>");

         for(int i = 0; i < labels.length; i++) {
            writer.print("<label>");
            writer.print("<![CDATA[" + Tool.localize(labels[i]) + "]]>");
            writer.print("</label>");
         }

         writer.println("</labels>");
      }

      if(formats != null && formats.length > 0) {
         writer.print("<formats>");

         for(int i = 0; i < formats.length; i++) {
            if(formats[i] != null) {
               formats[i].writeXML(writer);
            }
         }

         writer.println("</formats>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element dnode = Tool.getChildNodeByTagName(elem, "listData");

      if(dnode != null) {
         data = new ListData();
         data.parseXML(dnode);
      }

      Element bnode = Tool.getChildNodeByTagName(elem, "bindingInfo");

      if(bnode != null) {
         binding = new ListBindingInfo();
         binding.parseXML(bnode);
      }

      Element valuesNode = Tool.getChildNodeByTagName(elem, "values");

      if(valuesNode != null) {
         NodeList valuesList = Tool.getChildNodesByTagName(valuesNode, "value");

         if(valuesList != null && valuesList.getLength() > 0) {
            values = new Object[valuesList.getLength()];

            for(int i = 0; i < valuesList.getLength(); i++) {
               values[i] = Tool.getData(getDataType(),
                  Tool.getValue(valuesList.item(i)));
            }
         }
      }

      Element labelsNode = Tool.getChildNodeByTagName(elem, "labels");

      if(labelsNode != null) {
         NodeList labelsList = Tool.getChildNodesByTagName(labelsNode, "label");

         if(labelsList != null && labelsList.getLength() > 0) {
            labels = new String[labelsList.getLength()];

            for(int i = 0; i < labelsList.getLength(); i++) {
               labels[i] = Tool.getValue(labelsList.item(i));
            }
         }
      }

      Element formatsNode = Tool.getChildNodeByTagName(elem, "formats");

      if(formatsNode != null) {
         NodeList formatsList =
            Tool.getChildNodesByTagName(formatsNode, "VSCompositeFormat");

         if(formatsList != null && formatsList.getLength() > 0) {
             formats = new VSCompositeFormat[formatsList.getLength()];

            for(int i = 0; i < formatsList.getLength(); i++) {
               formats[i] = new VSCompositeFormat();
               formats[i].parseXML((Element) formatsList.item(i));
            }
         }
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public ListInputVSAssemblyInfo clone(boolean shallow) {
      try {
         ListInputVSAssemblyInfo info = (ListInputVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(binding != null) {
               info.binding = (ListBindingInfo) binding.clone();
            }

            if(data != null) {
               info.data = (ListData) data.clone();
            }

            if(rdata != null) {
               info.rdata = (ListData) rdata.clone();
            }

            info.sortTypeValue = (DynamicValue2) sortTypeValue.clone();
            info.edataDownValue = (DynamicValue2) edataDownValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ListInputVSAssemblyInfo", ex);
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
      int hint = VSAssembly.NONE_CHANGED;

      TableDataPath path1 = new TableDataPath(-1, TableDataPath.OBJECT);
      TableDataPath path2 = new TableDataPath(-1, TableDataPath.DETAIL);

      hint = hint | checkFormatChange(path1, info);
      hint = hint | checkFormatChange(path2, info);

      if(isEmbeddedDataDown()) {
         hint = hint | VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint | super.copyInfo(info);
   }

   /**
    * Check if the format of the labels are changed.
    */
   private int checkFormatChange(TableDataPath path, VSAssemblyInfo info) {
      VSCompositeFormat fmt1 = getFormatInfo().getFormat(path);
      VSCompositeFormat fmt2 = info.getFormatInfo().getFormat(path);

      // need to repopulate labels if format changed
      if(fmt1 == null && fmt2 != null && fmt2.getFormat() != null ||
         fmt2 == null && fmt1 != null && fmt1.getFormat() != null ||
         fmt1 != null && fmt2 != null &&
         (!Tool.equals(fmt1.getFormat(), fmt2.getFormat()) ||
          !Tool.equals(fmt1.getFormatExtent(), fmt2.getFormatExtent())))
      {
         return VSAssembly.INPUT_DATA_CHANGED;
      }

      return 0;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      ListInputVSAssemblyInfo linfo = (ListInputVSAssemblyInfo) info;

      if(stype != linfo.stype) {
         stype = linfo.stype;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(data, linfo.data)) {
         data = linfo.data;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(rdata, linfo.rdata)) {
         rdata = linfo.rdata;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(binding, linfo.binding)) {
         binding = linfo.binding;
         fireBindingEvent();
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(form != linfo.form) {
         form = linfo.form;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(sortTypeValue != linfo.sortTypeValue) {
         sortTypeValue = linfo.sortTypeValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(edataDownValue != linfo.edataDownValue) {
         edataDownValue = linfo.edataDownValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(selectFirstItem != linfo.selectFirstItem) {
         selectFirstItem = linfo.selectFirstItem;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(getDefaultValue(),linfo.getDefaultValue())) {
         setDefaultValue(linfo.getDefaultValue());
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      ListInputVSAssemblyInfo cinfo = (ListInputVSAssemblyInfo) info;

      if(cellHeight != cinfo.cellHeight) {
         cellHeight = cinfo.cellHeight;
         result = true;
      }

      return result;
   }

   /**
    * Check if the dynamic value of the format should be processed.
    */
   @Override
   protected boolean isProcessFormat(TableDataPath path) {
      // the cell format is normally processed on a per cell level, but needs
      // to be processed for design-time update
      return true;
   }

   /**
    * Get the text label corresponding to the selected object.
    */
   @Override
   public String getSelectedLabel() {
      return findLabel(getSelectedObject(), new BitSet(1));
   }

   /**
    * Get the text labels corresponding to the selected objects.
    */
   @Override
   public String[] getSelectedLabels() {
      Object[] objs = getSelectedObjects();
      BitSet indexs = new BitSet(objs.length);

      if(objs != null) {
         String[] labels = new String[objs.length];

         for(int i = 0; i < labels.length; i++) {
            labels[i] = findLabel(objs[i], indexs);
         }

         return labels;
      }

      return new String[0];
   }

   /**
    * Find the label for the object.
    */
   private String findLabel(Object obj, BitSet indexs) {
      if(labels == null || values == null) {
         return null;
      }

      for(int i = 0; i < values.length; i++) {
         if(isSelectedObjectEqual(values[i], obj) && !indexs.get(i)) {
            indexs.set(i);
            return labels[i];
         }
      }

      return null;
   }

   /**
    * Get the selected object of this assembly, to be overriden by
    * single input assemblies.
    */
   @Override
   public Object getSelectedObject() {
      return null;
   }

   /**
    * Get the selected objects of this assembly, to be overriden by
    * composite input assemblies.
    */
   @Override
   public Object[] getSelectedObjects() {
      Object obj = getSelectedObject();
      return (obj == null) ? new Object[0] : new Object[] {obj};
   }

   /**
    * Set the selected objects.
    */
   @Override
   public int setSelectedObjects(Object[] val) {
      return setSelectedObject((val.length == 0) ? null : val[0]);
   }

   /**
    * Check if it is form.
    */
   public boolean isForm() {
      return form;
   }

   /**
    * Set if it is form
    */
   public void setForm(boolean form) {
      this.form = form;
   }

   /**
    * Clear the selected objects.
    */
   public abstract void clearSelectedObjects();

   /**
    * Validate the selected object.
    */
   public abstract void validate();

   protected boolean isSelectedObjectEqual(Object selectedObject, Object value) {
      if(Tool.equals(selectedObject, value)) {
         return true;
      }

      // both shouldn't be null at this point so if one is then return false
      if(selectedObject == null || value == null) {
         return false;
      }

      String selStr = selectedObject.getClass().isArray() ? Tool.arrayToString(selectedObject) :
         Tool.toString(selectedObject);
      String valStr = value.getClass().isArray() ? Tool.arrayToString(value) :
         Tool.toString(value);
      return Tool.equals(selStr, valStr);
   }

   // input data
   private int stype;
   private boolean sortByValue = false;
   private DynamicValue2 sortTypeValue =
      new DynamicValue2(XConstants.SORT_ASC + "", XSchema.INTEGER);
   private DynamicValue2 edataDownValue =
      new DynamicValue2("false", XSchema.BOOLEAN);
   private DynamicValue2 selectFirstItem = new DynamicValue2("false", XSchema.BOOLEAN);
   private ListData data;
   private ListBindingInfo binding;
   private boolean form = false;
   private int cellHeight = AssetUtil.defh;
   // runtime
   private ListData rdata;
   private String defaultValue;
   private Object[] values;
   private String[] labels;
   private VSCompositeFormat[] formats;
   private static final Logger LOG =
      LoggerFactory.getLogger(ListInputVSAssemblyInfo.class);
}
