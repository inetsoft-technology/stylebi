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

import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * XMLIndexedStorage inplements <tt>IndexedStorage</tt> in an
 * <tt>XMLSerializable</tt> way.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XMLIndexedStorage extends AbstractIndexedStorage
   implements Serializable, Cloneable, XMLSerializable
{
   /**
    * Creates a new instance of XMLIndexedStorage.
    *
    * @param settings the configuration settings. The <code>storage.name</code>
    *                 property is required and should provide the name of the
    *                 XML node that contains the stored data.
    */
   public XMLIndexedStorage(Properties settings) {
      this(settings.getProperty("storage.name"));
   }

   /**
    * Creates a new instance of XMLIndexedStorage.
    *
    * @param name the name of the XML node that contains the stored data.
    */
   public XMLIndexedStorage(String name) {
      this.name = name;
   }

   /**
    * Remove all key-data mappings from storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   @Override
   public synchronized boolean clear() {
      map.clear();
      return true;
   }

   @Override
   public void dispose() {
      // NO-OP
   }

   /**
    * Check if the storage containe the key or not.
    * @param key the specified key
    * @return <tt>true</tt> if the storage containe the key,
    * <tt>false</tt> otherwise.
    */
   @Override
   public synchronized boolean contains(String key) {
      return map.containsKey(key);
   }

   /**
    * Return the data's length to which the specified key is mapped.
    */
   @Override
   public long getDataLength(String key) {
      long result = 0L;

      try {
         byte[] data = get(key);

         if(data != null) {
            result = data.length;
         }

         return result;
      }
      catch(Exception e) {
         LOG.error(
                     "Failed to read indexed storage data block", e);
      }

      return result;
   }

   /**
    * Return the data to which the specified key is mapped in this storage.
    * @param key key whose associated data is to be returned.
    * @return the data to which this map maps the specified key, or
    * <tt>null</tt> if the storage contains no mapping for this key.
    * @throws UnsupportedOperationException
    */
   @Override
   public byte[] get(String key) throws Exception {
      throw new UnsupportedOperationException();
   }

   /**
    * Add the specified data with the specified key in this storage
    * If the storage previously contained a data for this key, the old data is
    * replaced by the specified data.
    * @param key key with which the specified data is to be associated.
    * @param value data to be associated with the specified key.
    * @throws UnsupportedOperationException
    */
   @Override
   protected void put(String key, byte[] value) throws Exception {
      throw new UnsupportedOperationException();
   }

   /**
    * Return the data to which the specified key is mapped in this storage.
    * @param key key whose associated data is to be returned.
    * @param trans transformation before parsing.
    * @return the data to which this map maps the specified key, or
    * <tt>null</tt> if the storage contains no mapping for this key.
    * @throws UnsupportedOperationException
    */
   @Override
   public synchronized XMLSerializable getXMLSerializable(
      String key, TransformListener trans) throws Exception
   {
      return map.get(key);
   }

   /**
    * Add the specified data with the specified key in this storage
    * If the storage previously contained a data for this key, the old data is
    * replaced by the specified data.
    * @param key key with which the specified data is to be associated.
    * @param value data to be associated with the specified key.
    * @throws Exception if put XMLSerializable value failed.
    */
   @Override
   public synchronized void putXMLSerializable(String key,
      XMLSerializable value) throws Exception
   {
      map.put(key, value);
   }

   /**
    * Gets the data value that is associated with the specified key.
    *
    * @param key the data key.
    *
    * @return the associated value.
    * @throws UnsupportedOperationException
    */
   @Override
   public Object getSerializable(String key) throws Exception {
      throw new UnsupportedOperationException();
   }

   /**
    * Adds a data value that is associated with the specified key.
    *
    * @param key the data key.
    * @param value the value to add.
    *
    * @throws UnsupportedOperationException
    */
   @Override
   public void putSerializable(String key, Serializable value)
      throws Exception
   {
      throw new UnsupportedOperationException();
   }

   /**
    * Remove the data for this key from this storage if present.
    * @param key key whose data is to be removed from the storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   @Override
   public synchronized boolean remove(String key) {
      if(!map.containsKey(key)) {
         return false;
      }

      map.remove(key);
      return true;
   }

   /**
    * Rename a key.
    * @param okey the specified old key.
    * @param nkey the specified new key.
    * @param overwrite <tt>true</tt> to overwrite new key if exists,
    * <tt>false</tt> otherwise.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public synchronized boolean rename(String okey, String nkey,
                                      boolean overwrite) {
      boolean ocontained = map.containsKey(okey);

      if(!ocontained) {
         return false;
      }

      boolean ncontained = map.containsKey(nkey);

      if(ncontained && !overwrite) {
         return false;
      }

      XMLSerializable oval = map.remove(okey);
      map.put(nkey, oval);
      return true;
   }

   /**
    * Get the size.
    * @return the size of the storage, <tt>-1</tt> if exception occurs.
    */
   @Override
   public synchronized long size() {
      return map.size();
   }

   /**
    * Get last modified timestamp.
    * @return last modified timestamp.
    */
   @Override
   public long lastModified() {
      return 0;
   }

   @Override
   public long lastModified(String key) {
      return 0;
   }

   @Override
   public long lastModified(Filter filter) {
      return 0L;
   }

   @Override
   public Map<String, Long> getTimestamps(Filter filter) {
      return Collections.emptyMap();
   }

   @Override
   public Map<String, Long> getTimestamps(Filter filter, long from) {
      return Collections.emptyMap();
   }

   @Override
   public synchronized Set<String> getKeys(Filter filter) {
      Set<String> result = new HashSet<>();

      for(String key : map.keySet()) {
         if(filter == null || filter.accept(key)) {
            result.add(key);
         }
      }

      return result;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public synchronized void writeXML(PrintWriter writer) {
      writer.println("<" + name + ">");

      for(Map.Entry<String, XMLSerializable> e : map.entrySet()) {
         String key = e.getKey();
         XMLSerializable value = e.getValue();
         XMLEntry entry = new XMLEntry(key, value);
         entry.writeXML(writer);
      }

      writer.println("</" + name + ">");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    */
   @Override
   public synchronized void parseXML(Element tag) throws Exception {
      NodeList nodes = tag.getChildNodes();

      for(int i = 0; i < nodes.getLength(); i++) {
         Node node = nodes.item(i);

         if(node instanceof Element) {
            Element elem = (Element) node;
            XMLEntry entry = new XMLEntry();
            entry.parseXML(elem);
            map.put(entry.getKey(), entry.getValue());
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         XMLIndexedStorage storage = (XMLIndexedStorage) super.clone();
         storage.map = Tool.deepCloneMap(map);
         return storage;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * XML entry represents a key-value pair in XMLIndexedStorage.
    */
   private static class XMLEntry implements XMLSerializable {
      public XMLEntry() {
      }

      public XMLEntry(String key, XMLSerializable value) {
         this.key = key;
         this.value = value;
      }

      public String getKey() {
         return key;
      }

      public XMLSerializable getValue() {
         return value;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         String cls = value.getClass().getName();
         writer.println("<xmlEntry>");
         writer.print("<entryKey>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.println("</entryKey>");
         writer.print("<entryValue class=\"" + cls + "\">");
         value.writeXML(writer);
         writer.println("</entryValue>");
         writer.println("</xmlEntry>");
      }

      @Override
      public void parseXML(Element elem) throws Exception  {
         Element keynode = Tool.getChildNodeByTagName(elem, "entryKey");
         key = Tool.getValue(keynode);
         Element valnode = Tool.getChildNodeByTagName(elem, "entryValue");
         String cls = Tool.getAttribute(valnode, "class");
         assert valnode != null;
         NodeList xnodes = valnode.getChildNodes();
         Element xnode = null;

         for(int i = 0; i < xnodes.getLength(); i++) {
            Node node = xnodes.item(i);

            if(node instanceof Element) {
               xnode = (Element) node;
               break;
            }
         }

         if(xnode == null) {
            throw new Exception("XMLSerializable node not found: " + elem);
         }

         value = (XMLSerializable) Class.forName(cls).newInstance();
         value.parseXML(xnode);
      }

      private String key;
      private XMLSerializable value;
   }

   private String name;
   private Map<String, XMLSerializable> map = new HashMap<>();

   private static final org.slf4j.Logger LOG =
      LoggerFactory.getLogger(XMLIndexedStorage.class);
}
