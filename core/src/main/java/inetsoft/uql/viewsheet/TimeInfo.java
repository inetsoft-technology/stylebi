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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * TimeInfo contains time information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TimeInfo implements AssetObject {
   /**
    * Date year range.
    */
   public static final int YEAR = 1;
   /**
    * Date month range.
    */
   public static final int MONTH = 2;
   /**
    * Numeric range.
    */
   public static final int NUMBER = 3;
   /**
    * Cube member range.
    */
   public static final int MEMBER = 4;
   /**
    * Date day range.
    */
   public static final int DAY = 16;
   /**
    * Date hour range.
    */
   public static final int HOUR = 17;
   /**
    * Date minute range.
    */
   public static final int MINUTE = 18;
   /**
    * Time hour range.
    */
   public static final int HOUR_OF_DAY = 20;
   /**
    * Time minute range.
    */
   public static final int MINUTE_OF_DAY = 21;

   /**
    * Constructor.
    */
   public TimeInfo() {
      super();
   }

   /**
    * Get the length of time information.
    * @return the length of time information.
    */
   public int getLength() {
      return lengthValue.getIntValue(false, 3);
   }

   /**
    * Set the length of time information. For time slider, this is the number
    * of ticks of the sliding button.
    * @param length the length of time information.
    */
   public void setLength(int length) {
      lengthValue.setRValue(length);
   }

   /**
    * Set the length of time information. For time slider, this is the number
    * of ticks of the sliding button.
    * @param length the length of time information.
    */
   public void setLengthValue(int length) {
      lengthValue.setDValue(String.valueOf(length));
   }

   /**
    * Get the length of time information.
    * @return the length of time information.
    */
   public int getLengthValue() {
      return lengthValue.getIntValue(true, 3);
   }

   /**
    * Get the min value.
    */
   public Object getMin() {
      return min;
   }

   /**
    * Set the min value. If the min and max are set, the values are used
    * instead of queried from the data.
    */
   public void setMin(Object min) {
      this.min = min;
   }

   /**
    * Get the max value.
    */
   public Object getMax() {
      return max;
   }

   /**
    * Set the max value. If the max and max are set, the values are used
    * instead of queried from the data.
    */
   public void setMax(Object max) {
      this.max = max;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<timeInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</timeInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" length=\"" + getLength() + "\"");
      writer.print(" lengthValue=\"" + getLengthValue() + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      String txt = Tool.getAttribute(elem, "lengthValue");

      if(txt == null || txt.equals("NaN")) {
         return;
      }

      lengthValue.setDValue(txt);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      // do nothing
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      // do nothing
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         TimeInfo info = (TimeInfo) super.clone();

         info.lengthValue = (DynamicValue2) lengthValue.clone();
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone TimeInfo", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TimeInfo)) {
         return false;
      }

      TimeInfo tinfo = (TimeInfo) obj;
      return Tool.equals(lengthValue, tinfo.lengthValue) &&
         Tool.equals(min, tinfo.min) && Tool.equals(max, tinfo.max);
   }

   /**
    * Check if the binding information has changed.
    */
   public boolean equalsBinding(TimeInfo tinfo) {
      return Tool.equals(min, tinfo.min) && Tool.equals(max, tinfo.max);
   }

   /**
    * Reset runtime values.
    */
   public void resetRuntimeValues() {
      lengthValue.setRValue(null);
      min = null;
      max = null;
   }

   private DynamicValue2 lengthValue = new DynamicValue2(String.valueOf(3), XSchema.INTEGER);
   private Object min, max;

   private static final Logger LOG = LoggerFactory.getLogger(TimeInfo.class);
}
