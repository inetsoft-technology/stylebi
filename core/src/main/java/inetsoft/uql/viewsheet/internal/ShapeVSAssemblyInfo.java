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

import inetsoft.graph.internal.DimensionD;
import inetsoft.report.StyleConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.DynamicValue2;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * ShapeVSAssemblyInfo stores basic shape assembly information.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ShapeVSAssemblyInfo extends VSAssemblyInfo implements BaseAnnotationVSAssemblyInfo {
   /**
    * Constructor.
    */
   public ShapeVSAssemblyInfo() {
      super();
   }

   /**
    * Get the runtime shape's line style.
    */
   public int getLineStyle() {
      return lineStyleValue.getIntValue(false, StyleConstants.THIN_LINE);
   }

   /**
    * Get the design time shape's line style.
    */
   public int getLineStyleValue() {
      return lineStyleValue.getIntValue(true, StyleConstants.THIN_LINE);
   }

   /**
    * Set the runtime shape's line style.
    */
   public void setLineStyle(int style) {
      lineStyleValue.setRValue(style);
   }

   /**
    * Set the desing time shape's line style value.
    */
   public void setLineStyleValue(int style) {
      lineStyleValue.setDValue(style + "");
   }

   /**
    * Set whether to draw drop shadow.
    */
   public void setShadow(boolean shadow) {
      this.shadow.setRValue(shadow);
   }

   /**
    * Check whether to draw drop shadow.
    */
   public boolean isShadow() {
      return "true".equals(shadow.getRuntimeValue(true) + "");
   }

   /**
    * Set whether to draw drop shadow.
    */
   public void setShadowValue(boolean shadow) {
      this.shadow.setDValue(shadow + "");
   }

   /**
    * Check whether to draw drop shadow.
    */
   public boolean getShadowValue() {
      return "true".equals(shadow.getDValue());
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      list.add(shadow);
      return list;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" shadow=\"" + isShadow() + "\"");
      writer.print(" shadowValue=\"" + getShadowValue() + "\"");
      writer.print(" lineStyle=\"" + getLineStyle() + "\"");
      writer.print(" lineStyleValue=\"" + lineStyleValue.getDValue() + "\"");
      writer.print(" islocked=\"" + getLocked() + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      lineStyleValue.setDValue(
         getAttributeStr(elem, "lineStyle", StyleConstants.THIN_LINE + ""));
      shadow.setDValue(Tool.getAttribute(elem, "shadowValue"));
      islocked = "true".equals(Tool.getAttribute(elem, "islocked"));
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(annotations != null && !annotations.isEmpty()) {
         writer.print("<annotations>");

         for(int j = 0; j < annotations.size(); j++) {
            writer.print("<annotation>");
            writer.print("<![CDATA[" + annotations.get(j) + "]]>");
            writer.print("</annotation>");
         }

         writer.println("</annotations>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element anode = Tool.getChildNodeByTagName(elem, "annotations");

      if(anode != null) {
         NodeList alist = Tool.getChildNodesByTagName(anode, "annotation");

         if(alist != null && alist.getLength() > 0) {
            annotations = new ArrayList();

            for(int i = 0; i < alist.getLength(); i++) {
               annotations.add(Tool.getValue(alist.item(i)));
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
      ShapeVSAssemblyInfo cinfo = (ShapeVSAssemblyInfo) info;

      if(!Tool.equals(shadow, cinfo.shadow)) {
         shadow = cinfo.shadow;
         result = true;
      }

      if(islocked != cinfo.islocked) {
         islocked = cinfo.islocked;
         result = true;
      }

      if(!Tool.equals(lineStyleValue, cinfo.lineStyleValue) ||
         getLineStyle() != cinfo.getLineStyle())
      {
         lineStyleValue = cinfo.lineStyleValue;
         result = true;
      }

      return result;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public ShapeVSAssemblyInfo clone(boolean shallow) {
      ShapeVSAssemblyInfo sinfo = (ShapeVSAssemblyInfo) super.clone(shallow);

      if(!shallow) {
         if(shadow != null) {
            sinfo.shadow = (DynamicValue) shadow.clone();
         }

         if(lineStyleValue != null) {
            sinfo.lineStyleValue = (DynamicValue2) lineStyleValue.clone();
         }

         if(scalingRatio != null) {
            sinfo.scalingRatio = (DimensionD) scalingRatio.clone();
         }
      }

      return sinfo;
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      // set shape default background as white
      setDefaultFormat(false, true, true);
      getFormat().getDefaultFormat().setForegroundValue("0x555555");
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      lineStyleValue.setRValue(null);
   }

   /**
    * Get scaling ratio.
    */
   public DimensionD getScalingRatio() {
      return scalingRatio;
   }

   /**
    * Set scaling ratio.
    */
   public void setScalingRatio(DimensionD ratio) {
      this.scalingRatio = ratio;
   }

   /*
    * set the locked to the object, if the locked is true,
    * then the object can not be moved and selected by click.
    */
   public void setLocked(Boolean locked) {
      this.islocked = locked;
   }

   /*
    * get the object whether be locked.
    */
   public Boolean getLocked() {
      return islocked;
   }

   /**
    * Get annotations.
    */
   @Override
   public List<String> getAnnotations() {
      return annotations;
   }

   /**
    * Add a annotation.
    * @param name the specified annotation name.
    */
   @Override
   public void addAnnotation(String name) {
      if(!annotations.contains(name)) {
         annotations.add(name);
      }
   }

   /**
    * Remove the annotation.
    * @param name the specified annotation name.
    */
   @Override
   public void removeAnnotation(String name) {
      annotations.remove(name);
   }

   /**
    * Remove all annotations.
    */
   @Override
   public void clearAnnotations() {
      annotations.clear();
   }

   // view
   private Boolean islocked = false;
   private DynamicValue shadow = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue2 lineStyleValue =
      new DynamicValue2(StyleConstants.THIN_LINE + "", XSchema.INTEGER);
   private DimensionD scalingRatio = new DimensionD(1.0, 1.0);

   private List annotations = new ArrayList();
}
