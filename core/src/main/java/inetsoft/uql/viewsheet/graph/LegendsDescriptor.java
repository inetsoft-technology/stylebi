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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GDefaults;
import inetsoft.report.StyleConstants;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * LegendsDescriptor is a bean that holds the general attributes of the
 * legends area of a chart.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class LegendsDescriptor implements AssetObject, ContentObject {
   /**
    * No legend will be shown.
    */
   public static final int NO_LEGEND = 0;
   /**
    * The legend will be placed beside the top side of the chart and tiled
    * horizontally.
    */
   public static final int TOP = 1;
   /**
    * The legend will be placed beside the right side of the chart and tiled
    * vertically.
    */
   public static final int RIGHT = 2;
   /**
    * The legend will be placed beside the bottom side of the chart and tiled
    * horizontally.
    */
   public static final int BOTTOM = 3;
   /**
    * The legend will be placed beside the left side of the chart and tiled
    * vertically.
    */
   public static final int LEFT = 4;
   /**
    * The legend will be flaoted on the top of the chart.
    */
   public static final int IN_PLACE = 5;

   /**
    * Create a new instance of LegendsDescriptor.
    */
   public LegendsDescriptor() {
      fmt = new CompositeTextFormat();
      initDefaultFormat();
      fmt.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_TITLE);
      colorDesc = new LegendDescriptor();
      shapeDesc = new LegendDescriptor();
      sizeDesc = new LegendDescriptor();
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      TextFormat deffmt = fmt.getDefaultFormat();
      deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR);
      deffmt.setFont(vs ? VSAssemblyInfo.getDefaultFont(VSUtil.getDefaultFont()) :
                        VSUtil.getDefaultFont());
      deffmt.setBackground(Color.WHITE);
      deffmt.setAlpha(50);
   }

   /**
    * Get the color used by the legends border.
    */
   public Color getBorderColor() {
      return this.borderColor.get();
   }

   /**
    * Set the color used to present the legends border.
    * @param color border color
    */
   public void setBorderColor(Color color) {
      setBorderColor(color, true);
   }

   /**
    * Set the color used to present the legends border.
    * @param color border color
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setBorderColor(Color color, boolean force) {
      if(force || !Tool.equals(color, getBorderColor())) {
         setBorderColor(color, CompositeValue.Type.USER);
      }
   }

   /**
    * Set the color used to present the legends border.
    * @param color border color
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setBorderColor(Color color, CompositeValue.Type type) {
      this.borderColor.setValue(color, type);
   }

   /**
    * Get border of the legends.
    */
   public int getBorder() {
      return this.border.get();
   }

   /**
    * Set border of the legends.
    * @param i border line style
    */
   public void setBorder(int i) {
      setBorder(i, true);
   }

   /**
    * Set border of the legends.
    * @param i border line style
    * @param force if true always sets the USER value; otherwise only sets the USER value if
    *              it's different from the current value
    */
   public void setBorder(int i, boolean force) {
      if(force || i != getBorder()) {
         setBorder(i, CompositeValue.Type.USER);
      }
   }

   /**
    * Set border of the legends.
    * @param i border line style
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setBorder(int i, CompositeValue.Type type) {
      this.border.setValue(i, type);
   }

   /**
    * Get the legends layout option.
    */
   public int getLayout() {
      return this.option;
   }

   /**
    * Set the legends layout option.
    */
   public void setLayout(int opt) {
      this.option = opt;
   }

   /**
    * Get a legend descriptor for the color field.
    * @return a legend descriptor, <code>null</code> If the color legend don't
    * need to show.
    */
   public LegendDescriptor getColorLegendDescriptor() {
      return this.colorDesc;
   }

   /**
    * Set a legend descriptor for the color field.
    */
   public void setColorLegendDescriptor(LegendDescriptor desc) {
      this.colorDesc = desc;
   }

   /**
    * Get a legend descriptor for the shape or texture field.
    * @return a legend descriptor, <code>null</code> If the shape, texture or
    * line legend don't need to show.
    */
   public LegendDescriptor getShapeLegendDescriptor() {
      return this.shapeDesc;
   }

   /**
    * Set a legend descriptor for the shape or texture field.
    */
   public void setShapeLegendDescriptor(LegendDescriptor desc) {
      this.shapeDesc = desc;
   }

   /**
    * Get a legend descriptor for the size field.
    * @return a legend descriptor, <code>null</code> If the size legend don't
    * need to show.
    */
   public LegendDescriptor getSizeLegendDescriptor() {
      return this.sizeDesc;
   }

   /**
    * Set a legend descriptor for the size field.
    */
   public void setSizeLegendDescriptor(LegendDescriptor desc) {
      this.sizeDesc = desc;
   }

   /**
    * Get the preferred size for legends if set by the user, if the width/height
    * is between 0-1, will treat it as proportion, otherwise treat it as really
    * size.
    * @return the preferred size, <code>null</code> if no size is set.
    */
   public Dimension2D getPreferredSize() {
      return preferredSize;
   }

   /**
    * Set the preferred size for legends.
    */
   public void setPreferredSize(Dimension2D size) {
      this.preferredSize = size;
   }

   /**
    * Get the text format for the title area in legends.
    */
   public CompositeTextFormat getTitleTextFormat() {
      return fmt;
   }

   /**
    * Set the text format for the title area in legends.
    */
   public void setTitleTextFormat(CompositeTextFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * Get the count of the legends.
    */
   public int getLegendCount() {
      int count = 0;

      if(colorDesc != null) {
         count++;
      }

      if(shapeDesc != null) {
         count++;
      }

      if(sizeDesc != null) {
         count++;
      }

      return count;
   }

   /**
    * Get the gap between the legend and the axis/plot
    */
   public int getGap() {
      return gap.get();
   }

   /**
    * Set the gap between the legend and the axis/plot
    * @param gap gap size
    */
   public void setGap(int gap) {
      setGap(gap, CompositeValue.Type.USER);
   }

   /**
    * Set the gap between the legend and the axis/plot
    * @param gap gap size
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setGap(int gap, CompositeValue.Type type) {
      this.gap.setValue(gap, type);
   }

   /**
    * Get the legend item/title padding
    */
   public Insets getPadding() {
      return padding.get();
   }

   /**
    * Set the legend item/title padding
    * @param padding padding
    */
   public void setPadding(Insets padding) {
      setPadding(padding, CompositeValue.Type.USER);
   }

   /**
    * Set the legend item/title padding
    * @param padding padding
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setPadding(Insets padding, CompositeValue.Type type) {
      this.padding.setValue(padding, type);
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         LegendsDescriptor des = (LegendsDescriptor) super.clone();

         if(fmt != null) {
            des.fmt = (CompositeTextFormat) fmt.clone();
         }

         if(colorDesc != null) {
            des.colorDesc = (LegendDescriptor) colorDesc.clone();
         }

         if(shapeDesc != null) {
            des.shapeDesc = (LegendDescriptor) shapeDesc.clone();
         }

         if(sizeDesc != null) {
            des.sizeDesc = (LegendDescriptor) sizeDesc.clone();
         }

         des.borderColor = (CompositeValue<Color>) borderColor.clone();
         des.border = (CompositeValue<Integer>) border.clone();
         des.gap = (CompositeValue<Integer>) gap.clone();
         des.padding = (CompositeValue<Insets>) padding.clone();

         return des;
      }
      catch(Exception exc) {
         LOG.error("Failed to clone LegendsDescriptor", exc);
         return null;
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof LegendsDescriptor)) {
         return false;
      }

      LegendsDescriptor desc = (LegendsDescriptor) obj;

      return Tool.equals(fmt, desc.fmt) &&
         Tool.equalsContent(colorDesc, desc.colorDesc)
         && Tool.equalsContent(sizeDesc, desc.sizeDesc)
         && Tool.equalsContent(shapeDesc, desc.shapeDesc)
         && Tool.equals(borderColor, desc.borderColor)
         && Tool.equals(border, desc.border)
         && option == desc.option
         && Tool.equals(preferredSize, desc.preferredSize)
         && Tool.equals(gap, desc.gap)
         && Tool.equals(padding, desc.padding);
   }

   /**
    * Write xml representation.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<legendsDescriptor");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</legendsDescriptor>");
   }

   /**
    * Write attributes to a XML segment.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" borderColor=\"" + borderColor + "\"");
      writer.print(" border=\"" + border + "\"");
      writer.print(" option=\"" + option + "\"");

      if(preferredSize != null) {
         writer.print(" preferredSize=\"" + preferredSize.getWidth() +
                      "," + preferredSize.getHeight() + "\"");
      }

      writer.print(" gap=\"" + gap + "\"");
      writer.print(" padding=\"" + padding + "\"");
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   protected void writeContents(PrintWriter writer) {
      if(fmt != null) {
         fmt.writeXML(writer);
      }

      if(colorDesc != null) {
         writer.println("<colorLegendDescriptor>");
         colorDesc.writeXML(writer);
         writer.println("</colorLegendDescriptor>");
      }

      if(shapeDesc != null) {
         writer.println("<shapeLegendDescriptor>");
         shapeDesc.writeXML(writer);
         writer.println("</shapeLegendDescriptor>");
      }

      if(sizeDesc != null) {
         writer.println("<sizeLegendDescriptor>");
         sizeDesc.writeXML(writer);
         writer.println("</sizeLegendDescriptor>");
      }
   }

   /**
    * Method to parse an xml segment about parameter element information.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Parse attributes to an XML segment.
    */
   protected void parseAttributes(Element tag) throws Exception {
      String val;

      if((val = Tool.getAttribute(tag, "borderColor")) != null) {
         borderColor.parse(val);
      }

      if((val = Tool.getAttribute(tag, "border")) != null) {
         border.parse(val);
      }

      if((val = Tool.getAttribute(tag, "option")) != null) {
         option = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(tag, "preferredSize")) != null) {
         try {
            preferredSize = new DimensionD(
               Double.valueOf(val.substring(0, val.indexOf(','))).doubleValue(),
               Double.valueOf(val.substring(val.indexOf(',') + 1)).doubleValue());
         }
         catch(Exception e) {
         }
      }

      if((val = Tool.getAttribute(tag, "gap")) != null) {
         gap.parse(val);
      }

      if((val = Tool.getAttribute(tag, "padding")) != null) {
         padding.parse(val);
      }
   }

   /**
    * Parse the content part(child node) of XML segment.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "colorLegendDescriptor");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         colorDesc = new LegendDescriptor();
         colorDesc.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "shapeLegendDescriptor");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         shapeDesc = new LegendDescriptor();
         shapeDesc.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "sizeLegendDescriptor");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         sizeDesc = new LegendDescriptor();
         sizeDesc.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "compositeTextFormat");

      if(node != null) {
         fmt = new CompositeTextFormat();
         fmt.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_TITLE);
         fmt.parseXML(node);
      }
   }

   /**
    * Get the dynamic property values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      list.addAll(colorDesc.getDynamicValues());
      list.addAll(shapeDesc.getDynamicValues());
      list.addAll(sizeDesc.getDynamicValues());
      return list;
   }

   public void resetCompositeValues(CompositeValue.Type type) {
      borderColor.resetValue(type);
      border.resetValue(type);
      gap.resetValue(type);
      padding.resetValue(type);
   }
   private CompositeValue<Color> borderColor = new CompositeValue<>(Color.class,
                                                                    GDefaults.DEFAULT_LINE_COLOR);
   private CompositeValue<Integer> border = new CompositeValue<>(Integer.class,
                                                                 StyleConstants.THIN_LINE);
   private LegendDescriptor colorDesc;
   private LegendDescriptor shapeDesc;
   private LegendDescriptor sizeDesc;
   private int option = RIGHT;
   private Dimension2D preferredSize;
   private CompositeTextFormat fmt;
   private CompositeValue<Integer> gap = new CompositeValue<>(Integer.class, 0);
   private CompositeValue<Insets> padding = new CompositeValue<>(Insets.class, null);

   private static final Logger LOG = LoggerFactory.getLogger(LegendsDescriptor.class);
}
