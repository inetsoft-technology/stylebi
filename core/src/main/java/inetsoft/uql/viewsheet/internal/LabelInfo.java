/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class stores the label information for input VS assembly components.
 * Labels can be positioned relative to the input (top, bottom, left, right).
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
public class LabelInfo implements AssetObject {
   /**
    * Label position constant for placing label above the input.
    */
   public static final String TOP = "top";

   /**
    * Label position constant for placing label below the input.
    */
   public static final String BOTTOM = "bottom";

   /**
    * Label position constant for placing label to the left of the input.
    */
   public static final String LEFT = "left";

   /**
    * Label position constant for placing label to the right of the input.
    */
   public static final String RIGHT = "right";

   /**
    * Default gap between label and input in pixels.
    */
   public static final int DEFAULT_GAP = 5;

   /**
    * Default constructor.
    */
   public LabelInfo() {
      super();

      this.labelText = new DynamicValue2("", XSchema.STRING);
      this.labelVisible = new DynamicValue2("false", XSchema.BOOLEAN);
      this.labelPosition = LEFT;
      this.labelGap = new DynamicValue2(DEFAULT_GAP + "", XSchema.INTEGER);
   }

   /**
    * Constructor with label text.
    * @param text the label text value.
    */
   public LabelInfo(String text) {
      super();

      this.labelText = new DynamicValue2(text, XSchema.STRING);
      this.labelVisible = new DynamicValue2("true", XSchema.BOOLEAN);
      this.labelPosition = LEFT;
      this.labelGap = new DynamicValue2(DEFAULT_GAP + "", XSchema.INTEGER);
   }

   /**
    * Get the runtime label text value.
    * @return the runtime label text.
    */
   public String getLabelText() {
      Object val = labelText.getRuntimeValue(true);
      return val == null ? "" : val.toString();
   }

   /**
    * Set the runtime label text value.
    * @param text the runtime label text.
    */
   public void setLabelText(String text) {
      this.labelText.setRValue(text);
   }

   /**
    * Get the design time label text value.
    * @return the design time label text.
    */
   public String getLabelTextValue() {
      return labelText.getDValue();
   }

   /**
    * Set the design time label text value.
    * @param text the design time label text.
    */
   public void setLabelTextValue(String text) {
      this.labelText.setDValue(text);
   }

   /**
    * Check whether label is visible at runtime.
    * @return true if label is visible, otherwise false.
    */
   public boolean isLabelVisible() {
      return labelVisible.getBooleanValue(false, false);
   }

   /**
    * Set the runtime label visible value.
    * @param visible true if label is visible, otherwise false.
    */
   public void setLabelVisible(boolean visible) {
      labelVisible.setRValue(visible);
   }

   /**
    * Check whether label is visible at design time.
    * @return true if label is visible, otherwise false.
    */
   public boolean getLabelVisibleValue() {
      return labelVisible.getBooleanValue(true, false);
   }

   /**
    * Set the design time label visible value.
    * @param visible true if label is visible, otherwise false.
    */
   public void setLabelVisibleValue(String visible) {
      labelVisible.setDValue(visible);
   }

   /**
    * Get the label position relative to the input.
    * @return the label position (top, bottom, left, right).
    */
   public String getLabelPosition() {
      return labelPosition;
   }

   /**
    * Set the label position relative to the input.
    * @param position the label position (top, bottom, left, right).
    */
   public void setLabelPosition(String position) {
      if(TOP.equals(position) || BOTTOM.equals(position) ||
         LEFT.equals(position) || RIGHT.equals(position))
      {
         this.labelPosition = position;
      }
      else {
         this.labelPosition = LEFT;
      }
   }

   /**
    * Get the runtime gap between label and input in pixels.
    * @return the gap in pixels.
    */
   public int getLabelGap() {
      return labelGap.getIntValue(false, getLabelGapValue());
   }

   /**
    * Set the runtime gap between label and input in pixels.
    * @param gap the gap in pixels.
    */
   public void setLabelGap(int gap) {
      gap = Math.max(0, gap);
      labelGap.setRValue(gap);
   }

   /**
    * Get the design time gap between label and input in pixels.
    * @return the gap in pixels.
    */
   public int getLabelGapValue() {
      return labelGap.getIntValue(true, DEFAULT_GAP);
   }

   /**
    * Set the design time gap between label and input in pixels.
    * @param gap the gap in pixels.
    */
   public void setLabelGapValue(int gap) {
      gap = Math.max(0, gap);
      labelGap.setDValue(gap + "");
   }

