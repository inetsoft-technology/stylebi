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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;

/**
 * RadioButtonVSAssemblyInfo stores basic radio button assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RadioButtonVSAssemblyInfo extends ListInputVSAssemblyInfo
   implements CompoundVSAssemblyInfo, CompositeVSAssemblyInfo
{
   /**
    * Constructor.
    */
   public RadioButtonVSAssemblyInfo() {
      super();

      setPixelSize(new Dimension(2 * AssetUtil.defw, 2 * AssetUtil.defh));
   }

   /**
    * Get the group title.
    * @return the title of the radio button assembly.
    */
   @Override
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH), getViewsheet(), getName());
   }

   /**
    * Get the group title value.
    * @return the title value of the radio button assembly.
    */
   @Override
   public String getTitleValue() {
      return titleInfo.getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      titleInfo.setTitleValue(value);
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitle(String value) {
      titleInfo.setTitle(value);
   }

    /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public RadioButtonVSAssemblyInfo clone(boolean shallow) {
      try {
         RadioButtonVSAssemblyInfo info = (RadioButtonVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(titleInfo != null) {
               info.titleInfo = (TitleInfo) titleInfo.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone RadioButtonVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * If the group title is visible.
    * @return visibility of group title.
    */
   @Override
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Set the visibility of group title.
    * @param visible the visibility of group title.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * Get the visibility of radiobutton title.
    * @return the visibility of radiobutton title.
    */
   @Override
   public boolean getTitleVisibleValue() {
       return titleInfo.getTitleVisibleValue();
   }

   /**
    * Set the visibility of radiobutton title.
    * @param visible the visibility of radiobutton title.
    */
   @Override
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Get the radiobutton title height.
    * @return the title height of the radiobutton assembly.
    */
   @Override
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Get the radiobutton title height value.
    * @return the title height value of the radiobutton assembly.
    */
   @Override
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the radiobutton title height value.
    * @param value the specified radiobutton title height.
    */
   @Override
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   /**
    * Set the radiobutton title height.
    * @param value the specified radiobutton title height.
    */
   @Override
   public void setTitleHeight(int value) {
      titleInfo.setTitleHeight(value);
   }

   @Override
   public Insets getTitlePadding() {
      return titleInfo.getPadding();
   }

   @Override
   public void setTitlePadding(Insets padding, CompositeValue.Type type) {
      titleInfo.setPadding(padding, type);
   }

   /**
    * Get the selected object of this assembly info.
    * @return the selected object of this assembly info.
    */
   @Override
   public Object getSelectedObject() {
      return selectedObject;
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

      this.selectedObject = obj;

      return VSAssembly.OUTPUT_DATA_CHANGED;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);
      list.addAll(titleInfo.getViewDynamicValues());

      return list;
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
      Object[] values = getValues();

      // if value from variable don't clear
      if(isVariable() && values.length == 0) {
         return;
      }

      boolean contained = false;

      for(int i = 0; i < values.length; i++) {
         Object value = Tool.getData(getDataType(), values[i]);

         if(isSelectedObjectEqual(selectedObject, value)) {
            selectedObject = values[i];
            contained = true;
            break;
         }
      }

      // don't clear selected value if values are set through script (45582).
      if(!contained && (getListData() != null && getListData().isBinding() || values.length > 0)) {
         selectedObject = values.length > 0 ? values[0] : null;
      }
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

      titleInfo.renameDepended(oname, nname, vs);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(titleInfo != null) {
         titleInfo.writeXML(writer, getFormatInfo().getFormat(TITLEPATH),
            getViewsheet(), getName());
      }

      writer.print("<selectedObject>");
      writer.print("<![CDATA[" + Tool.getPersistentDataString(selectedObject,
         getDataType()) + "]]>");
      writer.print("</selectedObject>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      titleInfo.parseXML(elem);
      Element snode = Tool.getChildNodeByTagName(elem, "selectedObject");

      if(snode != null) {
         selectedObject = getPersistentData(getDataType(), Tool.getValue(snode));
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      RadioButtonVSAssemblyInfo cinfo = (RadioButtonVSAssemblyInfo) info;

      if(!Tool.equals(titleInfo, cinfo.titleInfo)) {
         titleInfo = cinfo.titleInfo;
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
      RadioButtonVSAssemblyInfo rinfo = (RadioButtonVSAssemblyInfo) info;

      if(!Tool.equals(selectedObject, rinfo.selectedObject)) {
         selectedObject = rinfo.selectedObject;
         result = true;
      }

      return result;
   }

   /**
    * Set the default vsobject format.
    * @param bcolor border color.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      // set default format of title
      VSCompositeFormat format = new VSCompositeFormat();
      format.getDefaultFormat().setBordersValue(new Insets(0, 0, 0, 0));
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      format.getCSSFormat().setCSSType(getObjCSSType() + CSSConstants.TITLE);
      format.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_TOP);
      getFormatInfo().setFormat(TITLEPATH, format);

      // set default format of detail
      TableDataPath datapath = new TableDataPath(-1, TableDataPath.DETAIL);
      format = new VSCompositeFormat();
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.PLAIN, 10));
      format.getDefaultFormat().setAlignmentValue(StyleConstants.CENTER);
      format.getCSSFormat().setCSSType(getObjCSSType() + CSSConstants.CELL);
      getFormatInfo().setFormat(datapath, format);

      // set default format of object
      format = new VSCompositeFormat();
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.PLAIN, 11));
      format.getCSSFormat().setCSSType(getObjCSSType());
      getFormatInfo().setFormat(OBJECTPATH, format);
      setCSSDefaults();
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.RADIO_BUTTON;
   }

   /**
    * Clear the selected objects.
    */
   @Override
   public void clearSelectedObjects() {
      selectedObject = null;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      titleInfo.resetRuntimeValues();
   }

   // view
   private TitleInfo titleInfo = new TitleInfo("RadioButton");
   // output data
   private Object selectedObject;

   private static final Logger LOG =
      LoggerFactory.getLogger(RadioButtonVSAssemblyInfo.class);
}
