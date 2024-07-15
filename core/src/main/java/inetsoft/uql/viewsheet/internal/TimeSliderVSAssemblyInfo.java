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

import inetsoft.graph.GraphConstants;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.DataSerializable;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.List;

/**
 * TimeSliderVSAssemblyInfo, the assembly info of a time slider assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TimeSliderVSAssemblyInfo extends MaxModeSelectionVSAssemblyInfo
   implements TitledVSAssemblyInfo, DropDownVSAssemblyInfo, DescriptionableAssemblyInfo
{
   /**
    * Constructor.
    */
   public TimeSliderVSAssemblyInfo() {
      super();

      tinfo = new SingleTimeInfo();

      setPixelSize(new Dimension(2 * AssetUtil.defw, 2 * AssetUtil.defh));
   }

   /**
    * Initialize the default format.
    */
   public void initDefaultFormat(boolean border) {
      setDefaultFormat(border);
   }

   /**
    * Get the time information.
    * @return the time information.
    */
   public TimeInfo getTimeInfo() {
      return tinfo;
   }

   /**
    * Set the time information.
    * @param info time information.
    */
   public void setTimeInfo(TimeInfo info) {
      this.tinfo = info;
   }

   /**
    * If the tick label is visible.
    * @return visibility of tick labels.
    */
   public boolean isLabelVisible() {
      return Boolean.parseBoolean(
               labelVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * Set the visibility of tick labels.
    * @param visible the visibility of tick labels.
    */
   public void setLabelVisible(boolean visible) {
      labelVisibleValue.setRValue(visible);
   }

   /**
    * If the tick label is visible.
    * @return visibility of tick labels.
    */
   public boolean getLabelVisibleValue() {
      return Boolean.parseBoolean(labelVisibleValue.getDValue());
   }

   /**
    * Set the visibility of tick labels.
    * @param visible the visibility of tick labels.
    */
   public void setLabelVisibleValue(boolean visible) {
      labelVisibleValue.setDValue(visible + "");
   }

   /**
    * If the tick is visible.
    * @return visibility of ticks.
    */
   public boolean isTickVisible() {
      return Boolean.parseBoolean(
               tickVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * Set the visibility of ticks.
    * @param visible the visibility of ticks.
    */
   public void setTickVisible(boolean visible) {
      tickVisibleValue.setRValue(visible);
   }

   /**
    * If the tick is visible.
    * @return visibility of ticks.
    */
   public boolean getTickVisibleValue() {
      return Boolean.parseBoolean(tickVisibleValue.getDValue());
   }

   /**
    * Set the visibility of ticks.
    * @param visible the visibility of ticks.
    */
   public void setTickVisibleValue(boolean visible) {
      tickVisibleValue.setDValue(visible + "");
   }

   /**
    * Get the current position.
    * @return the current position.
    */
   public int getCurrentPos() {
      if(slist == null) {
         return 0;
      }

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         SelectionValue sval = slist.getSelectionValue(i);

         if(sval.isSelected()) {
            return i;
         }
      }

      return 0;
   }

   /**
    * Get the total length.
    * @return the total length.
    */
   public int getTotalLength() {
      return slist == null ? 0 : slist.getSelectionValueCount();
   }

   /**
    * If it is composite date.
    * @return if it is composite date.
    */
   public boolean isComposite() {
      return compositeDate;
   }

   /**
    * Set if it is composite date.
    * @param composite if it is composite date.
    */
   public void setComposite(boolean composite) {
      this.compositeDate = composite;
   }

   /**
    * Check if the numbers are in log scale.
    */
   public boolean isLogScale() {
      return Boolean.parseBoolean(
               logScaleValue.getRuntimeValue(true) + "");
   }

   /**
    * Set if the numbers are in log scale. Log scale is only applied to numeric
    * values.
    */
   public void setLogScale(boolean log) {
      logScaleValue.setRValue(log);
   }

   /**
    * Check if the numbers are in log scale.
    */
   public boolean getLogScaleValue() {
      return Boolean.parseBoolean(logScaleValue.getDValue());
   }

   /**
    * Set if the numbers are in log scale. Log scale is only applied to numeric
    * values.
    */
   public void setLogScaleValue(boolean log) {
      logScaleValue.setDValue(log + "");
   }

   /**
    * If the max value is visible.
    * @return visibility of max value.
    */
   public boolean isMaxVisible() {
      return Boolean.parseBoolean(
               maxVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * Set the visibility of max value.
    * @param visible the visibility of max value.
    */
   public void setMaxVisible(boolean visible) {
      maxVisibleValue.setRValue(visible);
   }

   /**
    * If the max value is visible.
    * @return visibility of max value.
    */
   public boolean getMaxVisibleValue() {
      return Boolean.parseBoolean(maxVisibleValue.getDValue());
   }

   /**
    * Set the visibility of max value.
    * @param visible the visibility of max value.
    */
   public void setMaxVisibleValue(boolean visible) {
      maxVisibleValue.setDValue(visible + "");
   }

   /**
    * If the min value is visible.
    * @return visibility of min value.
    */
   public boolean isMinVisible() {
      return Boolean.parseBoolean(
               minVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * Set the visibility of min value.
    * @param visible the visibility of min value.
    */
   public void setMinVisible(boolean visible) {
      minVisibleValue.setRValue(visible);
   }

   /**
    * If the min value is visible.
    * @return visibility of min value.
    */
   public boolean getMinVisibleValue() {
      return Boolean.parseBoolean(minVisibleValue.getDValue());
   }

   /**
    * Set the visibility of min value.
    * @param visible the visibility of min value.
    */
   public void setMinVisibleValue(boolean visible) {
      minVisibleValue.setDValue(visible + "");
   }

   /**
    * If the current value is visible.
    * @return visibility of current value.
    */
   public boolean isCurrentVisible() {
      return Boolean.parseBoolean(
               currentVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * Set the visibility of current value.
    * @param visible the visibility of current value.
    */
   public void setCurrentVisible(boolean visible) {
       currentVisibleValue.setRValue(visible);
   }

   /**
    * If the current value is visible.
    * @return visibility of current value.
    */
   public boolean getCurrentVisibleValue() {
      return Boolean.parseBoolean(currentVisibleValue.getDValue());
   }

   /**
    * Set the visibility of current value.
    * @param visible the visibility of current value.
    */
   public void setCurrentVisibleValue(boolean visible) {
       currentVisibleValue.setDValue(visible + "");
   }

   /**
    * Check whether the upper bound is inclusive (less than or equal to).
    */
   public boolean isUpperInclusive() {
      return Boolean.parseBoolean(
               upperInclusiveValue.getRuntimeValue(true) + "");
   }

   /**
    * Set whether the upper bound is inclusive (less than or equal to).
    */
   public void setUpperInclusive(boolean upperInclusive) {
      upperInclusiveValue.setRValue(upperInclusive);
   }

   /**
    * Check whether the upper bound is inclusive (less than or equal to).
    */
   public boolean getUpperInclusiveValue() {
      return Boolean.parseBoolean(upperInclusiveValue.getDValue());
   }

   /**
    * Set whether the upper bound is inclusive (less than or equal to).
    */
   public void setUpperInclusiveValue(boolean upperInclusive) {
      upperInclusiveValue.setDValue(upperInclusive + "");
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH, false), getViewsheet(), getName());
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
    * Check whether the time slider title is visible.
    * @return the title of the time slider assembly.
    */
   @Override
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Set the visibility of time slider title.
    * @param visible the visibility of time slider title.
    */
   @Override
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Set the time slider title visible value.
    * @param visible the specified time slider title visible.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * Get the time slider title visible value.
    * @return the title visible value of the time slider assembly.
    */
   @Override
   public boolean getTitleVisibleValue() {
       return titleInfo.getTitleVisibleValue();
   }

   /**
    * Get the time slider title height.
    * @return the title height of the time slider assembly.
    */
   @Override
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Get the time slider title height value.
    * @return the title height value of the time slider assembly.
    */
   @Override
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the time slider title height value.
    * @param value the specified time slider title height.
    */
   @Override
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   /**
    * Set the time slider title height.
    * @param value the specified time slider title height.
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
    * Set if the this assembly is adhoc filter.
    */
   @Override
   public void setAdhocFilter(boolean adFilter) {
      adhocFilter = adFilter;
   }

   /**
    * Return whether this assembly is adhoc filter.
    */
   @Override
   public boolean isAdhocFilter() {
      return adhocFilter;
   }

   /**
    * Return whether this assembly is hidden by outside selection container.
    */
   public boolean isHidden() {
      return hidden;
   }

   /**
    * Set whether this assembly is hidden by outside selection container.
    */
   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" tickVisible=\"" + isTickVisible() + "\"");
      writer.print(" labelVisible=\"" + isLabelVisible() + "\"");
      writer.print(" compositeDate=\"" + compositeDate + "\"");
      writer.print(" logScale=\"" + isLogScale() + "\"");
      writer.print(" minVisible=\"" + isMinVisible() + "\"");
      writer.print(" maxVisible=\"" + isMaxVisible() + "\"");
      writer.print(" currentVisible=\"" + isCurrentVisible() + "\"");
      writer.print(" listHeight=\"" + listHeight + "\"");
      writer.print(" listHeightScale=\"" + listHeightScale + "\"");
      writer.print(" upperInclusive=\"" + isUpperInclusive() + "\"");
      writer.print(" tickVisibleValue=\"" +
                     tickVisibleValue.getDValue() + "\"");
      writer.print(" labelVisibleValue=\"" +
                     labelVisibleValue.getDValue() + "\"");
      writer.print(" logScaleValue=\"" + logScaleValue.getDValue() + "\"");
      writer.print(" maxVisibleValue=\"" + maxVisibleValue.getDValue() + "\"");
      writer.print(" minVisibleValue=\"" + minVisibleValue.getDValue() + "\"");
      writer.print(" currentVisibleValue=\"" +
                     currentVisibleValue.getDValue() + "\"");
      writer.print(" upperInclusiveValue=\"" +
                     upperInclusiveValue.getDValue() + "\"");
      writer.print(" adhocFilter=\"" + adhocFilter + "\"");
      writer.print(" hidden=\"" + hidden + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      compositeDate = "true".equals(Tool.getAttribute(elem, "compositeDate"));
      tickVisibleValue.setDValue(getAttributeStr(elem, "tickVisible", "false"));
      labelVisibleValue.setDValue(getAttributeStr(elem, "labelVisible", "false"));
      logScaleValue.setDValue(getAttributeStr(elem, "logScale", "false"));
      minVisibleValue.setDValue(getAttributeStr(elem, "minVisible", "false"));
      maxVisibleValue.setDValue(getAttributeStr(elem, "maxVisible", "false"));
      currentVisibleValue.setDValue(getAttributeStr(elem, "currentVisible",
                                                  "false"));
      upperInclusiveValue.setDValue(getAttributeStr(elem, "upperInclusive",
                                                  "true"));
      adhocFilter = "true".equals(Tool.getAttribute(elem, "adhocFilter"));
      hidden = "true".equals(Tool.getAttribute(elem, "hidden"));
      String text = Tool.getAttribute(elem, "listHeight");
      listHeight = text == null ? 2 : Integer.parseInt(text);
      text = Tool.getAttribute(elem, "listHeightScale");
      listHeightScale = text == null ? 1D : Double.parseDouble(text);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(titleInfo != null) {
         titleInfo.writeXML(writer, getFormatInfo().getFormat(TITLEPATH));
      }

      if(tinfo != null) {
         tinfo.writeXML(writer);
      }

      if(slist != null) {
         slist.writeXML(writer);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element tnode = Tool.getChildNodeByTagName(elem, "timeInfo");

      if(tnode != null) {
         if(compositeDate) {
            tinfo = new CompositeTimeInfo();
         }
         else {
            tinfo = new SingleTimeInfo();
         }

         tinfo.parseXML(tnode);
      }

      Element snode = Tool.getChildNodeByTagName(elem, "SelectionList");

      if(snode != null) {
         slist = new SelectionList();
         slist.parseXML(snode);
      }

      titleInfo.parseXML(elem);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public TimeSliderVSAssemblyInfo clone(boolean shallow) {
      try {
         TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(tinfo != null) {
               info.tinfo = (TimeInfo) tinfo.clone();
           }

            if(slist != null) {
               info.slist = (SelectionList) slist.clone();
            }

            if(logScaleValue != null) {
               info.logScaleValue = (DynamicValue) logScaleValue.clone();
            }

            if(upperInclusiveValue != null) {
               info.upperInclusiveValue = (DynamicValue) upperInclusiveValue.clone();
            }

            if(minVisibleValue != null) {
               info.minVisibleValue = (DynamicValue) minVisibleValue.clone();
            }

            if(maxVisibleValue != null) {
               info.maxVisibleValue = (DynamicValue) maxVisibleValue.clone();
            }

            if(currentVisibleValue != null) {
               info.currentVisibleValue = (DynamicValue) currentVisibleValue.clone();
            }

            if(tickVisibleValue != null) {
               info.tickVisibleValue = (DynamicValue) tickVisibleValue.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TimeSliderVSAssemblyInfo", ex);
      }

      return null;
   }

   @Override
   protected void setDefaultFormat(boolean border) {
      super.setDefaultFormat(border);
      final VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      // time slider is a titled assembly even though title only shows in selection container.
      // clear the background by default to match old logic in SelectionBaseVSAssemblyInfo
      if(titleFormat != null) {
         titleFormat.getDefaultFormat().setBackgroundValue(null);
         titleFormat.getDefaultFormat().setBordersValue(
            new Insets(GraphConstants.NONE, GraphConstants.NONE,
                       GraphConstants.THIN_LINE, GraphConstants.NONE));
         titleFormat.getDefaultFormat().setBorderColorsValue(
            new BorderColors(new Color(0xc0c0c0), new Color(0xc0c0c0),
                             new Color(0xc0c0c0), new Color(0xc0c0c0)));
      }
   }

   /**
    * Copy the assembly info.
    */
   @Override
   public int copyInfo(VSAssemblyInfo info) {
      int hint = 0;

      // format for slider labels is applied on the server side. Force refresh
      // if format changed.
      if(!Tool.equals(getFormatInfo(), info.getFormatInfo())) {
         VSCompositeFormat vfmt1 = getFormatInfo().getFormat(OBJECTPATH);
         VSCompositeFormat vfmt2 = info.getFormatInfo().getFormat(OBJECTPATH);

         if(!Tool.equals(vfmt1.getFormat(), vfmt2.getFormat()) ||
            !Tool.equals(vfmt1.getFormatExtent(), vfmt2.getFormatExtent()))
         {
            hint = VSAssembly.OUTPUT_DATA_CHANGED;
         }
      }

      return super.copyInfo(info) | hint;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      TimeSliderVSAssemblyInfo cinfo = (TimeSliderVSAssemblyInfo) info;
      DataRef ref = getDataRef();
      DataRef cref = cinfo.getDataRef();

      if(!Tool.equals(titleInfo, cinfo.titleInfo)) {
         titleInfo = (TitleInfo) cinfo.titleInfo.clone();
         result = true;
      }
      // if data ref changes and user does not change default title value,
      // update title value
      else if(ref != null && !ref.equals(cref) &&
              cref != null && ref.getName().equals(getTitle()))
      {
         setTitleValue(cref.getName());
         result = true;
      }

      if(!Tool.equals(tickVisibleValue, cinfo.tickVisibleValue) ||
         !Tool.equals(isTickVisible(), cinfo.isTickVisible()))
      {
         tickVisibleValue = cinfo.tickVisibleValue;
         result = true;
      }

      if(!Tool.equals(labelVisibleValue, cinfo.labelVisibleValue) ||
         !Tool.equals(isLabelVisible(), cinfo.isLabelVisible()))
      {
         labelVisibleValue = cinfo.labelVisibleValue;
         result = true;
      }

      if(!Tool.equals(minVisibleValue, cinfo.minVisibleValue) ||
         !Tool.equals(isMinVisible(), cinfo.isMinVisible()))
      {
         minVisibleValue = cinfo.minVisibleValue;
         result = true;
      }

      if(!Tool.equals(maxVisibleValue, cinfo.maxVisibleValue) ||
         !Tool.equals(isMaxVisible(), cinfo.isMaxVisible()))
      {
         maxVisibleValue = cinfo.maxVisibleValue;
         result = true;
      }

      if(!Tool.equals(currentVisibleValue, cinfo.currentVisibleValue) ||
         !Tool.equals(isCurrentVisible(), cinfo.isCurrentVisible()))
      {
         currentVisibleValue = cinfo.currentVisibleValue;
         result = true;
      }

      if(listHeight != cinfo.listHeight) {
         listHeight = cinfo.listHeight;
         result = true;
      }

      if(listHeightScale!= cinfo.listHeightScale) {
         listHeightScale = cinfo.listHeightScale;
         result = true;
      }

      if(adhocFilter != cinfo.adhocFilter) {
         adhocFilter = cinfo.adhocFilter;
         result = true;
      }

      if(hidden != cinfo.hidden) {
         hidden = cinfo.hidden;
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
      TimeSliderVSAssemblyInfo cinfo = (TimeSliderVSAssemblyInfo) info;

      if(!Tool.equals(tinfo, cinfo.tinfo)) {
         if(!tinfo.equalsBinding(cinfo.tinfo)) {
            hint |= VSAssembly.INPUT_DATA_CHANGED;
            hint |= VSAssembly.BINDING_CHANGED;
         }

         // non-binding change should be treated as output change
         tinfo = (TimeInfo) cinfo.tinfo.clone();
         hint |= VSAssembly.OUTPUT_DATA_CHANGED;
      }

      if(compositeDate != cinfo.compositeDate) {
         compositeDate = cinfo.compositeDate;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Copy the output data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyOutputDataInfo(VSAssemblyInfo info) {
      boolean result = super.copyOutputDataInfo(info);
      TimeSliderVSAssemblyInfo cinfo = (TimeSliderVSAssemblyInfo) info;

      if(!Tool.equals(logScaleValue, cinfo.logScaleValue) ||
         !Tool.equals(isLogScale(), cinfo.isLogScale()))
      {
         logScaleValue = cinfo.logScaleValue;
         result = true;
      }

      if(!Tool.equals(upperInclusiveValue, cinfo.upperInclusiveValue) ||
         !Tool.equals(isUpperInclusive(), cinfo.isUpperInclusive()))
      {
         upperInclusiveValue = cinfo.upperInclusiveValue;
         result = true;
      }

      if(tinfo.getLength() != cinfo.tinfo.getLength() ||
         tinfo.getLengthValue() != cinfo.tinfo.getLengthValue())
      {
         adjustCurrentPosition(tinfo.getLength(), cinfo.tinfo.getLength());
         tinfo.setLengthValue(cinfo.tinfo.getLengthValue());
         tinfo.setLength(cinfo.tinfo.getLength());
         result = true;
      }

      if(tinfo instanceof SingleTimeInfo) {
         if(cinfo.tinfo instanceof SingleTimeInfo) {
            SingleTimeInfo tinfo0 = (SingleTimeInfo) tinfo;
            SingleTimeInfo tinfo2 = (SingleTimeInfo) cinfo.tinfo;

            if(tinfo0.getRangeSizeValue() != tinfo2.getRangeSizeValue()) {
               tinfo0.setRangeSizeValue(tinfo2.getRangeSizeValue());
               result = true;
            }

            if(tinfo0.getMaxRangeSizeValue() != tinfo2.getMaxRangeSizeValue()) {
               tinfo0.setMaxRangeSizeValue(tinfo2.getMaxRangeSizeValue());
               result = true;
            }
         }
      }

      return result;
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
    * Get the selection list.
    * @return the selection list.
    */
   public SelectionList getSelectionList() {
      return slist;
   }

   /**
    * Set the selection list.
    * @param slist the selection list.
    */
   public void setSelectionList(SelectionList slist) {
      this.slist = slist;
   }

   /**
    * Gets selections data for SelectionList, SelectionTree, TimeSlider.
    * @return selections.
    */
   @Override
   public DataSerializable getSelections() {
      return slist;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.RANGE_SLIDER;
   }

   /**
    * Get the list height.
    * @return the list height.
    */
   @Override
   public int getListHeight() {
      return listHeight;
   }

   /**
    * Set the list height.
    * @param height the list height.
    */
   @Override
   public void setListHeight(int height) {
      this.listHeight = height;
   }

   @Override
   public double getListHeightScale() {
      return listHeightScale;
   }

   @Override
   public void setListHeightScale(double listHeightScale) {
      this.listHeightScale = listHeightScale;
   }

   /**
    * Get the data ref.
    * @return the data ref.
    */
   private DataRef getDataRef() {
      if(tinfo instanceof SingleTimeInfo) {
         return ((SingleTimeInfo) tinfo).getDataRef();
      }
      else if(tinfo instanceof CompositeTimeInfo) {
         DataRef[] refs = ((CompositeTimeInfo) tinfo).getDataRefs();

         if(refs != null && refs.length > 0) {
            return refs[0];
         }
      }

      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      if(tinfo instanceof SingleTimeInfo) {
         DataRef ref = ((SingleTimeInfo) tinfo).getDataRef();
         return ref != null ? new DataRef[] {ref} : new DataRef[0];
      }
      else if(tinfo instanceof CompositeTimeInfo) {
         return ((CompositeTimeInfo) tinfo).getDataRefs();
      }

      return new DataRef[0];
   }

   /**
    * Adjust current position.
    */
   private void adjustCurrentPosition(int olength, int nlength) {
      int total = getTotalLength();
      int current = getCurrentPos();

      if(current + nlength >= total) {
         int ncurrent = Math.max(0, total - nlength - 1);

         if(slist == null) {
            return;
         }

         for(int i = ncurrent; i < current; i++) {
            SelectionValue sval = slist.getSelectionValue(i);
            sval.setState(sval.getState() | SelectionValue.STATE_SELECTED);
         }
      }
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      minVisibleValue.setRValue(null);
      maxVisibleValue.setRValue(null);
      currentVisibleValue.setRValue(null);
      tickVisibleValue.setRValue(null);
      labelVisibleValue.setRValue(null);
      logScaleValue.setRValue(null);
      upperInclusiveValue.setRValue(null);
      titleInfo.resetRuntimeValues();
      tinfo.resetRuntimeValues();
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(scaleRatio.x, 1);
   }

   /**
    * Get the assembly name, which is a description for current assembly
    * @return descriptionName
    */
   public String getDescriptionName() {
      return this.descriptionName;
   }

   /**
    * {@inheritDoc}
    */
   public void setDescriptionName(String descriptionName) {
      this.descriptionName = descriptionName;
   }

   // view
   private DynamicValue minVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue maxVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue currentVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue tickVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue labelVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private int listHeight = 2;
   private double listHeightScale = 1D;
   // input data
   private TimeInfo tinfo;
   private boolean compositeDate;
   private DynamicValue logScaleValue = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue upperInclusiveValue = new DynamicValue("true", XSchema.BOOLEAN);
   // runtime data
   private SelectionList slist;
   private TitleInfo titleInfo = new TitleInfo("RangeSlider");
   private boolean adhocFilter;
   private boolean hidden;
   private String descriptionName;

   private static final Logger LOG = LoggerFactory.getLogger(TimeSliderVSAssemblyInfo.class);
}