   /**
    * Get the label format for styling.
    * @return the label format, or null if not set.
    */
   public VSCompositeFormat getLabelFormat() {
      return labelFormat;
   }

   /**
    * Set the label format for styling.
    * @param format the label format.
    */
   public void setLabelFormat(VSCompositeFormat format) {
      this.labelFormat = format;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param vs the viewsheet.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      VSUtil.renameDynamicValueDepended(oname, nname, labelText, vs);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<labelInfo class=\"" + getClass().getName() + "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</labelInfo>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "labelInfo");

      if(node != null) {
         parseAttributes(node);
         parseContents(node);
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" labelVisible=\"" + isLabelVisible() + "\"");
      writer.print(" labelVisibleValue=\"" + getLabelVisibleValue() + "\"");
      writer.print(" labelPosition=\"" + getLabelPosition() + "\"");
      writer.print(" labelGap=\"" + getLabelGap() + "\"");
      writer.print(" labelGapValue=\"" + getLabelGapValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      setLabelVisibleValue(Tool.getAttribute(elem, "labelVisibleValue"));

      String position = Tool.getAttribute(elem, "labelPosition");

      if(position != null) {
         setLabelPosition(position);
      }

      String gapStr = Tool.getAttribute(elem, "labelGapValue");

      if(gapStr != null) {
         try {
            setLabelGapValue(Integer.parseInt(gapStr));
         }
         catch(NumberFormatException e) {
            setLabelGapValue(DEFAULT_GAP);
         }
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      String text = getLabelText();

      if(text != null && !text.isEmpty()) {
         writer.print("<labelText>");
         writer.print("<![CDATA[" + text + "]]>");
         writer.println("</labelText>");
      }

      String textValue = getLabelTextValue();

      if(textValue != null && !textValue.isEmpty()) {
         writer.print("<labelTextValue>");
         writer.print("<![CDATA[" + textValue + "]]>");
         writer.println("</labelTextValue>");
      }

      if(labelFormat != null) {
         labelFormat.writeXML(writer);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "labelTextValue");
      setLabelTextValue(node == null ? "" : Tool.getValue(node));

      Element formatNode = Tool.getChildNodeByTagName(elem, "VSCompositeFormat");

      if(formatNode != null) {
         labelFormat = new VSCompositeFormat();
         labelFormat.parseXML(formatNode);
      }
   }

   /**
    * Returns a string representation of the object.
    */
   @Override
   public String toString() {
      return super.toString() + "(text=" + labelText + ", visible=" + labelVisible +
         ", position=" + labelPosition + ")";
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof LabelInfo)) {
         return false;
      }

      LabelInfo info = (LabelInfo) obj;

      return Tool.equals(labelText, info.labelText) &&
         Tool.equals(getLabelText(), info.getLabelText()) &&
         Tool.equals(labelVisible, info.labelVisible) &&
         isLabelVisible() == info.isLabelVisible() &&
         Tool.equals(labelPosition, info.labelPosition) &&
         Tool.equals(labelGap, info.labelGap) &&
         getLabelGap() == info.getLabelGap() &&
         Tool.equals(labelFormat, info.labelFormat);
   }

   /**
    * Returns a hash code value for the object.
    */
   @Override
   public int hashCode() {
      int result = labelText != null ? labelText.hashCode() : 0;
      result = 31 * result + (labelVisible != null ? labelVisible.hashCode() : 0);
      result = 31 * result + (labelPosition != null ? labelPosition.hashCode() : 0);
      result = 31 * result + (labelGap != null ? labelGap.hashCode() : 0);
      return result;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         LabelInfo info = (LabelInfo) super.clone();
         info.labelText = (DynamicValue2) labelText.clone();
         info.labelVisible = (DynamicValue2) labelVisible.clone();
         info.labelGap = (DynamicValue2) labelGap.clone();

         if(labelFormat != null) {
            info.labelFormat = (VSCompositeFormat) labelFormat.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone LabelInfo", ex);
      }

      return null;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   public List<DynamicValue> getViewDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(labelText);
      return list;
   }

   /**
    * Reset runtime values.
    */
   public void resetRuntimeValues() {
      labelText.setRValue(null);
      labelVisible.setRValue(null);
      labelGap.setRValue(null);
   }

   private DynamicValue2 labelText;
   private DynamicValue2 labelVisible;
   private String labelPosition;
   private DynamicValue2 labelGap;
   private VSCompositeFormat labelFormat;

   private static final Logger LOG = LoggerFactory.getLogger(LabelInfo.class);
}
