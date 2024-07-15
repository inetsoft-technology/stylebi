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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.aesthetic.TextFrame;
import inetsoft.graph.internal.GDefaults;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.css.CSSConstants;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * This descriptor keeps the information of axis.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class AxisDescriptor implements Cloneable, Serializable, XMLSerializable, ContentObject {
   /**
    * Create an AxisDescriptor.
    */
   public AxisDescriptor() {
      lineColor = ChartLineColor.getAxisLineColor(GDefaults.DEFAULT_LINE_COLOR);
      fmt = new CompositeTextFormat();

      initDefaultFormat();
      fmt.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_LABELS);
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      TextFormat deffmt = fmt.getDefaultFormat();
      deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR);
      deffmt.setFont(vs ? VSAssemblyInfo.getDefaultFont(VSUtil.getDefaultFont()) :
                        VSUtil.getDefaultFont());
   }

   public void setAxisCSS(String axisCss) {
      fmt.getCSSFormat().addCSSAttribute("axis", axisCss);

      for(String col : getColumnLabelTextFormatColumns()) {
         CompositeTextFormat colFmt = getColumnLabelTextFormat(col);

         if(colFmt != null) {
            colFmt.getCSSFormat().addCSSAttribute("axis", axisCss);
         }
      }
   }

   /**
    * Check if the axis label is visible.
    */
   public boolean isLabelVisible() {
      return labelVisible;
   }

   /**
    * Set the visibility of the axis label.
    */
   public void setLabelVisible(boolean labelVisible) {
      this.labelVisible = labelVisible;
   }

   /**
    * Get the text format for an axis label.
    * @return the text format.
    */
   public CompositeTextFormat getAxisLabelTextFormat() {
      return fmt;
   }

   /**
    * Get the columns in the format map.
    */
   public Set<String> getColumnLabelTextFormatColumns() {
      return fmtMap != null ? fmtMap.keySet() : new HashSet<>(0);
   }

   /**
    * Get the text format for an axis column.
    * @return the text format.
    */
   public CompositeTextFormat getColumnLabelTextFormat(String col) {
      return fmtMap != null ? fmtMap.get(col) : null;
   }

   /**
    * Set the text format for an axis label.
    */
   public void setAxisLabelTextFormat(CompositeTextFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * Set the text format for an axis label.
    */
   public void setColumnLabelTextFormat(String col, CompositeTextFormat fmt) {
      if(fmtMap == null) {
         fmtMap = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
      }

      fmtMap.put(col, fmt);
   }

   /**
    * Get the increment.
    * @return the increment.
    */
   public Number getIncrement() {
      return increment;
   }

   /**
    * Set the increment.
    * @param increment the increment.
    */
   public void setIncrement(Number increment) {
      this.increment = increment;
   }

   /**
    * Set the line color.
    * @param lineColor the line color.
    */
   public void setLineColor(Color lineColor) {
      this.lineColor = lineColor;
   }

   /**
    * Get the line color.
    */
   public Color getLineColor() {
      return lineColor;
   }

   /**
    * Get the maximum number.
    * @return the maximum number.
    */
   public Number getMaximum() {
      return maximum;
   }

   /**
    * Set the maximum number.
    */
   public void setMaximum(Number maximum) {
      this.maximum = maximum;
   }

   /**
    * Get the minimum number.
    * @return the minimum number.
    */
   public Number getMinimum() {
      return minimum;
   }

   /**
    * Set the minimum number.
    * @param minimum the minimum number.
    */
   public void setMinimum(Number minimum) {
      this.minimum = minimum;
   }

   /**
    * Get the minor increment.
    * @return the minor increment.
    */
   public Number getMinorIncrement() {
      return minorInc;
   }

   /**
    * Set the minor increment.
    * @param minorInc the minor increment.
    */
   public void setMinorIncrement(Number minorInc) {
      this.minorInc = minorInc;
   }

   /**
    * Get the fixed axis width.
    * @return axis width in pixels if the value is greater than 0.
    */
   public double getAxisWidth() {
      return fixedWidth;
   }

   /**
    * Set the fixed axis width.
    */
   public void setAxisWidth(double w) {
      this.fixedWidth = w;
   }

   /**
    * Get the fixed axis height.
    * @return axis height in pixels if the value is greater than 0.
    */
   public double getAxisHeight() {
      return fixedHeight;
   }

   /**
    * Set the fixed axis height.
    */
   public void setAxisHeight(double h) {
      this.fixedHeight = h;
   }

   /**
    * Check if the line is visible.
    */
   public boolean isLineVisible() {
      return lineVisible;
   }

   /**
    * Set the visibility of the line.
    */
   public void setLineVisible(boolean lineVisible) {
      this.lineVisible = lineVisible;
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
    * Check whether the scale is reversed (from largest to smallest).
    */
   public boolean isReversed() {
      return reversed;
   }

   /**
    * Set whether to ignore null value on categorical scale.
    */
   public void setNoNull(boolean nonull) {
      this.nonull = nonull;
   }

   /**
    * Check whether to ignore null value on categorical scale.
    */
   public boolean isNoNull() {
      return nonull;
   }

   /**
    * Set whether to allow truncate labels.
    */
   public void setTruncate(boolean truncate) {
      this.truncate = truncate;
   }

   /**
    * Check whether to allow truncate labels.
    */
   public boolean isTruncate() {
      return truncate;
   }

   /**
    * Set if the range (linear) is shared across the entire facet.
    */
   public void setSharedRange(boolean shared) {
      this.shared = shared;
   }

   /**
    * Check if the range (linear) is shared across the entire facet.
    */
   public boolean isSharedRange() {
      return shared;
   }

   /**
    * Check the visibility of the ticks.
    */
   public boolean isTicksVisible() {
      return ticksVisible;
   }

   /**
    * Set the visibility of the ticks.
    */
   public void setTicksVisible(boolean ticksVisible) {
      this.ticksVisible = ticksVisible;
   }

   /**
    * Add the title key value paire.
    */
   public void setLabelAlias(String key, String value) {
      key = key == null ? "" : key;

      if(value != null && value.trim().length() > 0) {
         if(titles == null) {
            titles = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
         }

         titles.put(key, value);
      }
      else if(titles != null) {
         titles.remove(key);
      }
   }

   /**
    * Get the label alias.
    */
   public String getLabelAlias(Object key) {
      key = key == null ? "" : key;
      return titles != null ? titles.get(key) : null;
   }

   /**
    * Get the legend item aliases.
    */
   public TextFrame getTextFrame() {
      DefaultTextFrame frame = new DefaultTextFrame();

      if(titles != null) {
         for(Object key : titles.keySet()) {
            String alias = titles.get(key);

            if("null".equals(key)) {
               key = null;
            }

            frame.setText(key, alias);
         }
      }

      return frame;
   }

   /**
    * Check if the axis label is displayed in max mode.
    */
   public boolean isMaxModeLabelVisible() {
      return maxModeLabelVisible;
   }

   /**
    * Set if the axis label is displayed in max mode.
    */
   public void setMaxModeLabelVisible(boolean maxModeLabelVisible) {
      this.maxModeLabelVisible = maxModeLabelVisible;
   }

   /**
    * Check if the axis line is displayed in max mode.
    */
   public boolean isMaxModeLineVisible() {
      return maxModeLineVisible;
   }

   /**
    * Set if the axis line is displayed in max mode.
    */
   public void setMaxModeLineVisible(boolean maxModeLineVisible) {
      this.maxModeLineVisible = maxModeLineVisible;
   }

   /**
    * Get the gap between the label and the axis line.
    */
   public int getLabelGap() {
      return labelGap.get();
   }

   /**
    * Set the gap between the label and the axis line.
    * @param labelGap label gap size
    */
   public void setLabelGap(int labelGap) {
      setLabelGap(labelGap, CompositeValue.Type.USER);
   }

   /**
    * Set the gap between the label and the axis line.
    * @param labelGap label gap size
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setLabelGap(int labelGap, CompositeValue.Type type) {
      this.labelGap.setValue(labelGap, type);
   }

   /**
    * Check if the axis this descriptor according to is visible.
    */
   public boolean isVisible() {
      return lineVisible || ticksVisible;
   }

   public void resetCompositeValues(CompositeValue.Type type) {
      labelGap.resetValue(type);

      if(type == CompositeValue.Type.CSS) {
         getAxisLabelTextFormat().getCSSFormat().setRotation(null);
      }
      else if(type == CompositeValue.Type.USER) {
         getAxisLabelTextFormat().getUserDefinedFormat().setRotation(null, false);
      }

      for(String col : getColumnLabelTextFormatColumns()) {
         CompositeTextFormat colFmt = getColumnLabelTextFormat(col);

         if(colFmt != null) {
            if(type == CompositeValue.Type.CSS) {
               colFmt.getCSSFormat().setRotation(null);
            }
            else if(type == CompositeValue.Type.USER) {
               colFmt.getUserDefinedFormat().setRotation(null, false);
            }
         }
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
    * Check if the contents are equal.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof AxisDescriptor)) {
         return false;
      }

      AxisDescriptor desc = (AxisDescriptor) obj;

      return Tool.equals(increment, desc.increment) &&
         Tool.equals(lineColor, desc.lineColor) &&
         Tool.equals(maximum, desc.maximum) &&
         Tool.equals(minimum, desc.minimum) &&
         Tool.equals(minorInc, desc.minorInc) &&
         Tool.equals(fmt, desc.fmt) &&
         Tool.equals(titles, desc.titles) &&
         Tool.equals(fmtMap, desc.fmtMap) &&
         lineVisible == desc.lineVisible &&
         labelVisible == desc.labelVisible &&
         logScale == desc.logScale &&
         reversed == desc.reversed &&
         nonull == desc.nonull &&
         truncate == desc.truncate &&
         shared == desc.shared &&
         ticksVisible == desc.ticksVisible &&
         fixedWidth == desc.fixedWidth &&
         fixedHeight == desc.fixedHeight &&
         maxModeLabelVisible == desc.maxModeLabelVisible &&
         maxModeLineVisible == desc.maxModeLineVisible &&
         Tool.equals(labelGap, desc.labelGap);
   }

   /**
    * Parse xml.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      parseAttributes(node);
      parseContents(node);
   }

   /**
    * Write xml.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<axisDescriptor ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</axisDescriptor>");
   }

   /**
    * Parse attributes.
    */
   protected void parseAttributes(Element node) {
      String val;

      val = Tool.getAttribute(node, "increment");

      if(val != null) {
         increment = Double.valueOf(val);
      }

      val = Tool.getAttribute(node, "lineColor");

      if(val != null) {
         lineColor = new Color(Integer.parseInt(val));
      }

      val = Tool.getAttribute(node, "maximum");

      if(val != null) {
         maximum = Double.valueOf(val);
      }

      val = Tool.getAttribute(node, "minimum");

      if(val != null) {
         minimum = Double.valueOf(val);
      }

      val = Tool.getAttribute(node, "minorInc");

      if(val != null) {
         minorInc = Double.valueOf(val);
      }

      if((val = Tool.getAttribute(node, "fixedWidth")) != null) {
         fixedWidth = Double.parseDouble(val);
      }

      if((val = Tool.getAttribute(node, "fixedHeight")) != null) {
         fixedHeight = Double.parseDouble(val);
      }

      val = Tool.getAttribute(node, "lineVisible");

      if(val != null) {
         lineVisible = "true".equals(val);
      }

      val = Tool.getAttribute(node, "logScale");

      if(val != null) {
         logScale = "true".equals(val);
      }

      val = Tool.getAttribute(node, "reversed");

      if(val != null) {
         reversed = "true".equals(val);
      }

      val = Tool.getAttribute(node, "nonull");

      if(val != null) {
         nonull = "true".equals(val);
      }

      val = Tool.getAttribute(node, "truncate");

      if(val != null) {
         truncate = "true".equals(val);
      }

      val = Tool.getAttribute(node, "shared");

      if(val != null) {
         shared = "true".equals(val);
      }

      val = Tool.getAttribute(node, "ticksVisible");

      if(val != null) {
         ticksVisible = "true".equals(val);
      }

      val = Tool.getAttribute(node, "axisLblVisible");

      if(val != null) {
         labelVisible = "true".equals(val);
      }

      val = Tool.getAttribute(node, "maxModeLabelVisible");

      if(val != null) {
         maxModeLabelVisible = "true".equals(val);
      }
      else {
         maxModeLabelVisible = isLabelVisible();
      }

      val = Tool.getAttribute(node, "maxModeLineVisible");

      if(val != null) {
         maxModeLineVisible = "true".equals(val);
      }
      else {
         maxModeLineVisible = isLineVisible();
      }

      val = Tool.getAttribute(node, "labelGap");

      if(val != null) {
         labelGap.parse(val);
      }

      strictNull = "true".equalsIgnoreCase(Tool.getAttribute(node, "strictNull"));
   }

   /**
    * Parse contents.
    */
   protected void parseContents(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "axisLblFmt");

      if(node != null) {
         fmt = new CompositeTextFormat();
         fmt.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS);
//         fmt.getCSSFormat().addCSSAttribute("axis", csstype)
         fmt.parseXML(Tool.getFirstChildNode(node));
      }

      node = Tool.getChildNodeByTagName(elem, "titles");

      if(node != null) {
         NodeList anodes = Tool.getChildNodesByTagName(node, "title");

         for(int i = 0; i < anodes.getLength(); i++) {
            Element anode = (Element) anodes.item(i);
            Element knode = Tool.getNthChildNode(anode, 0);
            String key = Tool.getValue(knode);
            Element vnode = Tool.getNthChildNode(anode, 1);
            String value = Tool.getValue(vnode);

            if(strictNull && CoreTool.FAKE_NULL.equals(key) || !strictNull && "null".equals(key)) {
               key = null;
            }

            setLabelAlias(key, value);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "formats");

      if(node != null) {
         NodeList anodes = Tool.getChildNodesByTagName(node, "format");

         for(int i = 0; i < anodes.getLength(); i++) {
            Element anode = (Element) anodes.item(i);
            Element knode = Tool.getNthChildNode(anode, 0);
            String key = Tool.getValue(knode);
            Element vnode = Tool.getNthChildNode(anode, 1);
            CompositeTextFormat fmt = new CompositeTextFormat();
            fmt.parseXML(vnode);
            setColumnLabelTextFormat(key, fmt);
         }
      }

      node = Tool.getChildNodeByTagName(elem, "axises");

      if(node != null) {
         NodeList anodes = Tool.getChildNodesByTagName(node, "axis");

         for(int i = 0; i < anodes.getLength(); i++) {
            Element anode = (Element) anodes.item(i);
            Element knode = Tool.getNthChildNode(anode, 0);
            String key = Tool.getValue(knode);
            Element vnode = Tool.getNthChildNode(anode, 1);
            AxisDescriptor axis = new AxisDescriptor();
            axis.parseXML(vnode);
         }
      }
   }

   /**
    * Write attributes.
    */
   protected void writeAttributes(PrintWriter writer) {
      if(increment != null) {
         writer.print(" increment=\"" + increment.doubleValue() + "\" ");
      }

      if(lineColor != null) {
         writer.print(" lineColor=\"" + lineColor.getRGB() + "\" ");
      }

      if(maximum != null) {
         writer.print(" maximum=\"" + maximum.doubleValue() + "\" ");
      }

      if(minimum != null) {
         writer.print(" minimum=\"" + minimum.doubleValue() + "\" ");
      }

      if(minorInc != null) {
         writer.print(" minorInc=\"" + minorInc.doubleValue() + "\" ");
      }

      writer.print(" lineVisible=\"" + lineVisible + "\" ");
      writer.print(" logScale=\"" + logScale + "\" ");
      writer.print(" reversed=\"" + reversed + "\" ");
      writer.print(" nonull=\"" + nonull + "\" ");
      writer.print(" truncate=\"" + truncate + "\" ");
      writer.print(" shared=\"" + shared + "\" ");
      writer.print(" ticksVisible=\"" + ticksVisible + "\" ");
      writer.print(" axisLblVisible=\"" + labelVisible + "\" ");
      writer.print(" fixedWidth=\"" + fixedWidth + "\" ");
      writer.print(" fixedHeight=\"" + fixedHeight + "\" ");
      writer.print(" maxModeLabelVisible=\"" + maxModeLabelVisible + "\" ");
      writer.print(" maxModeLineVisible=\"" + maxModeLineVisible + "\" ");
      writer.print(" labelGap=\"" + labelGap + "\" ");
      writer.print(" strictNull=\"true\" ");
   }

   /**
    * Write contents.
    */
   protected void writeContents(PrintWriter writer) {
      if(fmt != null) {
         writer.print("<axisLblFmt>");
         fmt.writeXML(writer);
         writer.print("</axisLblFmt>");
      }

      writer.print("<titles>");

      if(titles != null) {
         Iterator<Object> keys = titles.keySet().iterator();

         while(keys.hasNext()) {
            Object key = keys.next();
            key = key == null ? CoreTool.FAKE_NULL : key;
            String title = titles.get(key);
            writer.print("<title>");
            writer.print("<key>");
            writer.print("<![CDATA[" + key + "]]>");
            writer.println("</key>");
            writer.print("<value>");
            writer.print("<![CDATA[" + title + "]]>");
            writer.println("</value>");
            writer.print("</title>");
         }
      }

      writer.print("</titles>");

      writer.print("<formats>");

      if(fmtMap != null) {
         Iterator<String> keys = fmtMap.keySet().iterator();

         while(keys.hasNext()) {
            String key = keys.next();
            CompositeTextFormat format = fmtMap.get(key);
            writer.print("<format>");
            writer.print("<key>");
            writer.print("<![CDATA[" + key + "]]>");
            writer.println("</key>");
            format.writeXML(writer);
            writer.print("</format>");
         }
      }

      writer.print("</formats>");
   }

   /**
    * Clone the object.
    */
   @Override
   public AxisDescriptor clone() {
      try {
         AxisDescriptor desc = (AxisDescriptor) super.clone();

         if(increment != null) {
            desc.increment = increment.doubleValue();
         }

         if(lineColor != null) {
            desc.lineColor = new Color(lineColor.getRGB());
         }

         if(maximum != null) {
            desc.maximum = maximum.doubleValue();
         }

         if(minimum != null) {
            desc.minimum = minimum.doubleValue();
         }

         if(minorInc != null) {
            desc.minorInc = minorInc.doubleValue();
         }

         if(fmt != null) {
            desc.fmt = (CompositeTextFormat) fmt.clone();
         }

         if(fmtMap != null) {
            desc.fmtMap = Object2ObjectMaps
               .synchronize(new Object2ObjectOpenHashMap<>());
            Tool.deepCloneMapValues(fmtMap, desc.fmtMap);
         }

         if(titles != null) {
            desc.titles = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
            Tool.deepCloneMapValues(titles, desc.titles);
         }

         return desc;
      }
      catch(Exception e) {
         LOG.error("Failed to clone AxisDescriptor", e);
         return null;
      }
   }

   private Number increment;
   private Number maximum;
   private Number minimum;
   private Number minorInc;
   private boolean logScale = false;
   private boolean reversed = false;
   private boolean shared = true;
   private boolean strictNull = true;
   private Color lineColor;
   private boolean lineVisible = true;
   private boolean ticksVisible = false;
   private boolean labelVisible = true;
   private boolean maxModeLabelVisible = true;
   private boolean maxModeLineVisible = true;
   private boolean nonull = false;
   private boolean truncate = true;
   private CompositeTextFormat fmt;
   private Map<Object, String> titles;
   private Map<String, CompositeTextFormat> fmtMap;
   private double fixedWidth = 0;
   private double fixedHeight = 0;
   private CompositeValue<Integer> labelGap = new CompositeValue<>(Integer.class, 0);

   private static final Logger LOG = LoggerFactory.getLogger(AxisDescriptor.class);
}
