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

import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.DataComparer;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * SliderVSAssemblyInfo stores basic numeric slider assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SliderVSAssemblyInfo extends NumericRangeVSAssemblyInfo {
   /**
    * Constructor.
    */
   public SliderVSAssemblyInfo() {
      super();

      setPixelSize(new Dimension(2 * AssetUtil.defw, 2 * AssetUtil.defh));
   }

   /**
    * If the runtime tick label is visible.
    * @return visibility of tick labels.
    */
   public boolean isLabelVisible() {
      return Boolean.valueOf(labelVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * If the design time tick label is visible.
    * @return visibility of tick labels.
    */
   public boolean getLabelVisibleValue() {
      return Boolean.valueOf(labelVisibleValue.getDValue());
   }

   /**
    * Set the runtime visibility of tick labels.
    * @param visible the visibility of tick labels.
    */
   public void setLabelVisible(boolean visible) {
      labelVisibleValue.setRValue(visible);
   }

   /**
    * Set the design time visibility of tick labels.
    * @param visible the visibility of tick labels.
    */
   public void setLabelVisibleValue(boolean visible) {
      labelVisibleValue.setDValue(visible + "");
   }

   /**
    * Computer double increment precision.
    * @return The number after the decimal point.
    */
   public int getIncreamentPrecision() {
      int incPrecision = 2;
      String incStr = String.valueOf(getIncrement());
      int index = incStr.indexOf(".");

      if(index != -1) {
         incPrecision = Math.max(incStr.substring(index).length(), 2);
      }

      return incPrecision;
   }

   /**
    * Get the formatted labels for the min, ticks, and max.
    * @return the formatted labels.
    */
   public String[] getLabels() {
      double min = getMin();
      double max = getMax();
      double inc = getIncrement();
      labels = new String[(int) Math.ceil(Math.max(0, max - min) / inc) + 1];
      int i = 0;
      int incPrecision = getIncreamentPrecision();
      double power = Math.pow(10, incPrecision);

      for(double tick = min; DataComparer.compare(tick, max) < 0; tick += inc) {
         labels[i++] = formatLabel(Math.round(tick * power) / power);
      }

      labels[i] = formatLabel(max);
      return labels;
   }

   /**
    * If the runtime tick is visible.
    * @return visibility of ticks.
    */
   public boolean isTickVisible() {
      return Boolean.valueOf(tickVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * If the design time tick is visible.
    * @return visibility of ticks.
    */
   public boolean getTickVisibleValue() {
      return Boolean.valueOf(tickVisibleValue.getDValue());
   }

   /**
    * Set the runtime visibility of ticks.
    * @param visible the visibility of ticks.
    */
   public void setTickVisible(boolean visible) {
      tickVisibleValue.setRValue(visible);
   }

   /**
    * Set the design time visibility of ticks.
    * @param visible the visibility of ticks.
    */
   public void setTickVisibleValue(boolean visible) {
      tickVisibleValue.setDValue(visible + "");
   }

   /**
    * If the runtime max value is visible.
    * @return visibility of max value.
    */
   public boolean isMaxVisible() {
      return Boolean.valueOf(maxVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * If the design time max value is visible.
    * @return visibility of max value.
    */
   public boolean getMaxVisibleValue() {
      return Boolean.valueOf(maxVisibleValue.getDValue());
   }

   /**
    * Set the runtime visibility of max value.
    * @param visible the visibility of max value.
    */
   public void setMaxVisible(boolean visible) {
      maxVisibleValue.setRValue(visible);
   }

   /**
    * Set the design time visibility of max value.
    * @param visible the visibility of max value.
    */
   public void setMaxVisibleValue(boolean visible) {
      maxVisibleValue.setDValue(visible + "");
   }

   /**
    * If the runtime min value is visible.
    * @return visibility of min value.
    */
   public boolean isMinVisible() {
      return Boolean.valueOf(minVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * If the design time min value is visible.
    * @return visibility of min value.
    */
   public boolean getMinVisibleValue() {
      return Boolean.valueOf(minVisibleValue.getDValue());
   }

   /**
    * Set the runtime visibility of min value.
    * @param visible the visibility of min value.
    */
   public void setMinVisible(boolean visible) {
      minVisibleValue.setRValue(visible);
   }

   /**
    * Set the design time visibility of min value.
    * @param visible the visibility of min value.
    */
   public void setMinVisibleValue(boolean visible) {
      minVisibleValue.setDValue(visible + "");
   }

   /**
    * If the runtime current value is visible.
    * @return visibility of current value.
    */
   public boolean isCurrentVisible() {
      return Boolean.valueOf(currentVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * If the design time current value is visible.
    * @return visibility of current value.
    */
   public boolean getCurrentVisibleValue() {
      return Boolean.valueOf(currentVisibleValue.getDValue());
   }

   /**
    * Set the runtime visibility of current value.
    * @param visible the visibility of current value.
    */
   public void setCurrentVisible(boolean visible) {
      currentVisibleValue.setRValue(visible);
   }

   /**
    * Set the design time visibility of current value.
    * @param visible the visibility of current value.
    */
   public void setCurrentVisibleValue(boolean visible) {
      currentVisibleValue.setDValue(visible + "");
   }

   /**
    * Check whether to snap to increment.
    */
   public boolean isSnap() {
      return Boolean.valueOf(snapValue.getRuntimeValue(true) + "");
   }

   /**
    * Get snap to increment design time value.
    */
   public boolean getSnapValue() {
      return Boolean.valueOf(snapValue.getDValue());
   }

   /**
    * Set the runtime snap to increment value.
    */
   public void setSnap(boolean visible) {
      snapValue.setRValue(visible);
   }

   /**
    * Set the design time snap to increment value;
    */
   public void setSnapValue(boolean visible) {
      snapValue.setDValue(visible + "");
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
      writer.print(" minVisible=\"" + isMinVisible() + "\"");
      writer.print(" maxVisible=\"" + isMaxVisible() + "\"");
      writer.print(" currentVisible=\"" + isCurrentVisible() + "\"");
      writer.print(" tickVisibleValue=\"" +
         tickVisibleValue.getDValue() + "\"");
      writer.print(" labelVisibleValue=\"" +
         labelVisibleValue.getDValue() + "\"");
      writer.print(" minVisibleValue=\"" + minVisibleValue.getDValue() + "\"");
      writer.print(" maxVisibleValue=\"" + maxVisibleValue.getDValue() + "\"");
      writer.print(" currentVisibleValue=\"" + currentVisibleValue.getDValue() + "\"");
      writer.print(" snapValue=\"" + snapValue.getDValue() + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      tickVisibleValue.setDValue(getAttributeStr(elem, "tickVisible", "true"));
      minVisibleValue.setDValue(getAttributeStr(elem, "minVisible", "true"));
      maxVisibleValue.setDValue(getAttributeStr(elem, "maxVisible", "true"));
      labelVisibleValue.setDValue(getAttributeStr(elem, "labelVisible", "true"));
      currentVisibleValue.setDValue(getAttributeStr(elem, "currentVisible", "true"));
      snapValue.setDValue(getAttributeStr(elem, "snap", "false"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      double min = getMin();
      double max = getMax();
      double inc = getIncrement();
      int incPrecision = getIncreamentPrecision();
      double power = Math.pow(10, incPrecision);
      String maxstr = formatLabel(Double.valueOf(max));

      // if format is defined, the label can't be null
      if(maxstr != null) {
         writer.println("<labels>");

         for(double tick = min; tick < max; tick += inc) {
            String str =
               formatLabel(Double.valueOf(Math.round(tick * power) / power));
            writer.print("<label><![CDATA[" + str + "]]></label>");
         }

         writer.print("<label><![CDATA[" + maxstr + "]]></label>");
         writer.println("</labels>");
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
      SliderVSAssemblyInfo cinfo = (SliderVSAssemblyInfo) info;

      if(!Tool.equals(tickVisibleValue, cinfo.tickVisibleValue) ||
         isTickVisible() != cinfo.isTickVisible())
      {
         tickVisibleValue = cinfo.tickVisibleValue;
         result = true;
      }

      if(!Tool.equals(labelVisibleValue, cinfo.labelVisibleValue) ||
         isLabelVisible() != cinfo.isLabelVisible())
      {
         labelVisibleValue = cinfo.labelVisibleValue;
         result = true;
      }

      if(!Tool.equals(minVisibleValue, cinfo.minVisibleValue) ||
         isMinVisible() != cinfo.isMinVisible())
      {
         minVisibleValue = cinfo.minVisibleValue;
         result = true;
      }

      if(!Tool.equals(maxVisibleValue, cinfo.maxVisibleValue) ||
         isMaxVisible() != cinfo.isMaxVisible())
      {
         maxVisibleValue = cinfo.maxVisibleValue;
         result = true;
      }

      if(!Tool.equals(currentVisibleValue, cinfo.currentVisibleValue) ||
         isCurrentVisible() != cinfo.isCurrentVisible())
      {
         currentVisibleValue = cinfo.currentVisibleValue;
         result = true;
      }

      if(!Tool.equals(snapValue, cinfo.snapValue) || isSnap() != cinfo.isSnap()) {
         snapValue = cinfo.snapValue;
         result = true;
      }

      return result;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.SLIDER;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      tickVisibleValue.setRValue(null);
      labelVisibleValue.setRValue(null);
      minVisibleValue.setRValue(null);
      maxVisibleValue.setRValue(null);
      currentVisibleValue.setRValue(null);
      snapValue.setRValue(null);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public SliderVSAssemblyInfo clone(boolean shallow) {
      try {
         SliderVSAssemblyInfo info = (SliderVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(tickVisibleValue != null) {
               info.tickVisibleValue = tickVisibleValue.clone();
            }

            if(labelVisibleValue != null) {
               info.labelVisibleValue = labelVisibleValue.clone();
            }

            if(minVisibleValue != null) {
               info.minVisibleValue = minVisibleValue.clone();
            }

            if(maxVisibleValue != null) {
               info.maxVisibleValue = maxVisibleValue.clone();
            }

            if(currentVisibleValue != null) {
               info.currentVisibleValue = currentVisibleValue.clone();
            }

            info.snapValue = snapValue.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SliderVSAssemblyInfo", ex);
      }

      return null;
   }

   private DynamicValue tickVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue labelVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue minVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue maxVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue currentVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue snapValue = new DynamicValue("false", XSchema.BOOLEAN);
   private String[] labels;
   private static final Logger LOG = LoggerFactory.getLogger(SliderVSAssemblyInfo.class);
}
