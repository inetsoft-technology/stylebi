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
package inetsoft.uql.asset;

import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Source info contains the source information of a <tt>BoundTableAssembly<tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SourceInfo implements XSourceInfo, AssetObject {
   /**
    * Constructor.
    */
   public SourceInfo() {
      super();

      this.prop = new Properties();
   }

   /**
    * Constructor.
    */
   public SourceInfo(int type, String prefix, String source) {
      this();

      this.type = type;
      this.prefix = prefix;
      this.source = source;
   }

   /**
    * Get the type.
    * @return the type of the source info.
    */
   @Override
   public int getType() {
      return type;
   }

   /**
    * Set the type.
    * @param type the specified type.
    */
   @Override
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the prefix.
    * @return the prefix of the source info.
    */
   @Override
   public String getPrefix() {
      return prefix;
   }

   /**
    * Set the prefix.
    * @param prefix the specified prefix.
    */
   @Override
   public void setPrefix(String prefix) {
      this.prefix = prefix;
   }

   /**
    * Get the source.
    * @return the source of the source info.
    */
   @Override
   public String getSource() {
      return source;
   }

   /**
    * Set the source.
    * @param source the specified source.
    */
   @Override
   public void setSource(String source) {
      this.source = source;
   }

   /**
    * Get a property of the source info.
    * @param key the name of the property.
    * @return the value of the property.
    */
   @Override
   public String getProperty(String key) {
      return prop.getProperty(key);
   }

   /**
    * Set a property of the source info.
    * @param key the name of the property.
    * @param value the value of the property, <tt>null</tt> to remove the
    * property.
    */
   @Override
   public void setProperty(String key, String value) {
      if(value == null) {
         prop.remove(key);
      }
      else {
         prop.setProperty(key, value);
      }
   }

   /**
    * Check if the source info is valid.
    */
   public void checkValidity() throws Exception {
      Catalog catalog = Catalog.getCatalog();

      if(prefix == null) {
         MessageException ex = new MessageException(catalog.getString(
            "common.sourcePrefixNull"));
         throw ex;
      }
      else if(source == null) {
         MessageException ex = new MessageException(catalog.getString(
            "common.sourceNull"));
         throw ex;
      }
   }

   /**
    * Check if the source is empty.
    * @return <tt>true</tt> if is empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return type == NONE || source == null || source.equals("");
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<sourceInfo class=\"" + getClass().getName() +
         "\" type=\"" + type + "\">");

      if(prefix != null) {
         writer.print("<prefix>");
         writer.print("<![CDATA[" + prefix + "]]>");
         writer.println("</prefix>");
      }

      if(source != null) {
         writer.print("<source>");
         writer.print("<![CDATA[" + source + "]]>");
         writer.println("</source>");
      }

      writeProperties(writer);

      writer.println("</sourceInfo>");
   }

   /**
    * Write properties.
    * @param writer the destination print writer.
    */
   private void writeProperties(PrintWriter writer) {
      if(Tool.isCompact()) {
         return;
      }

      writer.println("<properties>");
      Enumeration keys = prop.keys();

      while(keys.hasMoreElements()) {
         String key = (String) keys.nextElement();
         writer.println("<property>");
         writer.print("<key>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.print("</key>");

         String val = prop.getProperty(key);
         writer.print("<value>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.print("</value>");

         writer.println("</property>");
      }

      writer.println("</properties>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      type = Integer.parseInt(Tool.getAttribute(elem, "type"));
      prefix = Tool.getChildValueByTagName(elem, "prefix");
      source = Tool.getChildValueByTagName(elem, "source");

      Element propnode = Tool.getChildNodeByTagName(elem, "properties");

      if(propnode != null) {
         parseProperties(propnode);
      }
   }

   /**
    * Parse the properties.
    * @param elem the specified xml element.
    */
   private void parseProperties(Element elem) throws Exception {
      NodeList list = elem.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof Element)) {
            continue;
         }

         Element propnode = (Element) list.item(i);
         Element keynode = Tool.getChildNodeByTagName(propnode, "key");
         Element valnode = Tool.getChildNodeByTagName(propnode, "value");
         String key = Tool.getValue(keynode);
         String val = Tool.getValue(valnode);
         setProperty(key, val);
      }
   }

   /**
    * Return a displayable string including folder description.
    */
   public String toView() {
      // for component source info always shouldn't display source prefix.
      String str = VSUtil.getVSAssemblyBinding(source);

      if(type != NONE) {
         String folderDesc = getProperty(QUERY_FOLDER);
         String schema = getProperty(SCHEMA);

         if(folderDesc != null && !folderDesc.equals("") &&
            !(schema != null && schema.equals(folderDesc)))
         {
            str = folderDesc + "." + source;
         }

         if(prefix != null && !prefix.equals(source)) {
            str = prefix + "." + str;
         }
      }

      return str;
   }

   /**
    * Return a displayable string.
    */
   public String toString() {
      if(prefix != null) {
         return prefix + "." + source;
      }

      return source;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      int hash = type;

      if(prefix != null) {
         hash = hash ^ prefix.hashCode();
      }

      if(source != null) {
         hash = hash ^ source.hashCode();
      }

      return hash;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object to compare.
    * @return <tt>true</tt> if equals the object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof SourceInfo)) {
         return false;
      }

      SourceInfo sinfo = (SourceInfo) obj;
      return sinfo.type == type && Tool.equals(sinfo.prefix, prefix) &&
         Tool.equals(sinfo.source, source);
   }

   private int type = QUERY;
   private String prefix = null;
   private String source = null;
   private Properties prop;

   private static final Logger LOG =
      LoggerFactory.getLogger(SourceInfo.class);
}
