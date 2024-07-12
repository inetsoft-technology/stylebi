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
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * CheckBoxVSAssemblyInfo stores basic checkbox assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CheckBoxVSAssemblyInfo extends ListInputVSAssemblyInfo
   implements CompoundVSAssemblyInfo, CompositeVSAssemblyInfo
{
   /**
    * Constructor.
    */
   public CheckBoxVSAssemblyInfo() {
      super();

      selectedObjects = new Object[0];
      setPixelSize(new Dimension(2 * AssetUtil.defw, 2 * AssetUtil.defh));
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH),
         getViewsheet(), getName());
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
    * Get the group title value.
    * @return the title value of the checkbox assembly.
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
    * If the checkbox title is visible.
    * @return visibility of checkbox title.
    */
   @Override
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Set the visibility of check box  title.
    * @param visible the visibility of check box  title.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * If the checkbox title is visible.
    * @return visibility of checkbox title.
    */
   @Override
   public boolean getTitleVisibleValue() {
      return titleInfo.getTitleVisibleValue();
   }

   /**
    * Set the visibility of checkbox title.
    * @param visible the visibility of checkbox title.
    */
   @Override
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Get the checkbox title height.
    * @return the title height of the checkbox assembly.
    */
   @Override
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Get the checkbox title height value.
    * @return the title height value of the checkbox assembly.
    */
   @Override
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the checkbox title height value.
    * @param value the specified checkbox title height.
    */
   @Override
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   /**
    * Set the checkbox title height.
    * @param value the specified checkbox title height.
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
   public Object[] getSelectedObjects() {
      return selectedObjects;
   }

   /**
    * Set the selected object of this assembly info.
    * @param objs the selected object of this assembly info.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObjects(Object[] objs) {
      objs = objs == null ? new Object[0] : objs;

      if(Tool.equals(objs, this.selectedObjects)) {
         return VSAssembly.NONE_CHANGED;
      }

      this.selectedObjects = objs;
      return VSAssembly.OUTPUT_DATA_CHANGED;
   }

   /**
    * Set the selected object.
    */
   @Override
   public int setSelectedObject(Object val) {
      return setSelectedObjects((val == null) ? new Object[0] :
                                new Object[] {val});
   }

   /**
    * Set the values of this assembly info.
    * @param values the values of this assembly info.
    */
   @Override
   public void setValues(Object[] values) {
      super.setValues(values);
   }

   /**
    * Validate the selected object.
    */
   @Override
   public void validate() {
      Object[] values = getValues();
      List list = new ArrayList();

      for(int i = 0; i < selectedObjects.length; i++) {
         boolean contained = false;

         for(int j = 0; j < values.length; j++) {
            if(isSelectedObjectEqual(selectedObjects[i], values[j])) {
               selectedObjects[i] = values[j];
               contained = true;
               break;
            }
         }

         if(contained) {
            list.add(selectedObjects[i]);
         }
      }

      selectedObjects = new Object[list.size()];
      list.toArray(selectedObjects);
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
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public CheckBoxVSAssemblyInfo clone(boolean shallow) {
      try {
         CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(selectedObjects != null) {
               info.selectedObjects = selectedObjects.clone();
            }

            if(titleInfo != null) {
               info.titleInfo = (TitleInfo) titleInfo.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CheckBoxVSAssemblyInfo", ex);
      }

      return null;
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

      if(selectedObjects != null && selectedObjects.length > 0) {
         writer.print("<selectedObjects>");

         for(int i = 0; i < selectedObjects.length; i++) {
            writer.print("<selectedObject>");
            writer.print("<![CDATA[" + Tool.getPersistentDataString(selectedObjects[i],
                         getDataType()) + "]]>");
            writer.print("</selectedObject>");
         }

         writer.println("</selectedObjects>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      titleInfo.parseXML(elem);

      Element snode = Tool.getChildNodeByTagName(elem, "selectedObjects");

      if(snode != null) {
         NodeList slist = Tool.getChildNodesByTagName(snode, "selectedObject");

         if(slist != null && slist.getLength() > 0) {
            selectedObjects = new Object[slist.getLength()];

            for(int i = 0; i < slist.getLength(); i++) {
               selectedObjects[i] =
                       getPersistentData(getDataType(), Tool.getValue(slist.item(i)));
            }
         }
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
      CheckBoxVSAssemblyInfo cinfo = (CheckBoxVSAssemblyInfo) info;

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
      CheckBoxVSAssemblyInfo cinfo = (CheckBoxVSAssemblyInfo) info;

      if(!Tool.equals(selectedObjects, cinfo.selectedObjects)) {
         selectedObjects = cinfo.selectedObjects;
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
      // set default format of title
      VSCompositeFormat format = new VSCompositeFormat();
      format.getDefaultFormat().setBordersValue(new Insets(0, 0, 0, 0));
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      format.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_TOP);
      format.getCSSFormat().setCSSType(getObjCSSType() + CSSConstants.TITLE);
      getFormatInfo().setFormat(TITLEPATH, format);

      // set default format of cell
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
      return CSSConstants.CHECKBOX;
   }

   /**
    * Clear the selected objects.
    */
   @Override
   public void clearSelectedObjects() {
      selectedObjects = new Object[0];
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
   private TitleInfo titleInfo = new TitleInfo("CheckBox");
   // output data
   private Object[] selectedObjects;

   private static final Logger LOG =
      LoggerFactory.getLogger(CheckBoxVSAssemblyInfo.class);
}
