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

import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GDefaults;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * LegendDescriptor is a bean that holds the specified attributes of the
 * legends area of a chart in viewsheet. It encapsulates the content format
 * information.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class LegendDescriptor implements AssetObject, ContentObject {
   /**
    * Create a new instance of LegendDescriptor.
    */
   public LegendDescriptor() {
      fmt = new CompositeTextFormat();
      initDefaultFormat();
      fmt.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_CONTENT);
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
    * Check if the legend is displayed.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set if the legend is displayed.
    */
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Check if the legend is displayed in max mode.
    */
   public boolean isMaxModeVisible() {
      return maxModeVisible;
   }

   /**
    * Set if the legend is displayed in max mode.
    */
   public void setMaxModeVisible(boolean maxModeVisible) {
      this.maxModeVisible = maxModeVisible;
   }

   /**
    * Check if the legend title is displayed.
    */
   public boolean isTitleVisible() {
      return titleVisible;
   }

   /**
    * Set if the legend title is displayed.
    */
   public void setTitleVisible(boolean titleVisible) {
      this.titleVisible = titleVisible;
   }

   /**
    * Check if the logarithm scale.
    */
   public boolean isLogarithmicScale() {
      return logScale;
   }

   /**
    * Set the logarithm scale.
    */
   public void setLogarithmicScale(boolean logScale) {
      this.logScale = logScale;
   }

   /**
    * Set whether the scale should be reversed (from largest to smallest).
    */
   public void setReversed(boolean reversed) {
      this.reversed = reversed;
   }

   /**
    * Set whether the legend would show null.
    */
   public void setNotShowNull(boolean notShowNull) {
      this.notShowNull = notShowNull;
   }

   /**
    * Check whether the scale is reversed (from largest to smallest).
    */
   public boolean isReversed() {
      return reversed;
   }

   /**
    * Check whether the legend would show null.
    */
   public boolean isNotShowNull() {
      return notShowNull;
   }

   public boolean isIncludeZero() {
      return includeZero;
   }

   public void setIncludeZero(boolean includeZero) {
      this.includeZero = includeZero;
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
    * Get the position for each chart legend, if x/y is between 0-1, treat it
    * as proportion, otherwise treat it as really position.
    */
   public Point2D getPosition() {
      return position;
   }

   /**
    * Set the position for each chart legend.
    */
   public void setPosition(Point2D pos) {
      this.position = pos;
   }

   /**
    * Set the exact position for each chart legend.
    */
   public void setPlotPosition(Point2D plotPos) {
      this.plotPos = plotPos;
   }

   /**
    * Get the exact position for each chart legend, if x/y is between 0-1,
    * treat it as proportion, this proportion is the distance from plot area not
    * from the whole chart, otherwise treat it as really position.
    */
   public Point2D getPlotPosition() {
      return plotPos;
   }

   /**
    * Get the title content.
    */
   public String getTitle() {
      Object str = title.getRuntimeValue(true);
      return (str == null) ? null : str + "";
   }

   /**
    * Set the title content.
    */
   public void setTitle(String title) {
      this.title.setRValue(title);
   }

   /**
    * Get the title content.
    */
   public String getTitleValue() {
      return title.getDValue();
   }

   /**
    * Set the title content.
    */
   public void setTitleValue(String title) {
      this.title.setDValue(title);
   }

   /**
    * Set color.
    */
   public void setColor(Color color) {
      getContentTextFormat().setColor(color);
   }

   /**
    * Get color.
    */
   public Color getColor() {
      return getContentTextFormat().getColor();
   }

   /**
    * Set font.
    */
   public void setFont(Font font) {
      getContentTextFormat().setFont(font);
   }

   /**
    * Get font.
    */
   public Font getFont() {
      return getContentTextFormat().getFont();
   }

   /**
    * Set format.
    */
   public void setFormat(XFormatInfo format) {
      getContentTextFormat().setFormat(format);
   }

   /**
    * Get font.
    */
   public XFormatInfo getFormat() {
      return getContentTextFormat().getFormat();
   }

   /**
    * Get the text format for the content area in legends.
    */
   public CompositeTextFormat getContentTextFormat() {
      return fmt;
   }

   /**
    * Set the text format for the content area in legends.
    */
   public void setContentTextFormat(CompositeTextFormat fmt0) {
      this.fmt = fmt0;
   }

   /**
    * Add the title key value pair.
    */
   public void setLabelAlias(String key, String value) {
      if(value != null && value.trim().length() > 0) {
         if(labelmap == null) {
            labelmap = new Object2ObjectOpenHashMap<>();
         }

         labelmap.put(key, value);
      }
      else if(labelmap != null) {
         labelmap.remove(key);
      }
   }

   /**
    * Get the label alias.
    */
   public String getLabelAlias(String key) {
      return labelmap != null ? labelmap.get(key) : null;
   }

   /**
    * Clear the label alias.
    */
   public void clearLabelAlias() {
      labelmap = null;
   }

   public Set<String> getAliasedLabels() {
      return labelmap != null ? labelmap.keySet() : new HashSet<>(0);
   }

   /**
    * Get the legend item aliases.
    */
   public TextFrame getTextFrame() {
      DefaultTextFrame frame = new DefaultTextFrame();

      if(labelmap != null) {
         for(Map.Entry<String, String> entry : labelmap.entrySet()) {
            Object key = entry.getKey();
            String alias = entry.getValue();

            if("null".equals(key)) {
               key = null;
            }

            frame.setText(key, alias);
         }
      }

      return frame;
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         LegendDescriptor des = (LegendDescriptor) super.clone();

         if(fmt != null) {
            des.fmt = (CompositeTextFormat) fmt.clone();
         }

         if(labelmap != null) {
            des.labelmap = Tool.deepCloneMap(labelmap);
         }

         if(preferredSize != null) {
            des.preferredSize = (Dimension2D) preferredSize.clone();
         }

         if(position != null) {
            des.position = (Point2D) position.clone();
         }

         if(plotPos != null) {
            des.plotPos = (Point2D) plotPos.clone();
         }

         if(title != null) {
            des.title = (DynamicValue) title.clone();
         }

         return des;
      }
      catch(Exception exc) {
         LOG.error("Failed to clone LegendDescriptor", exc);
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
      if(!(obj instanceof LegendDescriptor)) {
         return false;
      }

      LegendDescriptor des = (LegendDescriptor) obj;

      if(preferredSize != null && des.preferredSize != null &&
         !Tool.equals(preferredSize.getHeight(), des.preferredSize.getHeight()) &&
         !Tool.equals(preferredSize.getWidth(), des.preferredSize.getWidth()))
      {
         return false;
      }

      if(!Tool.equals(position, des.position)) {
         return false;
      }

      if(!Tool.equals(plotPos, des.plotPos)) {
         return false;
      }

      if(!Tool.equals(title, des.title)) {
         return false;
      }

      if(!Tool.equals(fmt, des.fmt)) {
         return false;
      }

      if(!Tool.equals(labelmap, des.labelmap)) {
         return false;
      }

      return logScale == des.logScale && reversed == des.reversed &&
         visible == des.visible && notShowNull == des.notShowNull &&
         titleVisible == des.titleVisible && maxModeVisible == des.maxModeVisible;
   }

   /**
    * Write xml representation.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<legendDescriptor");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</legendDescriptor>");
   }

   /**
    * Write attributes to a XML segment.
    */
   protected void writeAttributes(PrintWriter writer) {
      if(preferredSize != null) {
         writer.print(" preferredSize=\"" + preferredSize.getWidth() +
                         "," + preferredSize.getHeight() + "\"");
      }

      if(position != null) {
         writer.print(" position=\"" + position.getX() + "," +
                         position.getY() + "\"");
      }

      if(plotPos != null) {
         writer.print(" plotPos=\"" + plotPos.getX() + "," + plotPos.getY() +
                         "\"");
      }

      writer.print(" logScale=\"" + logScale + "\" ");
      writer.print(" reversed=\"" + reversed + "\" ");
      writer.print(" visible=\"" + visible + "\" ");
      writer.print(" maxModeVisible=\"" + maxModeVisible + "\" ");
      writer.print(" titleVisible=\"" + titleVisible + "\" ");
      writer.print(" notShowNull=\"" + notShowNull + "\" ");
      writer.print(" includeZero=\"" + includeZero + "\" ");
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   protected void writeContents(PrintWriter writer) {
      if(fmt != null) {
         fmt.writeXML(writer);
      }

      if(title.getDValue() != null) {
         writer.print("<title>");
         writer.print("<![CDATA[" + title.getDValue() + "]]>");
         writer.println("</title>");
      }

      if(getTitle() != null) {
         writer.print("<titleRValue>");
         writer.print("<![CDATA[" + getTitle() + "]]>");
         writer.print("</titleRValue>");
      }

      writer.print("<labels>");

      if(labelmap != null) {
         for(Map.Entry<String, String> entry : labelmap.entrySet()) {
            Object key = entry.getKey();
            String label = entry.getValue();
            writer.print("<label>");
            writer.print("<key>");
            writer.print("<![CDATA[" + key + "]]>");
            writer.println("</key>");
            writer.print("<value>");
            writer.print("<![CDATA[" + label + "]]>");
            writer.println("</value>");
            writer.print("</label>");
         }
      }

      writer.print("</labels>");
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

      if((val = Tool.getAttribute(tag, "preferredSize")) != null) {
         try {
            preferredSize = new DimensionD(
               Double.parseDouble(val.substring(0, val.indexOf(','))),
               Double.parseDouble(val.substring(val.indexOf(',') + 1)));
         }
         catch(Exception ex) {
            LOG.error("Failed to parse preferredSize: " + val, ex);
         }
      }

      if((val = Tool.getAttribute(tag, "position")) != null) {
         try {
            position = new Point2D.Double(
               Double.parseDouble(val.substring(0, val.indexOf(','))),
               Double.parseDouble(val.substring(val.indexOf(',') + 1)));
         }
         catch(Exception ex) {
            LOG.error("Failed to parse position: " + val, ex);
         }
      }

      if((val = Tool.getAttribute(tag, "plotPos")) != null) {
         try {
            plotPos = new Point2D.Double(
               Double.parseDouble(val.substring(0, val.indexOf(','))),
               Double.parseDouble(val.substring(val.indexOf(',') + 1)));
         }
         catch(Exception ex) {
            LOG.error("Failed to parse plotPos: " + val, ex);
         }
      }

      val = Tool.getAttribute(tag, "logScale");

      if(val != null) {
         logScale = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "reversed");

      if(val != null) {
         reversed = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "visible");

      if(val != null) {
         visible = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "maxModeVisible");

      if(val != null) {
         maxModeVisible = "true".equals(val);
      }
      else {
         maxModeVisible = visible;
      }

      val = Tool.getAttribute(tag, "titleVisible");

      if(val != null) {
         titleVisible = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "notShowNull");

      if(val != null) {
         notShowNull = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "includeZero");

      if(val != null) {
         includeZero = "true".equals(val);
      }
   }

   /**
    * Parse the content part(child node) of XML segment.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "compositeTextFormat");

      if(node != null) {
         fmt = new CompositeTextFormat();
         fmt.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_CONTENT);
         fmt.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "title");

      if(node != null) {
         // support empty title
         setTitleValue(Tool.getValue(node, false, false, true));
      }

      node = Tool.getChildNodeByTagName(tag, "labels");

      if(node != null) {
         NodeList anodes = Tool.getChildNodesByTagName(node, "label");

         for(int i = 0; i < anodes.getLength(); i++) {
            Element anode = (Element) anodes.item(i);
            Element knode = Tool.getNthChildNode(anode, 0);
            String key = Tool.getValue(knode);
            Element vnode = Tool.getNthChildNode(anode, 1);
            String value = Tool.getValue(vnode);
            setLabelAlias(key, value);
         }
      }
   }

   /**
    * A descriptor contains multi legend descriptor, will not perfistent.
    */
   public static class LegendArray extends LegendDescriptor {
      public LegendArray(LegendDescriptor[] legends) {
         this.legends = legends;
      }

      @Override
      public boolean isVisible() {
         return legends[legends.length - 1].isVisible();
      }

      @Override
      public void setVisible(boolean visible) {
         for(LegendDescriptor legend : legends) {
            legend.setVisible(visible);
         }
      }

      @Override
      public boolean isTitleVisible() {
         return legends[legends.length - 1].isTitleVisible();
      }

      @Override
      public void setTitleVisible(boolean titleVisible) {
         for(LegendDescriptor legend : legends) {
            legend.setTitleVisible(titleVisible);
         }
      }

      @Override
      public boolean isLogarithmicScale() {
         return legends[legends.length - 1].isLogarithmicScale();
      }

      @Override
      public void setLogarithmicScale(boolean logScale) {
         for(LegendDescriptor legend : legends) {
            legend.setLogarithmicScale(logScale);
         }
      }

      @Override
      public boolean isNotShowNull() {
         return legends[legends.length - 1].isNotShowNull();
      }

      @Override
      public void setNotShowNull(boolean notShowNull) {
         for(LegendDescriptor legend : legends) {
            legend.setNotShowNull(notShowNull);
         }
      }

      @Override
      public void setReversed(boolean reversed) {
         for(LegendDescriptor legend : legends) {
            legend.setReversed(reversed);
         }
      }

      @Override
      public boolean isReversed() {
         return legends[legends.length - 1].isReversed();
      }

      @Override
      public boolean isIncludeZero() {
         return legends[legends.length - 1].isIncludeZero();
      }

      @Override
      public void setIncludeZero(boolean includeZero) {
         for(LegendDescriptor legend : legends) {
            legend.setIncludeZero(includeZero);
         }
      }

      @Override
      public Dimension2D getPreferredSize() {
         return legends[legends.length - 1].getPreferredSize();
      }

      @Override
      public void setPreferredSize(Dimension2D size) {
         for(LegendDescriptor legend : legends) {
            legend.setPreferredSize(size);
         }
      }

      @Override
      public Point2D getPosition() {
         return legends[legends.length - 1].getPosition();
      }

      @Override
      public void setPosition(Point2D pos) {
         for(LegendDescriptor legend : legends) {
            legend.setPosition(pos);
         }
      }

      @Override
      public String getTitle() {
         return legends[legends.length - 1].getTitle();
      }

      @Override
      public void setTitle(String title) {
         for(LegendDescriptor legend : legends) {
            legend.setTitle(title);
         }
      }

      @Override
      public String getTitleValue() {
         return legends[legends.length - 1].getTitleValue();
      }

      @Override
      public void setTitleValue(String title) {
         for(LegendDescriptor legend : legends) {
            legend.setTitleValue(title);
         }
      }

      @Override
      public void setColor(Color color) {
         for(LegendDescriptor legend : legends) {
            legend.setColor(color);
         }
      }

      @Override
      public Color getColor() {
         return getContentTextFormat().getColor();
      }

      @Override
      public void setFont(Font font) {
         for(LegendDescriptor legend : legends) {
            legend.setFont(font);
         }
      }

      @Override
      public Font getFont() {
         return getContentTextFormat().getFont();
      }

      @Override
      public void setFormat(XFormatInfo format) {
         for(LegendDescriptor legend : legends) {
            legend.setFormat(format);
         }
      }

      @Override
      public XFormatInfo getFormat() {
         return getContentTextFormat().getFormat();
      }

      @Override
      public CompositeTextFormat getContentTextFormat() {
         return legends[legends.length - 1].getContentTextFormat();
      }

      @Override
      public void setContentTextFormat(CompositeTextFormat fmt0) {
         for(LegendDescriptor legend : legends) {
            legend.setContentTextFormat(fmt0);
         }
      }

      @Override
      public void setLabelAlias(String key, String value) {
         for(LegendDescriptor legend : legends) {
            legend.setLabelAlias(key, value);
         }
      }

      @Override
      public String getLabelAlias(String key) {
         return legends[legends.length - 1].getLabelAlias(key);
      }

      @Override
      public void clearLabelAlias() {
         for(LegendDescriptor legend : legends) {
            legend.clearLabelAlias();
         }
      }

      @Override
      public TextFrame getTextFrame() {
         return legends[legends.length - 1].getTextFrame();
      }

      private LegendDescriptor[] legends;
   }

   /**
    * Get the dynamic property values.
    */
   public List<DynamicValue> getDynamicValues() {
      ArrayList<DynamicValue> list = new ArrayList<>();
      list.add(title);
      return list;
   }

   private Dimension2D preferredSize = null;
   private Point2D position = null;
   private Point2D plotPos = null;
   private DynamicValue title = new DynamicValue();
   private CompositeTextFormat fmt;
   private Map<String, String> labelmap;
   private boolean logScale = false;
   private boolean reversed = false;
   private boolean visible = true;
   private boolean maxModeVisible = true;
   private boolean titleVisible = true;
   private boolean notShowNull = false;
   private boolean includeZero = false;

   private static final Logger LOG = LoggerFactory.getLogger(LegendDescriptor.class);
}
