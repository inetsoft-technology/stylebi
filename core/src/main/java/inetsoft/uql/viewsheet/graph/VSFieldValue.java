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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.VSValue;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * VSFieldValue stores a field value pair.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class VSFieldValue implements AssetObject, Comparable {
   /**
    * Constructor.
    */
   public VSFieldValue() {
      super();
   }

   /**
    * Constructor.
    */
   public VSFieldValue(String name, String val) {
      super();

      this.fieldName = name;
      this.fieldValue = new VSValue(val);
   }

   /**
    * Constructor.
    */
   public VSFieldValue(String name, Object value, boolean strictNull) {
      this(name, Tool.getDataString(value));

      if(strictNull) {
         setNullValueIsObjectNull(value == null);
      }
   }

   /**
    * Create a range value, condition is between val and val2.
    */
   public VSFieldValue(String name, String val, String val2) {
      this(name, val);
      this.fieldValue2 = new VSValue(val2);
   }

   /**
    * Set is string type or not.
    */
   public void setStringData(boolean strType) {
      this.strType = strType;
   }

   /**
    * Check if is string type.
    */
   public boolean isStringData() {
      return strType;
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSFieldValue)) {
         return false;
      }

      VSFieldValue value = (VSFieldValue) obj;

      if(!Tool.equals(fieldName, value.fieldName)) {
         return false;
      }

      if(!Tool.equals(fieldValue, value.fieldValue)) {
         return false;
      }

      if(!Tool.equals(fieldValue2, value.fieldValue2)) {
         return false;
      }

      if(nullValueIsObjectNull != value.nullValueIsObjectNull) {
         return false;
      }

      return true;
   }

   @Override
   public int hashCode() {
      return (fieldName != null ? fieldName.hashCode() : 0) +
         (fieldValue != null ? fieldValue.hashCode() : 0) +
         (fieldValue2 != null ? fieldValue2.hashCode() : 0);
   }

   /**
    * Set the fieldname of this field value.
    * @param name file name
    */
   public void setFieldName(String name) {
      this.fieldName = name;
   }

   /**
    * Get the fieldname for this processor.
    * @return the field name.
    */
   public String getFieldName() {
      return fieldName;
   }

   /**
    * Set the fieldvalue of this.
    * @param value stores basic value information
    */
   public void setFieldValue(VSValue value) {
      this.fieldValue = value;
   }

   /**
    * Get the fieldvalue for this processor.
    * @return the field value.
    */
   public VSValue getFieldValue() {
      return fieldValue;
   }

   /**
    * Set the second fieldvalue for range condition.
    */
   public void setFieldValue2(VSValue value) {
      this.fieldValue2 = value;
   }

   /**
    * Get the second fieldvalue for range condition.
    */
   public VSValue getFieldValue2() {
      return fieldValue2;
   }

   /**
    * Generate the XML segment to represent this field value.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<VSFieldValue class=\"" + getClass().getName() + "\"");

      if(fieldName != null) {
         writer.print(" fieldName =\"" + Tool.escape(fieldName) + "\"");
      }

      if(strType) {
         writer.print(" strType=\"" + strType + "\"");
      }

      writer.println(">");

      if(fieldValue != null) {
         fieldValue.writeXML(writer);
      }

      if(fieldValue2 != null) {
         fieldValue2.writeXML(writer);
      }

      writer.println("</VSFieldValue>");
   }

   /**
    * Parse the XML element that contains information on this
    * fieldvalue.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      fieldName = Tool.getAttribute(tag, "fieldName");
      String s = Tool.getAttribute(tag, "strType");

      if(s != null) {
         strType = true;
      }

      NodeList list = Tool.getChildNodesByTagName(tag, "VSValue");

      for(int i = 0; i < list.getLength(); i++) {
         Element valueNode = (Element) list.item(i);

         if(valueNode != null) {
            if(i == 0) {
               fieldValue = new VSValue();
               fieldValue.parseXML(valueNode);
            }
            else {
               fieldValue2 = new VSValue();
               fieldValue2.parseXML(valueNode);
            }
         }
      }
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         VSFieldValue  obj = (VSFieldValue) super.clone();

         if(fieldValue != null) {
            obj.setFieldValue((VSValue) fieldValue.clone());
         }

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone VSFieldValue", e);
         return null;
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "Field:" + fieldName + ",Value:" + fieldValue;
   }

   /**
    * Compare it with another object.
    */
   @Override
   public int compareTo(Object obj) {
      if(!(obj instanceof VSFieldValue)) {
         return 0;
      }

      int rc = CoreTool.compare(fieldValue, ((VSFieldValue) obj).fieldValue, true, true);

      if(rc == 0) {
         rc = CoreTool.compare(fieldValue2, ((VSFieldValue) obj).fieldValue2, true, true);
      }

      return rc;
   }

   /**
    * Check if value is an object null when value is "null".
    */
   public boolean isNullValueIsObjectNull() {
      return nullValueIsObjectNull;
   }

   /**
    * set if value is an object null when value is "null".
    * @param nullValueIsObjectNull
    */
   public void setNullValueIsObjectNull(boolean nullValueIsObjectNull) {
      this.nullValueIsObjectNull = nullValueIsObjectNull;
   }

   private String fieldName;
   private VSValue fieldValue;
   private VSValue fieldValue2;
   private boolean strType;
   private boolean nullValueIsObjectNull = true;

   private static final Logger LOG = LoggerFactory.getLogger(VSFieldValue.class);
}
