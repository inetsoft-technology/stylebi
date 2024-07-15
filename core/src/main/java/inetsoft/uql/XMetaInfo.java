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
package inetsoft.uql;

import inetsoft.report.Hyperlink;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * XMetaInfo defines a meta info with drill info and format info.
 *
 * @version 9.1, 04/06/2007
 * @author InetSoft Technology Corp
 */
public class XMetaInfo implements XMLSerializable, Serializable, Cloneable {
   /**
    * Create an empty meta info.
    */
   public XMetaInfo() {
      super();
   }

   /**
    * Set the drill info to the info.
    * @param info the drill info.
    */
   public void setXDrillInfo(XDrillInfo info) {
      this.dinfo = info;
   }

   /**
    * Get the drill info.
    */
   public XDrillInfo getXDrillInfo() {
      return this.dinfo;
   }

   /**
    * Check if drill info is empty.
    */
   public boolean isXDrillInfoEmpty() {
      return this.dinfo == null;
   }

   /**
    * Check if drill info and format info are empty.
    */
   public boolean isEmpty() {
      return isXDrillInfoEmpty() && isXFormatInfoEmpty();
   }

   /**
    * Set a format info to the info.
    * @param info the drill info.
    */
   public void setXFormatInfo(XFormatInfo info) {
      this.finfo = info;
   }

   /**
    * Get a format info.
    */
   public XFormatInfo getXFormatInfo() {
      return this.finfo;
   }

   /**
    * Check if format info is empty.
    */
   public boolean isXFormatInfoEmpty() {
      return this.finfo == null;
   }

   /**
    * Check if it is date.
    */
   public boolean isAsDate() {
      return asDate;
   }

   /**
    * Set it is date.
    * @param asDate date.
    */
   public void setAsDate(boolean asDate) {
      this.asDate = asDate;
   }

   /**
    * Get the date pattern.
    */
   public String getDatePattern() {
      return datePattern;
   }

   /**
    * Set the date pattern.
    * @param datePattern the date pattern.
    */
   public void setDatePattern(String datePattern) {
      this.datePattern = datePattern;
   }

   /**
    * Set the database locale.
    */
   public void setLocale(Locale locale) {
      this.locale = locale;
   }

   /**
    * Get the database locale.
    */
   public Locale getLocale() {
      return locale;
   }

   /**
    * Set data type for the column which created this meta info,
    * it is transient.
    */
   public void setProperty(String key, String value) {
      if(key == null) {
         return;
      }

      if(value == null) {
         removeProperty(key);
      }
      else {
         getProperties().put(key, value);
      }
   }

   /**
    * Remove a property.
    */
   public void removeProperty(String key) {
      if(key != null) {
         getProperties().remove(key);
      }
   }

   /**
    * Get the data type.
    */
   public String getProperty(String key) {
      return key == null ? null : getProperties().get(key);
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XMetaInfo)) {
         return false;
      }

      XMetaInfo meta = (XMetaInfo) obj;

      return Tool.equals(dinfo, meta.dinfo) &&
         Tool.equals(finfo, meta.finfo) && asDate == meta.asDate &&
         Tool.equals(datePattern, meta.datePattern) &&
         Tool.equals(locale, meta.locale);
   }

   @Override
   public int hashCode() {
      return Objects.hash(dinfo, finfo, asDate, datePattern, locale);
   }

   /**
    * Get the string representaion.
    */
   public String toString() {
      return toString(false);
   }

   public String toString(boolean full) {
      StringBuilder buf = new StringBuilder();
      buf.append("XMetaInfo");
      buf.append(super.hashCode());
      buf.append("[");

      if(dinfo != null) {
         buf.append(dinfo.toString(full));
         buf.append(",");
      }

      if(finfo != null) {
         buf.append(finfo.toString());
      }

      buf.append("," + asDate + ", " + datePattern + ", " + locale);
      buf.append("]");

      return buf.toString();
   }

   /**
    * Clone the object.
    */
   @Override
   public XMetaInfo clone() {
      try {
         XMetaInfo meta = (XMetaInfo) super.clone();

         if(dinfo != null) {
            meta.dinfo = (XDrillInfo) dinfo.clone();
         }

         if(finfo != null) {
            meta.finfo = (XFormatInfo) finfo.clone();
         }

         meta.properties = (Map<String, String>) ((HashMap) getProperties()).clone();
         return meta;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone XMetaInfo", ex);
      }

      return null;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<XMetaInfo>");
      writeDatePattern(writer);

      if(dinfo != null && !dinfo.isEmpty()) {
         dinfo.writeXML(writer);
      }

      if(finfo != null && !finfo.isEmpty()) {
         finfo.writeXML(writer);
      }

      writer.println("</XMetaInfo>");
   }

   private void writeDatePattern(PrintWriter writer) {
      writer.println("<DatePattern asDate=\"" + asDate +
         "\" locale=\"" + formatLocale(locale) + "\">");
      String pattern = (datePattern == null ? "" : datePattern);
      writer.print("<![CDATA[" + pattern + "]]>");
      writer.println("</DatePattern>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseDatePattern(tag);
      Element dnode = Tool.getChildNodeByTagName(tag, "XDrillInfo");

      if(dnode != null) {
         dinfo = new XDrillInfo();
         dinfo.parseXML(dnode);
      }

      Element fnode = Tool.getChildNodeByTagName(tag, "XFormatInfo");

      if(fnode != null) {
         finfo = new XFormatInfo();
         finfo.parseXML(fnode);
      }
   }

   private void parseDatePattern(Element tag) {
      Element node = Tool.getChildNodeByTagName(tag, "DatePattern");

      if(node != null) {
         asDate = "true".equalsIgnoreCase(Tool.getAttribute(node, "asDate"));
         String lstr = Tool.getAttribute(node, "locale");
         locale = getLocale(lstr);
         datePattern = Tool.getValue(node);
      }
   }

   private Map<String, String> getProperties() {
      if(properties == null) {
         properties = new HashMap<>();
      }

      return properties;
   }

   /**
    * Format locale to string.
    */
   public static String formatLocale(Locale locale) {
      if(locale == null) {
         return "";
      }

      String lan = locale.getLanguage();
      String country = locale.getCountry();
      String variant = locale.getVariant();

      return lan + (country.length() <= 0 ? "" :
         ("_" + country + (variant.length() <= 0 ? "" : "_" + variant)));
   }

   /**
    * Create locale from string.
    */
   public static Locale getLocale(String locale) {
      if(locale == null || locale.trim().length() <= 0) {
         return null;
      }

      String[] arr = locale.split("_");
      String language = "", country = "", variant = "";

      if(arr.length > 0) {
         language = arr[0];
      }

      if(arr.length > 1) {
         country = arr[1];
      }

      if(arr.length > 2) {
         variant = arr[2];
      }

      return new Locale(language, country, variant);
   }

   public void processDrillLinks(List<Hyperlink> drillLinks) {
      if(dinfo == null) {
         return;
      }

      Enumeration paths = dinfo.getDrillPaths();

      while(paths.hasMoreElements()) {
         DrillPath path = (DrillPath) paths.nextElement();
         String link = path.getLink();

         if(link != null) {
            drillLinks.add(new Hyperlink(link));
         }
      }
   }

   private XDrillInfo dinfo; // drill info
   private XFormatInfo finfo; // format info
   private boolean asDate; // is a date?
   private String datePattern; // date pattern
   private Locale locale;
   // * runtime properties, such as the column which this meta info belongs'
   // data type or this meta info's format info is auto created...
   private transient Map<String, String> properties = new HashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(XMetaInfo.class);
}
