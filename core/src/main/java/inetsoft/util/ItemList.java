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
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

/**
 * This class represents a list containing Strings or XMLSerializable objects.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class ItemList implements XMLSerializable, Serializable {
   /**
    * Create an item list.
    */
   public ItemList() {
      this("list");
   }

   /**
    * Create an item list.
    * @param name the specified item list name.
    */
   public ItemList(String name) {
      items = new ArrayList();
      this.name = name;
}

   /**
    * Add an item.
    * @param item the item to be added to the list.
    */
   public void addItem(Object item) {
      items.add(item);
   }

   /**
    * Add an item.
    * @param index the specified index to add item.
    * @param val the specified item to be added to the list.
    */
   public void addItem(int index, Object val) {
      items.add(index, val);
   }

   /**
    * Batch add items.
    * @param items0 the specified items to be added to the list.
    */
   public void addAllItems(Collection items0) {
      items.addAll(items0);
   }

   /**
    * Check if contains an item.
    * @param item the specified item.
    * @return true if contains, false otherwise.
    */
   public boolean containsItem(Object item) {
      return items.contains(item);
   }

   /**
    * Get item at an index.
    * @param index the specified index.
    * @return item at the specified index.
    */
   public Object getItem(int index) {
      return items.get(index);
   }

   /**
    * Get index of an item.
    * @param item the specified item.
    * @return index of the specified item.
    */
   public int indexOfItem(Object item) {
      return items.indexOf(item);
   }

   /**
    * Get items iterator.
    * @return items iterator.
    */
   public Iterator itemsIterator() {
      return items.iterator();
   }

   /**
    * Remove an item.
    * @param item specified item to be removed from the list.
    */
   public void removeItem(Object item) {
      items.remove(item);
   }

   /**
    * Remove item at an index.
    * @param index the specified index.
    */
   public void removeItem(int index) {
      items.remove(index);
   }

   /**
    * Set item at an index.
    * @param index the specified index.
    * @param item the specified item.
    */
   public void setItem(int index, Object item) {
      items.set(index, item);
   }

   /**
    * Get size of the item list.
    * @return size of the item list.
    */
   public int size() {
      return items.size();
   }

   /**
    * Get size of the item list.
    * @return size of the item list.
    */
   public int getSize() {
      return size();
   }

   /**
    * Clear the item list.
    */
   public void clear() {
      items.clear();
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<" + name + " class=\"inetsoft.util.ItemList\">");

      for(int i = 0; i < items.size(); i++) {
         writeSerializableObject(items.get(i), writer);
      }

      writer.print("</" + name + ">");
   }

   /**
    * Write the contents to OutputStream.
    * @param dos the destination OutputStream.
    */
   public void writeData(DataOutputStream dos) {
      try{
         dos.writeUTF(name);
         dos.writeInt(items.size());

         for(int i = 0; i < items.size(); i++) {
            writeSerializableObject2(items.get(i), dos);
         }
      }
      catch(IOException e) {
      }
   }

   /**
    * Write a serializable object.
    */
   protected void writeSerializableObject(Object item, PrintWriter writer) {
      // is xml serializable?
      if(item instanceof XMLSerializable) {
         ((XMLSerializable) item).writeXML(writer);
      }
      // is not xml serializable?
      else if(item != null) {
         writer.print("<item><![CDATA[" +
            Tool.byteEncode(item.toString(), true) + "]]></item>");
      }
      else {
         writer.print("<item><![CDATA[]]></item>");
      }
   }

   /**
    * Write a serializable object.
    */
   protected void writeSerializableObject2(Object item, DataOutputStream dos) {
      try {
         dos.writeBoolean(item instanceof DataSerializable);

         // is DataSerializable?
         if(item instanceof DataSerializable) {
            ((DataSerializable) item).writeData(dos);
         }
         // is not data serializable?
         else if(item != null) {
            dos.writeUTF(item.toString());
         }
         else {
            dos.writeUTF("");
         }
      }
      catch(IOException e) {
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      clear();

      NodeList list = Tool.getChildNodesByTagName(tag, "item");

      if(list.getLength() > 0) {
         for(int i = 0; i < list.getLength(); i++) {
            Element itemnode = (Element) list.item(i);
            // not trim space value
            String val =
               Tool.byteDecode(Tool.getValue(itemnode, false, true, true));
            items.add(val);
         }
      }
      else {
         list = tag.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            if(list.item(i) instanceof Element) {
               Element node = (Element) list.item(i);
               String classname = Tool.getAttribute(node, "classname");
               classname = classname == null ?
                           Tool.getAttribute(node, "class") :
                           classname;
               XMLSerializable obj =
                  (XMLSerializable) Class.forName(classname).newInstance();
               obj.parseXML(node);
               items.add(obj);
            }
         }
      }
   }

   /**
    * Set item list name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get item list name.
    */
   public String getName() {
      return name;
   }

   /**
    * Convert the items to an array.
    */
   public Object[] toArray() {
      return items.toArray();
   }

   /**
    * Convert the items to an array.
    */
   public Object[] toArray(Object[] arr) {
      return items.toArray(arr);
   }

   /**
    * Check if equals to another object.
    * @param obj the specified object.
    */
   public boolean equals(Object obj) {
      ItemList list2 = (ItemList) obj;
      return items.equals(list2.items);
   }

   /**
    * Get the hash code.
    * @return the hash code.
    */
   public int hashCode() {
      return items.hashCode();
   }

   public String toString() {
      return "ItemList: " + name + " " + items;
   }

   protected List items;
   protected String name;
   private static final Logger LOG = LoggerFactory.getLogger(ItemList.class);
}
