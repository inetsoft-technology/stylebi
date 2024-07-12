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
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;

/**
 * GaugeVSAssemblyInfo stores basic gauge assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class GaugeVSAssemblyInfo extends RangeOutputVSAssemblyInfo implements DescriptionableAssemblyInfo
{
   /**
    * The default face id without theme of the VSGauge.
    */
   private static final int DEFAULT_FACE_ID = 10120;

   /**
    * Constructor.
    */
   public GaugeVSAssemblyInfo() {
      super();
      setPixelSize(new Dimension(140, 140));
      setFace(DEFAULT_FACE_ID);
   }

   /**
    * If the runtime label is visible.
    * @return the label visibility of the assembly.
    */
   public boolean isLabelVisible() {
      return Boolean.parseBoolean(labelVisibleValue.getRuntimeValue(true) + "");
   }

   /**
    * If the design time label is visible.
    * @return the label visibility of the assembly.
    */
   public boolean getLabelVisibleValue() {
      return Boolean.parseBoolean(labelVisibleValue.getDValue());
   }

   /**
    * Set the runtime label visibility.
    * @param visible the specified label visibility.
    */
   public void setLabelVisible(boolean visible) {
      labelVisibleValue.setRValue(visible);
   }

   /**
    * Set the design time label visiblity
    */
   public void setLabelVisibleValue(boolean visible) {
      labelVisibleValue.setDValue(visible + "");
   }

    /**
    * Get the runtime value fill.
    * @return the value fill of the assembly.
    */
   public Color getValueFillColor() {
     return Tool.getColorData(vfColorValue.getRValue());
   }

   /**
    * Set the runtime value fill color
    * @param vf the specified value fill.
    */
   public void setValueFillColor(Color vf) {
      this.vfColorValue.setRValue(vf);
   }

   /**
    * Get the design time label is visible.
    * @return the value fill value of the assembly.
    */
   public String getValueFillColorValue() {
      return vfColorValue.getDValue();
   }

   /**
    * Set the value fill value (expression or RGB number) to this assembly.
    * @param vfval the specified value fill value.
    */
   public void setValueFillColorValue(String vfval) {
      this.vfColorValue.setDValue(vfval);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" labelVisible=\"" + isLabelVisible() + "\"");
      writer.print(" labelVisibleValue=\"" + labelVisibleValue.getDValue() + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      labelVisibleValue.setDValue(getAttributeStr(elem, "labelVisible", "true"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(vfColorValue.getDValue() != null) {
         writer.print("<valueFill>");
         writer.print("<![CDATA[" + vfColorValue.getDValue() + "]]>");
         writer.println("</valueFill>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      vfColorValue.setDValue(Tool.getChildValueByTagName(elem, "valueFill"));
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      GaugeVSAssemblyInfo cinfo = (GaugeVSAssemblyInfo) info;

      if(!Tool.equals(labelVisibleValue, cinfo.labelVisibleValue) ||
         !Tool.equals(isLabelVisible(), cinfo.isLabelVisible()))
      {
         labelVisibleValue = cinfo.labelVisibleValue;
         result = true;
      }

      if(!Tool.equals(vfColorValue, cinfo.vfColorValue) ||
         !Tool.equals(getValueFillColorValue(), cinfo.getValueFillColorValue()))
      {
         vfColorValue = cinfo.vfColorValue;
         result = true;
      }

      return result;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.GAUGE;
   }

   /**
    * Set the format to this assembly info.
    * @param fmt the specified format.
    */
   @Override
   public void setFormat(VSCompositeFormat fmt) {
      VSCompositeFormat nfmt = fmt == null ? VSGauge.getDefaultFormat(getFace()) : fmt;
      super.setFormat(nfmt);
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      labelVisibleValue.setRValue(null);
      vfColorValue.setRValue(null);

      if(getBindingInfo() == null || getBindingInfo().isEmpty()) {
         setValue(null);
      }
   }

   /**
    * Set the default vsobject format.
    */
   @Override
   protected void setDefaultFormat(boolean border, boolean setFormat, boolean fill) {
      super.setDefaultFormat(border, setFormat, fill);

      int align = StyleConstants.H_CENTER | StyleConstants.V_CENTER;
      getFormat().getDefaultFormat().setAlignmentValue(align);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public GaugeVSAssemblyInfo clone(boolean shallow) {
      try {
         GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(labelVisibleValue != null) {
               info.labelVisibleValue = (DynamicValue) labelVisibleValue.clone();
            }

            if(vfColorValue != null) {
               info.vfColorValue = (DynamicValue) vfColorValue.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone GaugeVSAssemblyInfo", ex);
      }

      return null;
   }

   /*
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);
      list.add(vfColorValue);
      return list;
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

   private DynamicValue vfColorValue = new DynamicValue(null, XSchema.COLOR);
   private DynamicValue labelVisibleValue = new DynamicValue("true", XSchema.BOOLEAN);
   private static final Logger LOG = LoggerFactory.getLogger(GaugeVSAssemblyInfo.class);
   private String descriptionName;
}
