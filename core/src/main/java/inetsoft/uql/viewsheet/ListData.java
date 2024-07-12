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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.text.Format;
import java.util.Arrays;
import java.util.Objects;

/**
 * ListData contains the embedded label data and value data for list assemblies.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ListData implements AssetObject {
   /**
    * Constructor.
    */
   public ListData() {
      super();

      this.dtype = XSchema.STRING;
      this.labels = new String[0];
      this.values = new Object[0];
      this.formats = new VSCompositeFormat[0];
   }

   /**
    * Set query data start point.
    */
   public void setQueryDataIndex(int qidx) {
      this.qindex = qidx;
   }

   /**
    * Get query data start index.
    */
   public int getQueryDataIndex() {
      return qindex;
   }

   /**
    * Get the labels.
    * @return the labels of this list data.
    */
   public String[] getLabels() {
      return labels;
   }

   /**
    * Set the labels to this list data.
    * @param labels the specified labels.
    */
   public void setLabels(String[] labels) {
      this.labels = labels == null ? new String[0] : labels;
   }

   /**
    * Get the formats.
    * @return the formats of this list data.
    */
   public VSCompositeFormat[] getFormats() {
      return formats;
   }

   /**
    * Set the formats to this list data.
    * @param formats the specified formats.
    */
   public void setFormats(VSCompositeFormat[] formats) {
      this.formats = formats == null ? new VSCompositeFormat[0] : formats;
   }

   /**
    * Get the default cell formats.
    */
   public Format[] getDefaultFormats() {
      return dfmts;
   }

   /**
    * Set default cell formats.
    */
   public void setDefaultFormats(Format[] dfmts) {
      this.dfmts = dfmts;
   }

   /**
    * Check if this binding is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return values.length > 0;
   }

   /**
    * Check if is binding list data.
    */
   public boolean isBinding() {
      return binding;
   }

   /**
    * Set whether is binding list data.
    */
   public void setBinding(boolean binding) {
      this.binding = binding;
   }

   /**
    * Get the data type.
    * @return the data type of this list data.
    */
   public String getDataType() {
      return dtype;
   }

   /**
    * Set the data type to this list data.
    * @param dtype the specified data type.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype;
   }

   /**
    * Get the values.
    * @return the values of this list data.
    */
   public Object[] getValues() {
      return values;
   }

   /**
    * Set the values to this list data.
    * @param values the specified values.
    */
   public void setValues(Object[] values) {
      this.values = values == null ? new Object[0] : values;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<listData class=\"" + getClass().getName()+ "\" strictNull=\"true\">");
      writeContents(writer);
      writer.print("</listData>");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      writer.print("<dataType>");
      writer.print("<![CDATA[" + dtype + "]]>");
      writer.println("</dataType>");

      for(int i = 0; i < labels.length; i++) {
         writer.print("<label>");
         writer.print("<![CDATA[" + Tool.getPersistentDataString(labels[i]) + "]]>");
         writer.print("</label>");
      }

      for(int i = 0; i < values.length; i++) {
         writer.print("<value>");
         writer.print("<![CDATA[" + Tool.getPersistentDataString(values[i], dtype) +
                      "]]>");
         writer.print("</value>");
      }
   }

   private void parseAttributes(Element elem) throws Exception {
      strictNull = "true".equalsIgnoreCase(Tool.getAttribute(elem, "strictNull"));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      dtype = Tool.getChildValueByTagName(elem, "dataType");

      NodeList lnodes = Tool.getChildNodesByTagName(elem, "label");
      labels = new String[lnodes.getLength()];

      for(int i = 0; i < lnodes.getLength(); i++) {
         Element lnode = (Element) lnodes.item(i);
         String val = Tool.getValue(lnode);
         labels[i] = (String) getPersistentData(XSchema.STRING, val);
      }

      NodeList vnodes = Tool.getChildNodesByTagName(elem, "value");
      values = new Object[vnodes.getLength()];

      for(int i = 0; i < vnodes.getLength(); i++) {
         Element vnode = (Element) vnodes.item(i);
         String val = Tool.getValue(vnode);
         values[i] = getPersistentData(dtype, val);
      }
   }

   public Object getPersistentData(String type, String val) {
      return strictNull ? Tool.getPersistentData(type, val) : Tool.getData(type, val);
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
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "ListData: [" + dtype + ", {" + Arrays.asList(labels) + "}, {" +
             Arrays.asList(values) + "}]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ListData info = (ListData) super.clone();

         if(labels != null) {
            info.labels = labels.clone();
         }

         if(values != null) {
            info.values = values.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ListData", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ListData)) {
         return false;
      }

      ListData data = (ListData) obj;

      return Objects.equals(dtype, data.dtype) &&
         Arrays.equals(labels, data.labels) &&
         Arrays.equals(values, data.values);
   }

   private String dtype;
   private String[] labels;
   private Object[] values;
   private boolean strictNull = true; // for bc
   private transient boolean binding;
   // set query data start index
   private transient int qindex = 0;
   // default table cell format, if not set format for the
   // data, use default format to format datas
   private transient Format[] dfmts;
   // runtime
   private VSCompositeFormat[] formats; // temporary data

   private static final Logger LOG =
      LoggerFactory.getLogger(ListData.class);
}
