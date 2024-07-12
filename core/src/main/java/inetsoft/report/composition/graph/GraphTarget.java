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
package inetsoft.report.composition.graph;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.form.TargetForm;
import inetsoft.graph.internal.GDefaults;
import inetsoft.report.internal.Util;
import inetsoft.report.io.viewsheet.VSFontHelper;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.graph.TextFormat;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.css.CSSAttr;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.MessageFormat;
import java.text.*;
import java.util.List;
import java.util.*;

/**
 * GraphTarget represents a target value on a chart.
 *
 * @author InetSoft Technology Corp.
 * @since  10.0
 */
public class GraphTarget implements Cloneable, Serializable, XMLSerializable {
   /**
    * Create a new instance of GraphTarget.
    */
   public GraphTarget() {
      super();

      fmt = new CompositeTextFormat();
      initDefaultFormat();
      fmt.getCSSFormat().setCSSType(CSSConstants.CHART_TARGET_LABELS);
      Font fn = SreeEnv.getFont("report.font");

      if(fn != null) {
         fmt.setFont(fn);
      }
   }

   public void initDefaultFormat() {
      initDefaultFormat(false);
   }

   public void initDefaultFormat(boolean vs) {
      TextFormat deffmt = fmt.getDefaultFormat();
      deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR);
      deffmt.setFont(vs ? VSAssemblyInfo.getDefaultFont(VSFontHelper.getDefaultFont()) :
                        VSFontHelper.getDefaultFont());
      deffmt.setRotation(0d);
   }

   /**
    * Builds a graph target object in memory from the xml representation.
    * @param element The XML element to read.
    * @return A GraphTarget object based on the XML.
    */
   public static GraphTarget instantiateFromXML(Element element)
      throws Exception
   {
      GraphTarget newTarget = new GraphTarget();

      newTarget.parseXML(element);
      return newTarget;
   }

   /**
    * Get the field this target is associated with.
    * @return null if the target is global.
    */
   public String getField() {
      return field;
   }

   /**
    * Set the field this target is associated with.
    */
   public void setField(String field) {
      this.field = field;
   }

   /**
    * Get the field this target label is associated with.
    * @return the field label for the target.
    */
   public String getFieldLabel() {
      return this.fieldLabel;
   }

   /**
    * Set the field this target label is associated with.
    */
   public void setFieldLabel(String label) {
      this.fieldLabel = label;
   }

   /**
    * Get the localized field name this target is associated with.
    * @return null if the target is global.
    */
   public String getLocalizedMeasure() {
      return localizedStr;
   }

   /**
    * Set the localized field name this target is associated with.
    */
   public void setLocalizedMeasure(String localstr) {
      this.localizedStr = localstr;
   }

   /**
    * Get the target label.
    * @return the target label.
    */
   public DynamicValue[] getLabelFormats() {
      return labelFormats;
   }

   /**
    * Set the target labels.
    * @param labels the target labels.
    */
   public void setLabelFormats(DynamicValue[] labels) {
      this.labelFormats = labels;
   }

   /**
    * Set the target labels.
    * @param labels the target labels.
    */
   public void setLabelFormats(String[] labels) {
      DynamicValue[] dvs = new DynamicValue[Math.max(2, labels.length)];

      for(int i = 0; i < dvs.length; i++) {
         dvs[i] = new DynamicValue(i < labels.length ? labels[i] : "{0}");
      }

      setLabelFormats(dvs);
   }

   /**
    * Set the target labels.
    * @param label the target labels.
    */
   public void setLabelFormats(String label) {
      this.labelFormats = new DynamicValue[] {
         new DynamicValue(label, XSchema.STRING),
         new DynamicValue("{0}", XSchema.STRING)
      };
   }

   /**
    * Get the target line style.
    * @return the line style.
    */
   public int getLineStyle() {
      return lineStyle;
   }

   /**
    * Set the target line style.
    * @param style the line style.
    */
   public void setLineStyle(int style) {
      this.lineStyle = style;
   }

   /**
    * Get the target line color.
    * @return the color used for the target line and label.
    */
   public Color getLineColor() {
      return lineColor;
   }

   /**
    * Set the target line color.
    * @param color the color used for the target line and label.
    */
   public void setLineColor(Color color) {
      this.lineColor = color;
   }

   /**
    * Get the fill above color.
    * @return the color used for the target fill.
    */
   public Color getFillAbove() {
      return fillAboveColor;
   }

   /**
    * Set the fill above color.
    * @param color the color used for the target fill.
    */
   public void setFillAbove(Color color) {
      fillAboveColor = color;
   }

   /**
    * Get the fill below color.
    * @return the color used for the target fill.
    */
   public Color getFillBelow() {
      return fillBelowColor;
   }

   /**
    * Set the fill below color.
    * @param color the color used for the target fill.
    */
   public void setFillBelow(Color color) {
      fillBelowColor = color;
   }

   /**
   * get the target alpha.
   * @return the alpha used for the target fill.
   */
   public int getAlphaValue() {
      return alphaValue;
   }

   /**
   * set the target alpha.
   * @param alphaValue the alpha used for the target fill.
   */
   public void setAlphaValue(int alphaValue) {
      this.alphaValue = alphaValue;
   }

   /**
    * Get formatting for text.
    */
   public CompositeTextFormat getTextFormat() {
      return fmt;
   }

   /**
    * Set formatting for text.
    */
   public void setTextFormat(CompositeTextFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * Get Color Frame.
    */
   public CategoricalColorFrameWrapper getBandFill() {
      return bandFill;
   }

   /**
    * Set color frame.
    */
   public void setBandFill(CategoricalColorFrameWrapper bandFill) {
      this.bandFill = bandFill;
   }

   /**
    * Get the strategy used to calculate target line values.
    */
   public TargetStrategyWrapper getStrategy() {
      return strategy;
   }

   /**
    * Set the strategy used to calculate target line values.
    */
   public void setStrategy(TargetStrategyWrapper strategy) {
      this.strategy = strategy;
   }

   /**
    * @return whether the scope of the target is the entire chart or just
    * the individual sub-coordinates.
    */
   public boolean isChartScope() {
      return chartScope;
   }

   /**
    * Set whether the scope of the target is the entire chart or just
    * the individual sub-coordinates.
    */
   public void setChartScope(boolean chartScope) {
      this.chartScope = chartScope;
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         GraphTarget obj = (GraphTarget) super.clone();

         if(fmt != null) {
            obj.fmt = (CompositeTextFormat) fmt.clone();
         }

         return obj;
      }
      catch(Exception exc) {
         LOG.error("Failed to clone graph target", exc);
      }

      return null;
   }

   /**
    * Get a String representation of this object.
    * @return the target label.
    */
   public String toString() {
      Catalog catalog = Catalog.getCatalog();
      String str = strategy.getGenericLabel();

      if(field != null || fieldLabel != null) {
         String temp = localizedStr == null || localizedStr.isEmpty() ?
            fieldLabel : localizedStr;
         temp = temp == null || temp.isEmpty() ? field : fieldLabel;
         str += " " + Catalog.getCatalog().getString("of") + " " + temp;
      }

      return str +
         " [" + catalog.getString(Util.getLineStyleName(lineStyle)) + "]";
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<graphTarget");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</graphTarget>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   private void writeAttributes(PrintWriter writer) {
      writer.print(" lineStyle=\"" + lineStyle + "\"");
      writer.print(" scope=\"" + (chartScope ? "all" : "subchart") + "\"");
      writer.print(" alphaValue=\"" + getAlphaValue() + "\"");

      if(field != null) {
         writer.print(" field=\"" + Tool.encodeHTMLAttribute(field) + "\"");
      }

      if(fieldLabel != null) {
         writer.print(
            " fieldLabel=\"" + Tool.encodeHTMLAttribute(fieldLabel) + "\"");
      }

      if(fillAboveColor!= null) {
         writer.print(" fillAbove=\"" + fillAboveColor.getRGB() + "\"");
      }

      if(fillBelowColor != null) {
         writer.print(" fillBelow=\"" + fillBelowColor.getRGB() + "\"");
      }

      if(lineColor != null) {
         writer.print(" lineColor=\"" + lineColor.getRGB() + "\"");
      }

      writer.print(" index=\"" + index + "\"");

      if(dateField) {
         writer.print(" dateField=\"true\"");
      }

      if(timeField) {
         writer.print(" timeField=\"true\"");
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   private void writeContents(PrintWriter writer) {
      if(fmt != null) {
         writer.print("<labelFormat>");
         fmt.writeXML(writer);
         writer.print("</labelFormat>");
      }

      writer.print("<strategy className=\"");
      writer.print(Tool.encodeHTMLAttribute(strategy.getClass().getName()));
      writer.print("\">");
      writer.print(strategy.generateXmlContents());
      writer.print("</strategy>");

      bandFill.writeXML(writer);

      if(labelFormats.length > 0) {
         writer.print("<labelTemplates>");

         for(DynamicValue dv : labelFormats) {
            writer.print("<label><![CDATA[");
            writer.print(dv.getDValue() == null ? "" : dv.getDValue());
            writer.print("]]></label>");
         }

         writer.print("</labelTemplates>");
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem); // Parse common attributes
      parseContents(elem); // Parse common contents
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   private void parseAttributes(Element elem) {
      String prop;

      if((prop = Tool.getAttribute(elem, "lineStyle")) != null) {
         lineStyle = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "scope")) != null) {
         chartScope = prop.equalsIgnoreCase("all");
      }

      field = Tool.getAttribute(elem, "field");
      fieldLabel = Tool.getAttribute(elem, "fieldLabel");

      // Get various color attributes
      if((prop = Tool.getAttribute(elem, "lineColor")) != null) {
         lineColor = new Color(Integer.parseInt(prop));
      }

      if((prop = Tool.getAttribute(elem, "fillAbove")) != null) {
         fillAboveColor = new Color(Integer.parseInt(prop));
      }

      if((prop = Tool.getAttribute(elem, "fillBelow")) != null) {
         fillBelowColor = new Color(Integer.parseInt(prop));
      }

      if((prop =Tool.getAttribute(elem, "alphaValue")) != null) {
         alphaValue = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "index")) != null) {
         index = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "dateField")) != null) {
         dateField = prop.equalsIgnoreCase("true");
      }

      if((prop = Tool.getAttribute(elem, "timeField")) != null) {
         timeField = prop.equalsIgnoreCase("true");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   private void parseContents(Element elem) throws Exception {
      // Label format
      Element node = Tool.getChildNodeByTagName(elem, "labelFormat");

      if(node != null) {
         fmt = new CompositeTextFormat();
         fmt.parseXML(Tool.getFirstChildNode(node));
         HashMap<String, String> attrs = new HashMap<>();
         attrs.put("index", index + "");
         fmt.getCSSFormat().setCSSAttributes(attrs);
      }

      // parse strategy by instantiating a strategy from the class name
      // and calling parseXml() on the xml element
      node = Tool.getChildNodeByTagName(elem, "strategy");

      if(node != null) {
         String stratClassStr = node.getAttribute("className");
         TargetStrategyWrapper strategy = TargetStrategyWrapper.fromClassName(stratClassStr);
         strategy.parseXml(node);
         this.strategy = strategy;
      }

      node = Tool.getChildNodeByTagName(elem, "legendFrame");

      if(node != null) {
         bandFill.parseXML(node);
      }

      // Get label formats
      node = Tool.getChildNodeByTagName(elem, "labelTemplates");

      if(node != null) {
         List<DynamicValue> labels = new ArrayList<>();
         NodeList children = Tool.getChildNodesByTagName(node, "label");

         // Add each label
         for(int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            // changed to use cdata in 12.3
            String val = Tool.getAttribute((Element) child, "value");
            val = val == null ? Tool.getValue(child) : val;
            labels.add(new DynamicValue(val, XSchema.STRING));
         }

         this.labelFormats = labels.toArray(new DynamicValue[0]);
      }
   }

   /**
    * Retrieve all of the dynamic values so they can be executed
    */
   public Collection<DynamicValue> getDynamicValues() {
      Collection<DynamicValue> list = new ArrayList<>(strategy.getDynamicValues());
      Collections.addAll(list, labelFormats);
      return list;
   }

   /**
    * Populate the graph form with the appropriate values which are common to
    * all Graph Targets.
    * @param form The form to populate.
    */
   public final TargetForm initializeForm(TargetForm form, DataSet data) {
      form.setTextSpec(GraphUtil.getTextSpec(fmt, null, null));
      form.setFillAbove(fillAboveColor);
      form.setFillBelow(fillBelowColor);
      form.setAlpha(alphaValue);
      form.setColor(lineColor);
      form.setLine(lineStyle);
      form.setChartScope(chartScope);
      form.setFieldLabel(fieldLabel);
      form.setStrategy(strategy.unwrap());
      form.setBandColorFrame((CategoricalColorFrame) bandFill.getVisualFrame());
      // clip form so a target in one sub-graph won't be drawn in a neighboring
      // subgraph in a facet if it's out of range
      form.setHint(GraphElement.HINT_CLIP, "true");

      if(data instanceof BoxDataSet && data.indexOfHeader(BoxDataSet.MEDIUM_PREFIX + field) >= 0) {
         form.setField(BoxDataSet.MEDIUM_PREFIX + field);
      }
      else {
         form.setField(field);
      }

      DynamicValue[] labelFormats = this.labelFormats;

      if(strategy instanceof DynamicLineWrapper) {
         // if target line, don't use the second label format. this is for
         // the case the target value expends into multiple values in runtime
         if(((DynamicLineWrapper) strategy).getParameters().length == 1) {
            labelFormats = new DynamicValue[] { labelFormats[0] };
         }
      }

      form.setLabelFormats(unwrapLabels(labelFormats));
      form.setDateTarget(isDateField());
      form.setTimeTarget(isTimeField());
      form.setMeasure(field);

      return form;
   }

   /**
    * Set flag indicating this is a "Date" field.
    */
   public void setDateField(final boolean dateFieldFlag) {
      this.dateField = dateFieldFlag;
   }

   /**
    * Return flag indicating this is a "Date" field.
    */
   public boolean isDateField() {
      return dateField;
   }

   /**
    * Return flag indicating this is a time field.
    */
   public boolean isTimeField() {
      return timeField;
   }

   /**
    * Set flag indicating this is a time field.
    */
   public void setTimeField(boolean timeField) {
      this.timeField = timeField;
   }

   /**
    * Handles splitting labels by commas and ",," escape sequences
    */
   private MessageFormat[] decodeLabel(String rawLabel) {
      if(rawLabel == null) {
         rawLabel = "";
      }

      StringBuilder builder = new StringBuilder();
      List<MessageFormat> tokens = new ArrayList<>();
      boolean lastCharWasBackslash = false;

      for(int i = 0; i < rawLabel.length(); i++) {
         char c = rawLabel.charAt(i);

         if(c == '\\') {
            if(lastCharWasBackslash) {
               // We found an escape sequence, just add a comma
               builder.append(c);
               lastCharWasBackslash = false;
            }
            else {
               lastCharWasBackslash = true;
            }
         }
         else if(c == ',') {
            if(lastCharWasBackslash) {
               builder.append(c);
            }
            else {
               tokens.add(createMessageFormat(builder.toString().trim()));
               builder = new StringBuilder();
            }
         }
         else {
            // Add character to current string
            builder.append(c);
            lastCharWasBackslash = false;
         }
      }

      // Add final token
      tokens.add(createMessageFormat(builder.toString().trim()));
      MessageFormat[] formats = new MessageFormat[tokens.size()];
      return tokens.toArray(formats);
   }

   // Get runtime values of the label formats.
   private MessageFormat[] unwrapLabels(DynamicValue[] labelFormats) {
      List<MessageFormat> formats = new ArrayList<>();

      for(DynamicValue labelFormat : labelFormats) {
         Object rval = labelFormat.getRuntimeValue(false);

         if(rval instanceof Object[]) {
            Object[] arr = (Object[]) rval;

            for(Object item : arr) {
               String label = Tool.getStringData(item);
               label = label == null ? "" : label;
               formats.add(createMessageFormat(label.trim()));
            }
         }
         else {
            String label = Tool.getStringData(rval);
            label = label == null ? "" : label;
            formats.add(createMessageFormat(label.trim()));
         }
      }

      return formats.toArray(new MessageFormat[0]);
   }

   /**
    * Create a text format.
    */
   private MessageFormat createMessageFormat(String fmtStr) {
      MessageFormat fmt = new java.text.MessageFormat(fmtStr);
      int i = 0;

      for(Format fm : fmt.getFormats()) {
         if(fm instanceof DecimalFormat) {
            String subpattern = ((DecimalFormat)fm).toPattern();
            fmt.setFormat(i, new ExtendedDecimalFormat(subpattern));
         }
         else if(fm instanceof SimpleDateFormat) {
            String subpattern = ((SimpleDateFormat)fm).toPattern();
            fmt.setFormat(i, new ExtendedDateFormat(subpattern));
         }

         i++;
      }

      return fmt;
   }

   public void setIndex(int index) {
      this.index = index;
      fmt.getCSSFormat().setCSSAttributes(new CSSAttr("index", index + ""));
   }

   public int getIndex() {
      return this.index;
   }

   private String field;
   private String fieldLabel;
   private int lineStyle = GraphConstants.THIN_LINE;
   private DynamicValue[] labelFormats = new DynamicValue[] {
      new DynamicValue("{0}", XSchema.STRING),
      new DynamicValue("{0}", XSchema.STRING)};
   private String localizedStr;
   private Color lineColor = GDefaults.DEFAULT_TARGET_LINE_COLOR;
   private Color fillAboveColor;
   private Color fillBelowColor;
   private int alphaValue = 100;
   private boolean chartScope = false; // T for all, F for subchart
   private TargetStrategyWrapper strategy = new DynamicLineWrapper();
   private CompositeTextFormat fmt;
   private int index;
   private boolean dateField = false;
   private boolean timeField = false;

   private CategoricalColorFrameWrapper bandFill = new CategoricalColorFrameWrapper();

   {
      CategoricalColorFrame color = new CategoricalColorFrame();
      color.setColor(0, new Color(0xe4f2e9));
      color.setColor(1, new Color(0xd8e5dd));
      color.setColor(2, new Color(0xccd9d0));
      color.setColor(3, new Color(0xc0ccc4));
      bandFill.setVisualFrame(color);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(GraphTarget.class);
}
