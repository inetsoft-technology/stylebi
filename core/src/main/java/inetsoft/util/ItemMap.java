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
package inetsoft.util;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

/**
 * This class represents a xml serializable map containing Strings.
 * @author InetSoft Technology Corp.
 * @since  6.5
 */
public class ItemMap implements XMLSerializable, Serializable {
   /**
    * Create an item map.
    */
   public ItemMap() {
      this("map");
   }

   /**
    * Create an item map.
    * @param name the specified item map name.
    */
   public ItemMap(String name) {
      this.name = name;
      // keep insersion order since it may be needed for dispay items
      map = new OrderedMap(); 
   }

   /**
    * Clear the item map.
    */
   public void clear() {
      map.clear();
   }

   /**
    * Check if contains an item.
    * @param key the specified item key.
    * @return true if contains, false otherwise.
    */
   public boolean containsItem(Object key) {
      return map.containsKey(key);
   }

   /**
    * Get an item.
    * @param key the specified item key.
    * @return item of the specified item key
    */
   public Object getItem(Object key) {
      return map.get(key);
   }

   /**
    * Get item keys iterator.
    * @return item keys iterator.
    */
   public Iterator itemKeys() {
      return map.keySet().iterator();
   }

   /**
    * Get item values iterator.
    * @return item values iterator.
    */
   public Iterator itemValues() {
      return map.values().iterator();
   }

   /**
    * Put an item into the item map.
    * @param key the specified item key.
    * @param val the specified item value.
    */
   public void putItem(Object key, Object val) {
      map.put(key, val);
   }

   /**
    * Batch put items into the item map.
    * @param map0 the specified map.
    */
   public void putAllItems(Map map0) {
      map.putAll(map0);
   }

   /**
    * Remove an item.
    * @param key the specified item key.
    */
   public void removeItem(Object key) {
      map.remove(key);
   }

   /**
    * Get size of the item map.
    * @return size of the item map.
    */
   public int size() {
      return map.size();
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<" + name + " class=\"inetsoft.util.ItemMap\">");

      Iterator keys = itemKeys();

      while(keys.hasNext()) {
         Object key = keys.next();
         Object val = getItem(key);

         writer.print("<item>");
         writer.print("<key>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.print("</key>");
         writer.print("<value>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.print("</value>");
         writer.print("</item>");
      }

      writer.print("</" + name + ">");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      clear();

      NodeList list = Tool.getChildNodesByTagName(tag, "item");

      for(int i = 0; i < list.getLength(); i++) {
         Element itemnode = (Element) list.item(i);
         Element keynode = Tool.getChildNodeByTagName(itemnode, "key");
         Element valnode = Tool.getChildNodeByTagName(itemnode, "value");
         String key = Tool.getValue(keynode);
         String val = Tool.getValue(valnode);
         putItem(key, val);
      }
   }

   /**
    * Set item map name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get item map name.
    */
   public String getName() {
      return name;
   }

   protected Map map;
   protected String name;
}
