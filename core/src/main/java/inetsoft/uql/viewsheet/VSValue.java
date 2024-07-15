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
package inetsoft.uql.viewsheet;

import inetsoft.graph.visual.ElementVO;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;

/**
 * SelectionValue stores basic value information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSValue implements AssetObject, DataSerializable, Comparable {
   /**
    * Constructor.
    */
   public VSValue() {
      super();
   }

   /**
    * Constructor.
    */
   public VSValue(String value) {
      this(value, value);
   }

   /**
    * Constructor.
    */
   public VSValue(String label, String value) {
      this.label = Tool.validateToXML(label);
      this.value = Tool.validateToXML(value);
   }

   /**
    * Get the label of this viewsheet value.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Set the label to this viewsheet value.
    */
   public void setLabel(String label) {
      this.label = Tool.validateToXML(label);
   }

   /**
    * Get the value of this viewsheet value.
    */
   public String getValue() {
      return value;
   }

   /**
    * Set the value to this viewsheet value.
    */
   public void setValue(String value) {
      this.value = Tool.validateToXML(value);
   }

   /**
    * Get the object.
    * @param dtype the specified data type.
    */
   public Object getObject(String dtype) {
      return Tool.getData(dtype, value);
   }

   /**
    * Check if the value is the predefined all.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isAll() {
      return ElementVO.ALL_PREFIX.equals(value);
   }

   /**
    * Check if the value is a null value.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isNull() {
      return Tool.NULL.equals(value);
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSValue)) {
         return false;
      }

      VSValue vval = (VSValue) obj;
      // only compare the value, because the label may be formatted with
      // different pattern, for example, the format for date type object will
      // be different by the local
      return Tool.equals(value, vval.value);
   }

   @Override
   public int hashCode() {
      return (label != null ? label.hashCode() : 0) + (value != null ? value.hashCode() : 0);
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSValue", ex);
      }

      return null;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public final void writeData(DataOutputStream output) throws IOException {
      writeData(output, Integer.MAX_VALUE, null);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public final void writeData(DataOutputStream output, int levels,
      SelectionList list) throws IOException
   {
      writeData(output, levels, list, true);
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   public final void writeData(DataOutputStream output, int levels,
      SelectionList list, boolean containsFormat) throws IOException
   {
      writeAttributes(output, list);
      writeContents(output, levels, list, containsFormat);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean parseData(DataInputStream input) {
      return true;
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
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    * @param levels the number of levels of nodes to write.
    * @param list the list this value is on.
    */
   public final void writeXML(PrintWriter writer, int levels,
      SelectionList list)
   {
      writer.print("<VSValue class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer, list);
      writer.println(">");
      writeContents(writer, levels, list);
      writer.print("</VSValue>");
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writeXML(writer, Integer.MAX_VALUE, null);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      writeContents(writer, Integer.MAX_VALUE, null);
   }

   /**
    * Write contents.
    */
   protected void writeContents(DataOutputStream output, int levels,
                                SelectionList list) throws IOException
   {
      writeContents(output, levels, list, true);
   }

   /**
    * Write contents.
    */
   protected void writeContents(DataOutputStream output, int levels,
                                SelectionList List, boolean containsFormat)
                                throws IOException
   {
      output.writeBoolean(label == null);

      if(label != null) {
         Tool.writeUTF(output, label);
      }

      output.writeBoolean(value == null || value.equals(label));

      // optimization, avoid writing value if not necessary
      if(value != null && !value.equals(label)) {
         Tool.writeUTF(output, value);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    * @param levels the number of levels of nodes to write.
    * @param list the list this value is on.
    */
   protected void writeContents(PrintWriter writer, int levels,
                                SelectionList list)
   {
      if(label != null) {
         writer.print("<label2>");
         writer.print("<![CDATA[" + Tool.byteEncode(label, true) + "]]>");
         writer.println("</label2>");
      }

      String value0 = value == null ? CoreTool.FAKE_NULL : value;

      // optimization, avoid writing value if not necessary
      if(value0 != null && !value0.equals(label)) {
         writer.print("<value2 strictNull=\"" + strictNull + "\" fakeNull=\"" + (value == null) + "\">");
         writer.print("<![CDATA[" + Tool.byteEncode(value0, true) + "]]>");
         writer.println("</value2>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element labelNode = Tool.getChildNodeByTagName(elem, "label2");

      if(labelNode != null) {
         label = Tool.getValue(labelNode, true);
         label = label == null ? "" : label;
         label = Tool.byteDecode(label);
      }

      Element vnode = Tool.getChildNodeByTagName(elem, "value2");

      if(vnode != null) {
         boolean fakeNull = "true".equals(Tool.getAttribute(vnode, "fakeNull"));
         value = fakeNull ? null : Tool.getValue(vnode, true);
         value = value == null ? null : Tool.byteDecode(value);
         value = "null".equals(value) && !strictNull ? null : value;
      }
      else {
         value = label;
      }

      if(labelNode != null || vnode != null) {
         return;
      }

      // backup
      labelNode = Tool.getChildNodeByTagName(elem, "label");

      if(labelNode != null) {
         label = Tool.getValue(labelNode, true);
         label = label == null ? "" : label;
      }

      vnode = Tool.getChildNodeByTagName(elem, "value");

      if(vnode != null) {
         value = Tool.getValue(vnode, true);
         value = value == null ? "" : value;
      }
      else {
         value = label;
      }
   }

   /**
    * Write attributes.
    */
   protected void writeAttributes(DataOutputStream out, SelectionList list)
      throws IOException
   {
      out.writeBoolean(strictNull);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer, SelectionList list) {
      writer.print(" strictNull=\"" + strictNull + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      strictNull = "true".equalsIgnoreCase(Tool.getAttribute(elem, "strictNull"));
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "Label:" + label + ",Value:" + value;
   }

   /**
    * Compare it with another object.
    */
   @Override
   public int compareTo(Object obj) {
      if(obj == null) {
         return 1;
      }
      else if(!(obj instanceof VSValue)) {
         return 0;
      }

      VSValue val2 = (VSValue) obj;

      if(value == null) {
         return val2.value == null ? 0 : -1;
      }

      return value.compareTo(val2.value);
   }

   private String label;
   private String value;
   private boolean strictNull = true; // for bc

   private static final Logger LOG =
      LoggerFactory.getLogger(VSValue.class);
}
