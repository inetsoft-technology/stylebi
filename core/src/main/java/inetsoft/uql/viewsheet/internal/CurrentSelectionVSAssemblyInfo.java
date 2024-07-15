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

import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.Viewsheet;
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
 * CurrentSelectionVSAssemblyInfo stores current selection assembly information.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CurrentSelectionVSAssemblyInfo extends ContainerVSAssemblyInfo
   implements CompositeVSAssemblyInfo, MaxModeSupportAssemblyInfo
{
   /**
    * A constant defined the max tip value count to display in outer selection
    * title.
    */
   public static final int MAX_TIP_COUNT = 30;

   /**
    * Constructor.
    */
   public CurrentSelectionVSAssemblyInfo() {
      super();

      setPixelSize(new Dimension(3 * AssetUtil.defw, 12 * AssetUtil.defh));
      outTitles = new ArrayList<>();
      outValues = new ArrayList<>();
      outNames = new ArrayList<>();
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(true, true, false);
   }

   /**
    * Check if show runtime current selection or not.
    */
   public boolean isShowCurrentSelection() {
      return Boolean.parseBoolean(showCurrentValue.getRuntimeValue(true) + "");
   }

   /**
    * Check if show design time current selection or not.
    */
   public boolean getShowCurrentSelectionValue() {
      return Boolean.parseBoolean(showCurrentValue.getDValue());
   }

   /**
    * Set show runtime current selection or not.
    */
   public void setShowCurrentSelection(boolean show) {
      showCurrentValue.setRValue(show);
   }

   /**
    * Set show design time current selection or not.
    */
   public void setShowCurrentSelectionValue(boolean show) {
      showCurrentValue.setDValue(show + "");
   }

   /**
    * Check if runtime editable in viewer.
    */
   public boolean isAdhocEnabled() {
      return Boolean.parseBoolean(viewerCreateValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether editable in runtime viewer.
    */
   public void setAdhocEnabled(boolean adhoc) {
      viewerCreateValue.setRValue(adhoc);
   }

   /**
    * Check if design time editable in viewer or not.
    */
   public boolean getAdhocEnabledValue() {
      return Boolean.parseBoolean(viewerCreateValue.getDValue());
   }

   /**
    * Set whether editable in design time viewer.
    */
   public void setAdhocEnabledValue(boolean adhoc) {
      viewerCreateValue.setDValue(adhoc + "");
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH), getViewsheet(), getName());
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
      this.titleInfo.setTitleValue(value);
   }

   /**
    * Check whether the current selection title is visible.
    * @return the title visible of the current selection assembly.
    */
   @Override
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Get the run time current selection title visible value.
    * @return the title visible value of the current selection assembly.
    */
   @Override
   public boolean getTitleVisibleValue() {
      return titleInfo.getTitleVisibleValue();
   }

   /**
    * Set the current selection title value.
    * @param visible the specified current selection title visible.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * Set the current selection title visible value.
    * @param visible the specified current selection title visible.
    */
   @Override
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Get the current selection title height.
    * @return the title height of the current selection assembly.
    */
   @Override
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Get the current selection title height value.
    * @return the title height value of the current selection assembly.
    */
   @Override
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the current selection title height value.
    * @param value the specified current selection title height.
    */
   @Override
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   /**
    * Set the current selection title height.
    * @param value the specified current selection title height.
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

   public boolean isEmpty() {
      if(!this.isShowCurrentSelection()) {
         return getAssemblyCount() == 0;
      }

      return getAssemblyCount() == 0 && getOutSelection().isEmpty();
   }

   /**
    * Clear outward selections.
    */
   public void clearOutSelections() {
      outNames.clear();
      outTitles.clear();
      outValues.clear();
   }

   /**
    * Get outward selections title.
    * @return the outward selections' title.
    */
   public String[] getOutSelectionTitles() {
      String[] arr = new String[outTitles.size()];
      outTitles.toArray(arr);
      return arr;
   }

   /**
    * Get outward selections selected value.
    * @return the outward selections selected value.
    */
   public String[] getOutSelectionValues() {
      String[] arr = new String[outValues.size()];
      outValues.toArray(arr);
      return arr;
   }

   /**
    * Get outward selections selected name.
    * @return the outward selections selected name.
    */
   public String[] getOutSelectionNames() {
      String[] arr = new String[outNames.size()];
      outNames.toArray(arr);
      return arr;
   }

   /**
    * Set outward selection title.
    */
   public void setOutSelectionValue(String name, String title, String value) {
      if(name != null) {
         outNames.add(name);
         outTitles.add(title);
         String[] tips = value == null ? new String[0] : value.split("; ");

         if(tips.length > MAX_TIP_COUNT) {
            value = "";

            for(int i = 0; i < MAX_TIP_COUNT; i++) {
               if(i > 0) {
                  value += "; ";
               }

               value += tips[i];
            }

            // another line to display ...
            value += "; ...";
         }

         outValues.add(value);
      }
   }

   /**
    * Set the size ration of the title label and value.
    * @param ratio a value from 0 to 1, a value of NaN means using default.
    */
   public void setTitleRatio(double ratio) {
      this.titleRatio = ratio;
   }

   /**
    * Get the size ration of the title label and value.
    */
   public double getTitleRatio() {
      return this.titleRatio;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" showCurrent=\"" + isShowCurrentSelection() + "\"");
      writer.print(" viewerCreate=\"" + isAdhocEnabled() + "\"");
      writer.print(" showCurrentValue=\"" +
         showCurrentValue.getDValue() + "\"");
      writer.print(" viewerCreateValue=\"" +
         viewerCreateValue.getDValue() + "\"");

      if(!Double.isNaN(titleRatio)) {
         writer.print(" ratio=\"" + titleRatio + "\"");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      showCurrentValue.setDValue(getAttributeStr(elem, "showCurrent", "false"));
      viewerCreateValue.setDValue(
         getAttributeStr(elem, "viewerCreate", "true"));

      String str = Tool.getAttribute(elem, "ratio");

      if(str == null) {
         titleRatio = Double.NaN;
      }
      else {
         titleRatio = Double.parseDouble(str);
      }
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

      if(outNames != null && outNames.size() > 0) {
         writer.print("<names>");

         for(int i = 0; i < outNames.size(); i++) {
            writer.print("<name>");
            writer.print("<![CDATA[" + outNames.get(i) + "]]>");
            writer.print("</name>");
         }

         writer.println("</names>");
      }

      if(outTitles != null && outTitles.size() > 0) {
         writer.print("<titles>");

         for(int i = 0; i < outTitles.size(); i++) {
            writer.print("<title>");
            writer.print("<![CDATA[" +
               (outTitles.get(i) == null ? "" :
               Tool.localize(outTitles.get(i).toString())) + "]]>");
            writer.print("</title>");
         }

         writer.println("</titles>");
      }

      if(outValues != null && outValues.size() > 0) {
         writer.print("<values>");

         for(int i = 0; i < outTitles.size(); i++) {
            writer.print("<value>");
            writer.print("<![CDATA[" + (outValues.get(i) == null ? "(none)" :
               outValues.get(i)) + "]]>");
            writer.print("</value>");
         }

         writer.println("</values>");
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

      Element snode = Tool.getChildNodeByTagName(elem, "names");

      if(snode != null) {
         NodeList slist = Tool.getChildNodesByTagName(snode, "name");

         if(slist != null && slist.getLength() > 0) {
            outNames = new ArrayList<>();

            for(int i = 0; i < slist.getLength(); i++) {
               outNames.add(Tool.getValue(slist.item(i)));
            }
         }
      }

      snode = Tool.getChildNodeByTagName(elem, "titles");

      if(snode != null) {
         NodeList slist = Tool.getChildNodesByTagName(snode, "title");

         if(slist != null && slist.getLength() > 0) {
            outTitles = new ArrayList<>();

            for(int i = 0; i < slist.getLength(); i++) {
               outTitles.add(Tool.getValue(slist.item(i)));
            }
         }
      }

      snode = Tool.getChildNodeByTagName(elem, "values");

      if(snode != null) {
         NodeList slist = Tool.getChildNodesByTagName(snode, "value");

         if(slist != null && slist.getLength() > 0) {
            outValues = new ArrayList<>();

            for(int i = 0; i < slist.getLength(); i++) {
               outValues.add(Tool.getValue(slist.item(i)));
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
      CurrentSelectionVSAssemblyInfo cinfo =
         (CurrentSelectionVSAssemblyInfo) info;

      if(!Tool.equals(titleInfo, cinfo.titleInfo)) {
         titleInfo = cinfo.titleInfo;
         result = true;
      }

      if(!Tool.equals(showCurrentValue, cinfo.showCurrentValue) ||
         !Tool.equals(isShowCurrentSelection(), cinfo.isShowCurrentSelection()))
      {
         showCurrentValue = cinfo.showCurrentValue;
         result = true;
      }

      if(!Tool.equals(viewerCreateValue, cinfo.viewerCreateValue) ||
         !Tool.equals(isAdhocEnabled(), cinfo.isAdhocEnabled()))
      {
         viewerCreateValue = cinfo.viewerCreateValue;
         result = true;
      }

      if(!Tool.equals(outTitles, cinfo.outTitles)) {
         outTitles = cinfo.outTitles;
         result = true;
      }

      if(!Tool.equals(outValues, cinfo.outValues)) {
         outValues = cinfo.outValues;
         result = true;
      }

      if(titleRatio != cinfo.titleRatio) {
         titleRatio = cinfo.titleRatio;
         result = true;
      }

      return result;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.SELECTION_CONTAINER;
   }

   /**
    * Update objects.
    */
   public void update(int index0, int index1, boolean swapOutSels) {
      if(swapOutSels) {
         updateOutSels(index0, index1);
      }
      else {
         updateAssemblies(index0, index1);
      }
   }

   /**
    * Update out selections.
    */
   private void updateOutSels(int index0, int index1) {
      update(index0, index1, outNames);
      update(index0, index1, outTitles);
      update(index0, index1, outValues);
   }

   /**
    * Update assemblies.
    */
   private void updateAssemblies(int index0, int index1) {
      update(index0, index1, getAssemblies());
   }

   /**
    * Swap objects.
    */
   private void update(int id0, int id1, ArrayList<String> list) {
      String[] temps = new String[list.size()];
      list.toArray(temps);
      update(id0, id1, temps);

      for(int i = 0; i < temps.length; i++) {
         list.set(i, temps[i]);
      }
   }

   /**
    * Swap objects.
    */
   private void update(int id0, int id1, String[] list) {
      if(id0 >= id1) {
         updatePositive(id0, id1, list);
      }
      else {
         updateNegative(id0, id1, list);
      }
   }

   /**
    * Positive update.
    */
   private void updatePositive(int max, int min, String[] list) {
      String tmp = list[max];

      for(int i = max; i > min; i--) {
         list[i] = list[i - 1];
      }

      list[min] = tmp;
   }

   /**
    * Negative update.
    */
   private void updateNegative(int min, int max, String[] list) {
      String tmp = list[min];
      max = Math.min(max, list.length - 1); // avoid iob exception (49375)

      // 2/10/2017 @damianwysocki moving from index min to max,
      // previous logic would move from min to max-1
      for(int i = min; i < max; i++) {
         list[i] = list[i + 1];
      }

      list[max] = tmp;
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
    * Get the out selection assemblies.
    */
   public List<String> getOutSelection() {
      return outNames;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public CurrentSelectionVSAssemblyInfo clone(boolean shallow) {
      try {
         CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(titleInfo != null) {
               info.titleInfo = (TitleInfo) titleInfo.clone();
            }

            if(outNames != null) {
               info.outNames = new ArrayList<>(outNames);
            }

            if(outTitles != null) {
               info.outTitles = new ArrayList<>(outTitles);
            }

            if(outValues != null) {
               info.outValues = new ArrayList<>(outValues);
            }

            info.showCurrentValue = (DynamicValue) showCurrentValue.clone();
            info.viewerCreateValue = (DynamicValue) viewerCreateValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CurrentSelectionVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      titleInfo.resetRuntimeValues();
      showCurrentValue.setRValue(null);
      viewerCreateValue.setRValue(null);
   }

   @Override
   public Dimension getMaxSize() {
      return maxSize;
   }

   @Override
   public void setMaxSize(Dimension maxSize) {
      this.maxSize = maxSize;
   }

   /**
    * @return the z-index value when in max mode
    */
   @Override
   public int getMaxModeZIndex() {
      return maxModeZIndex > 0 ? maxModeZIndex : getZIndex();
   }

   /**
    * Set the z-index value when in max mode
    */
   @Override
   public void setMaxModeZIndex(int maxModeZIndex) {
      this.maxModeZIndex = maxModeZIndex;
   }

   private static final Logger LOG = LoggerFactory.getLogger(CurrentSelectionVSAssemblyInfo.class);

   private TitleInfo titleInfo = new TitleInfo("SelectionContainer");
   private DynamicValue showCurrentValue = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue viewerCreateValue = new DynamicValue("true", XSchema.BOOLEAN);
   private ArrayList<String> outTitles;
   private ArrayList<String> outValues;
   private ArrayList<String> outNames;
   private double titleRatio = Double.NaN;
   // max mode
   private Dimension maxSize = null;
   private int maxModeZIndex = -1;
}
