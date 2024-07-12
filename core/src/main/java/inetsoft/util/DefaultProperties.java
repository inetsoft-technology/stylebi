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
package inetsoft.util;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * Specialization of Properties that falls back to a separate property set
 * for default values if no default is specified.
 *
 * @author InetSoft Technology
 * @since  10.3
 */
public class DefaultProperties extends Properties {
   /**
    * Creates a new instance of <tt>DefaultProperties</tt>.
    *
    * @param mainProperties    the main properties.
    * @param defaultProperties the properties containing the default values.
    */
   public DefaultProperties(Properties mainProperties,
                            Properties defaultProperties)
   {
      this.mainProperties = mainProperties;
      this.defaultProperties = defaultProperties != null ? defaultProperties : new Properties();
   }

   /**
    * Gets the main, mutable properties underlying this properties object.
    *
    * @return the main properties.
    */
   public Properties getMainProperties() {
      return mainProperties;
   }

   /**
    * Gets the default, read-only properties underlying this properties object.
    *
    * @return the default properties.
    */
   public Properties getDefaultProperties() {
      return defaultProperties;
   }

   @Override
   public String getProperty(String key) {
      String value = mainProperties.getProperty(key);

      if(value == null) {
         value = defaultProperties.getProperty(key);
      }

      return value;
   }

   @Override
   public String getProperty(String key, String defaultValue) {
      String value = mainProperties.getProperty(key);

      if(value == null || "".equals(value)) {
         value = defaultValue;
      }

      return value;
   }

   @Override
   public void clear() {
      mainProperties.clear();
   }

   @Override
   public Object clone() {
      Properties mcopy = (Properties) mainProperties.clone();
      Properties dcopy = (Properties) defaultProperties.clone();
      return new DefaultProperties(mcopy, dcopy);
   }

   @Override
   public boolean contains(Object value) {
      return mainProperties.contains(value) ||
         defaultProperties.contains(value);
   }

   @Override
   public boolean containsKey(Object key) {
      return mainProperties.containsKey(key) ||
         defaultProperties.containsKey(key);
   }

   @Override
   public boolean containsValue(Object value) {
      return mainProperties.containsValue(value) ||
         defaultProperties.containsValue(value);
   }

   @Override
   public Enumeration<Object> elements() {
      return mainProperties.elements();
   }

   @Override
   public Set<Entry<Object, Object>> entrySet() {
      return mainProperties.entrySet();
   }

   @Override
   public Object get(Object key) {
      Object value = mainProperties.get(key);

      if(value == null) {
         value = defaultProperties.get(key);
      }

      return value;
   }

   @Override
   public boolean isEmpty() {
      return mainProperties.isEmpty() && defaultProperties.isEmpty();
   }

   @Override
   public Enumeration<Object> keys() {
      return mainProperties.keys();
   }

   @Override
   public Set<Object> keySet() {
      return mainProperties.keySet();
   }

   @Override
   public void list(PrintStream out) {
      mainProperties.list(out);
   }

   @Override
   public void list(PrintWriter out) {
      mainProperties.list(out);
   }

   @Override
   public void load(InputStream inStream) throws IOException {
      mainProperties.load(inStream);
   }

   @Override
   public void load(Reader reader) throws IOException {
      mainProperties.load(reader);
   }

   @Override
   public void loadFromXML(InputStream in) throws IOException,
         InvalidPropertiesFormatException {
      mainProperties.loadFromXML(in);
   }

   @Override
   public Enumeration<?> propertyNames() {
      return mainProperties.propertyNames();
   }

   @Override
   public Object put(Object key, Object value) {
      return mainProperties.put(key, value);
   }

   @Override
   public void putAll(Map<? extends Object, ? extends Object> t) {
      mainProperties.putAll(t);
   }

   @Override
   public Object remove(Object key) {
      return mainProperties.remove(key);
   }

   @SuppressWarnings("deprecation")
   @Override
   public void save(OutputStream out, String comments) {
      mainProperties.save(out, comments);
   }

   @Override
   public Object setProperty(String key, String value) {
      return mainProperties.setProperty(key, value);
   }

   @Override
   public int size() {
      return mainProperties.size();
   }

   @Override
   public void store(OutputStream out, String comments) throws IOException {
      mainProperties.store(out, comments);
   }

   @Override
   public void store(Writer writer, String comments) throws IOException {
      mainProperties.store(writer, comments);
   }

   @Override
   public void storeToXML(OutputStream os, String comment, String encoding)
      throws IOException
   {
      mainProperties.storeToXML(os, comment, encoding);
   }

   @Override
   public void storeToXML(OutputStream os, String comment)
      throws IOException
   {
      mainProperties.storeToXML(os, comment);
   }

   @Override
   public Set<String> stringPropertyNames() {
      return mainProperties.stringPropertyNames();
   }

   @Override
   public String toString() {
      return mainProperties.toString();
   }

   @Override
   public Collection<Object> values() {
      return mainProperties.values();
   }

   private final Properties mainProperties;
   private final Properties defaultProperties;
}