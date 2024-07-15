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
package inetsoft.uql.asset;

import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;

/**
 * ValueRangeInfo holds value range definition. Each range is defined by
 * the lower and upper bounds.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ValueRangeInfo implements AssetObject, DataSerializable {
   /**
    * Constructor.
    */
   public ValueRangeInfo() {
      super();

      values = new double[0];
      labels = new String[0];
   }

   /**
    * Get the range boundary values.
    * @return the values.
    */
   public double[] getValues() {
      return values;
   }

   /**
    * Set the value ranges. Values are divided into n+1 ranges by the values
    * in the list. The values must be sorted.
    * @param values range boundary values.
    */
   public void setValues(double[] values) {
      this.values = values == null ? new double[0] : values;
   }

   /**
    * Get the labels.
    * @return the labels.
    */
   public String[] getLabels() {
      return labels;
   }

   /**
    * Set the ranges' labels.
    * @param labels range boundary labels.
    */
   public void setLabels(String[] labels) {
      this.labels = labels == null ? new String[0] : labels;
   }

   /**
    * If show the bottom value.
    * @return if show the bottom value.
    */
   public boolean isShowBottomValue() {
      return showingBottomValue;
   }

   /**
    * Set if show the bottom value.
    * @param bottom if show the bottom value.
    */
   public void setShowBottomValue(boolean bottom) {
      this.showingBottomValue = bottom;
   }

   /**
    * If show the top value.
    * @return if show the top value.
    */
   public boolean isShowTopValue() {
      return showingTopValue;
   }

   /**
    * Set if show the top value.
    * @param top if show the top value.
    */
   public void setShowTopValue(boolean top) {
      this.showingTopValue = top;
   }

   /**
    * Check if upper bound is inclusive.
    */
   public InclusiveType getInclusiveType() {
      return inclusiveType;
   }

   /**
    * Set if upper bound is inclusize.
    * @param inclusiveType Inclusivity type one of {@link InclusiveType}
    */
   public void setInclusiveType(InclusiveType inclusiveType) {
      this.inclusiveType = inclusiveType;
   }

   /**
    * Get the string representation.
    * @return the string representaion.
    */
   public String toString() {
      return "[" + Tool.arrayToString(values) + ", " +
         Tool.arrayToString(labels) + ", " + showingBottomValue +
         ", " + showingTopValue + ", " + inclusiveType + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ValueRangeInfo info = (ValueRangeInfo) super.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<valueRangeInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</valueRangeInfo>");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(values != null && values.length > 0) {
         writer.print("<values>");

         for(int i = 0; i < values.length; i++) {
            writer.print("<value>");
            writer.print("<![CDATA[" + values[i] + "]]>");
            writer.print("</value>");
         }

         writer.println("</values>");
      }

      if(labels != null && labels.length > 0) {
         writer.print("<labels>");

         for(int i = 0; i < labels.length; i++) {
            if(labels[i] != null) {
               writer.print("<label>");
               writer.print("<![CDATA[" + labels[i] + "]]>");
               writer.print("</label>");
            }
            else {
               writer.print("<label/>");
            }
         }

         writer.println("</labels>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element valuesNode = Tool.getChildNodeByTagName(elem, "values");

      if(valuesNode != null) {
         NodeList valuesList =
            Tool.getChildNodesByTagName(valuesNode, "value");

         if(valuesList != null && valuesList.getLength() > 0) {
            values = new double[valuesList.getLength()];

            for(int i = 0; i < valuesList.getLength(); i++) {
               values[i] =
                  Double.parseDouble(Tool.getValue(valuesList.item(i)));
            }
         }
      }

      Element labelsNode = Tool.getChildNodeByTagName(elem, "labels");

      if(labelsNode != null) {
         NodeList labelsList =
            Tool.getChildNodesByTagName(labelsNode, "label");

         if(labelsList != null && labelsList.getLength() > 0) {
            labels = new String[labelsList.getLength()];

            for(int i = 0; i < labelsList.getLength(); i++) {
               String label = Tool.getValue(labelsList.item(i));
               labels[i] = label != null ? label : null;
            }
         }
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" showingBottomValue=\"" + showingBottomValue + "\"");
      writer.print(" showingTopValue=\"" + showingTopValue + "\"");
      writer.print(" inclusiveType=\"" + inclusiveType + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      showingBottomValue = "true".equals(Tool.getAttribute(elem, "showingBottomValue"));
      showingTopValue = "true".equals(Tool.getAttribute(elem, "showingTopValue"));
      inclusiveType = "NONE".equals(Tool.getAttribute(elem, "inclusiveType")) ?
         InclusiveType.NONE : "UPPER".equals(Tool.getAttribute(elem, "inclusiveType")) ?
         InclusiveType.UPPER : InclusiveType.LOWER;
   }

   /**
    * Write data to a DataOutputStream.
    * @param dos the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      try {
         dos.writeBoolean(showingBottomValue);
         dos.writeBoolean(showingTopValue);
         dos.writeChars(inclusiveType + "");
         dos.writeInt(values.length);

         for(int i = 0; i < values.length; i++) {
            dos.writeUTF(values[i] + "");
         }

         dos.writeInt(labels.length);

         for(int i = 0; i < labels.length; i++) {
            dos.writeUTF(labels[i] != null ? labels[i] + "" : "");
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to serialize data", ex);
      }
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      //do nothing
      return true;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object to compare to.
    * @return <tt>true</tt> if equals the specified object, <tt>false</tt>
    * otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ValueRangeInfo)) {
         return false;
      }

      ValueRangeInfo vinfo = (ValueRangeInfo) obj;

      if(values.length != vinfo.values.length ||
         showingBottomValue != vinfo.showingBottomValue ||
         showingTopValue != vinfo.showingTopValue ||
         inclusiveType != vinfo.inclusiveType)
      {
         return false;
      }

      for(int i = 0; i < values.length; i++) {
         if(DataComparer.compare(values[i], vinfo.values[i]) != 0) {
            return false;
         }
      }

      return true;
   }

   private double[] values;
   private String[] labels;
   private boolean showingBottomValue = true;
   private boolean showingTopValue = true;
   private InclusiveType inclusiveType = InclusiveType.LOWER;

   private static final Logger LOG = LoggerFactory.getLogger(ValueRangeInfo.class);
}
