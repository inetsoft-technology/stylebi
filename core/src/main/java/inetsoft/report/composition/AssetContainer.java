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
package inetsoft.report.composition;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.*;
import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Asset container contains <tt>XMLSerializable</tt>s, and encapsulates the
 * associated functions for <tt>AssetEvent</tt>, <tt>AssetCommand</tt>, etc.
 * <p>
 * When creating a sub class of this class, please make sure that the default
 * constructor is available for <tt>Class.forName</tt> to create the instance
 * of the sub class.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class AssetContainer implements AssetObject, DataSerializable {
   /**
    * Create an asset container by parsing an xml element.
    * @param elem the specified xml element.
    * @return the created asset container.
    */
   public static AssetContainer createAssetContainer(Element elem)
      throws Exception
   {
      String cls = Tool.getAttribute(elem, "class");
      AssetContainer container =
         (AssetContainer) Class.forName(cls).newInstance();
      container.parseXML(elem);
      return container;
   }

   /**
    * Constructor.
    */
   public AssetContainer() {
      super();
      map = new HashMap<>();
      map2 = new HashMap<>();
   }

   /**
    * Copy propereties from another asset container.
    * @param container the specified another asset container.
    */
   public void copyProperties(AssetContainer container) {
      container.lock.lock();

      try {
         List<String> list = new ArrayList<>(container.map2.keySet());

         for(String key : list) {
            String val = container.map2.get(key);
            put(key, val);
         }
      }
      finally {
         container.lock.unlock();
      }
   }

   /**
    * Put a key-value pair.
    * @param name the specified key-value pair name.
    * @param obj the specified key-value pair value.
    */
   public final void put(String name, XMLSerializable obj) {
      if(obj != null) {
         map.put(name, obj);
      }
   }

   /**
    * Put a key-value pair.
    * @param name the specified key-value pair name.
    * @param obj the specified key-value pair value.
    */
   public final void put(String name, String obj) {
      if(obj != null) {
         map.put(name, obj);
         lock.lock();

         try {
            map2.put(name, obj);
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Get the value of a key-value pair.
    * @param name the specified name.
    * @return the value of the key-value pair, <tt>null</tt> if not found.
    */
   public Object get(String name) {
      return map.get(name);
   }

   /**
    * Remove one key-value pair.
    * @param name the specified key-value pair.
    */
   public void remove(String name) {
      map.remove(name);
      lock.lock();

      try {
         map2.remove(name);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Clear the asset container.
    */
   public void clear() {
      map.clear();
      lock.lock();

      try {
         map2.clear();
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AssetContainer container = (AssetContainer) super.clone();
         container.map = new HashMap<>(map);
         container.map2 = new HashMap<>(map2);
         return container;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<assetContainer class=\"" + getClassName() + "\">");
      Map map = (Map) this.map.clone();

      for(Object key : map.keySet()) {
         Object val = map.get(key);

         if(val != null) {
            createPairEntry((String) key, val).writeXML(writer);
         }
      }

      writeContents(writer);
      writer.println("</assetContainer>");
   }

   /**
    * Get class name.
    */
   protected String getClassName() {
      return getClass().getName();
   }

   /**
    * Get proper PairEntry.
    */
   protected PairEntry createPairEntry(String key, Object val) {
      return new PairEntry(key, val);
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   protected void writeContents(PrintWriter writer) {
      // do nothing
   }

   /**
    * Write the data value to DataOutputStream.
    * @param dos the destination OutputStream.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      try {
         dos.writeInt(map.size());

         for(String key : map.keySet()) {
            Object val = map.get(key);

            if(val != null) {
               createPairEntry(key, val).writeData(dos);
            }
         }
      }
      catch(IOException ignore) {
      }

      writeContents2(dos);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      //do nothing
      return true;
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the data.
    */
   protected void writeContents2(DataOutputStream dos) {
      // do nothing
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      NodeList nodes = Tool.getChildNodesByTagName(elem, "pairEntry");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element temp = (Element) nodes.item(i);
         PairEntry entry = createPairEntry(null, null);
         entry.parseXML(temp);
         
         if(entry.value != null) {
            map.put(entry.key, entry.value);
         }

         if(entry.value instanceof String) {
            map2.put(entry.key, (String) entry.value);
         }
      }

      parseContents(elem);
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
      // do nothing
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals0(Object obj) {
      if(obj == null) {
         return false;
      }

      if(!obj.getClass().equals(this.getClass())) {
         return false;
      }

      AssetContainer container2 = (AssetContainer) obj;
      return map2.equals(container2.map2);
   }

   /**
    * Get the hash code.
    * @return the hash code of this asset container.
    */
   public int hashCode() {
      return getClass().hashCode() + map2.hashCode();
   }

   /**
    * Pair entry encapsulates write/parse xml logic.
    */
   protected static class PairEntry implements XMLSerializable, DataSerializable {
      public PairEntry() {
         super();
      }

      public PairEntry(String key, Object val) {
         this();
         this.key = key;
         this.value = val;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         boolean str = (value instanceof String);
         Class cls = (value instanceof CustomSerializable)
            ? ((CustomSerializable) value).getSerializedClass()
            : value.getClass();

         writer.println("<pairEntry>");
         writer.print("<key>");
         writer.print("<![CDATA[" + encode(key) + "]]>");
         writer.print("</key>");

         writer.print("<value string=\"" + str + "\" class=\"" +
                      cls.getName() + "\">");

         if(str) {
            writer.print("<![CDATA[" + encode((String) value) + "]]>");
         }
         else {
            writeValueXML(writer);
         }

         writer.print("</value>");
         writer.println("</pairEntry>");
      }

      protected String encode(String str) {
         return str;
      }

      protected String decode(String str) {
         return str;
      }

      protected void writeValueXML(PrintWriter writer) {
         ((XMLSerializable) value).writeXML(writer);
      }

      @Override
      public void parseXML(Element elem) throws Exception {
         this.key = decode(Tool.getChildValueByTagName(elem, "key"));

         Element vnode = Tool.getChildNodeByTagName(elem, "value");
         boolean str = "true".equals(Tool.getAttribute(vnode, "string"));
         String cls = Tool.getAttribute(vnode, "class");

         if(str) {
            value = decode(Tool.getValue(vnode));
         }
         else {
            vnode = Tool.getFirstChildNode(vnode);
            this.value = Class.forName(cls).newInstance();
            ((XMLSerializable) value).parseXML(vnode);
         }
      }

      @Override
      public void writeData(DataOutputStream dos) {
         boolean str = value instanceof String;
         Class cls = (value instanceof CustomSerializable)
            ? ((CustomSerializable) value).getSerializedClass()
            : value.getClass();

         try {
            dos.writeUTF(key);
            dos.writeBoolean(str);

            if(str) {
               dos.writeUTF((String) value);
            }
            else {
               dos.writeUTF(cls.getName());
               writeValueData(dos);
            }
         }
         catch(IOException ignore) {
         }
      }

      protected void writeValueData(DataOutputStream dos) throws IOException {
         if(value instanceof DataSerializable) {
            ((DataSerializable) value).writeData(dos);
         }
         else if(value instanceof XMLSerializable) {
            XMLTool.writeXMLSerializableAsData(dos, (XMLSerializable) value);
         }
      }

      @Override
      public boolean parseData(DataInputStream input) {
         // do nothing
         return true;
      }

      @Override
      public String toString() {
         return "PairEntry{" +
            "key='" + key + '\'' +
            ", value=" + value +
            '}';
      }

      public String key;
      public Object value;
   }

   /**
    * Pair entry encapsulates write/parse xml logic, it imporve the logic of
    * write xml/data for value.
    */
   protected static class PairEntry2 extends PairEntry {
      public PairEntry2() {
         super();
      }

      public PairEntry2(String key, Object val) {
         super(key, val);
      }

      @Override
      protected void writeValueXML(PrintWriter writer) {
         if(value instanceof XMLSerializable) {
            super.writeValueXML(writer);
         }
         else if(value instanceof DataSerializable) {
            writer.print("<![CDATA[");
            byte[] arr =
               Base64.encodeBase64(Tool.convertObject((DataSerializable) value));
            writer.print(new String(arr));
            writer.println("]]>");
         }
         else {
            throw new RuntimeException("unsupported data found: " + value);
         }
      }

      @Override
      protected void writeValueData(DataOutputStream dos) throws IOException {
         if(value instanceof DataSerializable) {
            ((DataSerializable) value).writeData(dos);
         }
         else if(value instanceof XMLSerializable) {
            StringWriter writer = new StringWriter();
            ((XMLSerializable) value).writeXML(new PrintWriter(writer));
            dos.writeUTF(writer.toString());
         }
         else {
            throw new RuntimeException("unsupported data found: " + value);
         }
      }

      @Override
      protected String encode(String str) {
         return Tool.byteEncode(str);
      }

      @Override
      protected String decode(String str) {
         return Tool.byteDecode(str);
      }
   }

   /**
    * Get viewsheet sand box.
    */
   protected static ViewsheetSandbox getSandbox(ViewsheetSandbox box,
                                                String name) 
   {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box0 = box.getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return getSandbox(box0, name);
      }

      return box;
   }

   protected HashMap<String, Object> map;
   protected HashMap<String, String> map2;
   private Lock lock = new ReentrantLock();
}
