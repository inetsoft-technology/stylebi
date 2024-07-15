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
package inetsoft.report.internal.binding;

import inetsoft.report.ReportSheet;
import inetsoft.report.TableLens;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * The base class which defines the common methods for all query related attr
 * classes.
 * All classes which implements this interface is element type independent,
 * and is treated as part of the query extension.
 *
 * @version 6.0 9/30/2003
 * @author mikec
 */

public abstract class DataAttr implements XMLSerializable, Cloneable, Serializable {
   /**
    * Get a property.
    */
   public String getProperty(String key) {
      return (String) properties.get(key);
   }

   /**
    * Set a property.
    * @param name the property name.
    * @param value the property value, null to remove the property.
    */
   public void setProperty(String name, String value) {
      if(value == null) {
         properties.remove(name);
      }
      else {
         properties.put(name, value);
      }
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         DataAttr attr = (DataAttr) super.clone();
         attr.properties = (Hashtable) properties.clone();
         return attr;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone data attributes", ex);
      }

      return null;
   }

   /**
    * Write the xml segment to the destination writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      // xml header part
      writer.print("<filter class=\"" + DataAttr.getClassName(getClass()) +
                   "\" ");
      writeAttributes(writer);
      writer.println(">");

      // xml content part
      writeCDATA(writer);
      writeProperties(writer);
      writeContents(writer);

      // xml footer
      writer.println("</filter>");
   }

   /**
    * Write attributes to an XML segment.
    */
   protected abstract void writeAttributes(PrintWriter writer);

   /**
    * Write the xml segment CDATA part.
    */
   protected void writeCDATA(PrintWriter writer) {
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   protected abstract void writeContents(PrintWriter writer);

   /**
    * Parse the xml segment.
    */
   @Override
   public final void parseXML(Element tag) throws Exception {
      parseAttributes(tag);

      parseCDATA(tag);
      parseContents(tag);
      parseProperties(tag);
   }

   /**
    * Parse the attribute part.
    */
   protected abstract void parseAttributes(Element tag) throws Exception;

   /**
    * Parse the CDATA value.
    */
   protected void parseCDATA(Element tag) throws Exception {
   }

   /**
    * Parse other contents.
    */
   protected abstract void parseContents(Element tag) throws Exception;

   /**
    * Parse the properties.
    */
   protected void parseProperties(Element tag) {
      // parse properties
      NodeList list = tag.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof Element)) {
            continue;
         }

         Element tag2 = (Element) list.item(i);

         if(tag2.getTagName().equals("property")) {
            String name = getSingleNodeValue(tag2, "name");
            String value = getSingleNodeValue(tag2, "value");

            if(name != null && value != null) {
               setProperty(name, value);
            }
         }
      }
   }

   /**
    * Write XML segmenet of the properties.
    */
   protected void writeProperties(PrintWriter writer) {
      if(properties != null && properties.size() > 0) {
         Enumeration keys = properties.keys();

         while(keys.hasMoreElements()) {
            String prop = (String) keys.nextElement();
            Object value = properties.get(prop);

            if(prop.equals("description")) {
               value = Catalog.getCatalog(null,
                  Catalog.REPORT).getString((String)value);
            }

            writer.println("<property><name><![CDATA[" + prop + "]]></name>" +
               "<value><![CDATA[" + value + "]]></value></property>");
         }
      }
   }

   /**
    * Get a single node value.
    */
   protected String getSingleNodeValue(Element tag, String key) {
      NodeList pair = tag.getChildNodes();

      for(int j = 0; j < pair.getLength(); j++) {
         if(!(pair.item(j) instanceof Element)) {
            continue;
         }

         Element node = (Element) pair.item(j);

         if(node.getTagName().equals(key)) {
            return Tool.getValue(node);
         }
      }

      return null;
   }

   /**
    * Get the unqualified class name.
    */
   static String getClassName(Class cls) {
      String name = cls.getName();
      int dot = name.lastIndexOf('.');

      return (dot > 0) ? name.substring(dot + 1) : name;
   }

   protected Hashtable properties = new Hashtable();

   private static final Logger LOG =
      LoggerFactory.getLogger(DataAttr.class);
}
