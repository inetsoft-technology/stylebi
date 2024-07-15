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
package inetsoft.analytic.composition;

import inetsoft.report.composition.AssetContainer;
import inetsoft.util.*;
import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * VSContainer, the default implementation for AssetContainer. It is just a
 * container for String, XMLSerializable and DataSerializable objects.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class VSContainer extends AssetContainer {
   /**
    * Put a key-value pair.
    * @param name the specified key-value pair name.
    * @param obj the specified key-value pair value.
    */
   public final void put(String name, DataSerializable obj) {
      if(obj != null) {
         map.put(name, obj);
      }
   }

   /**
    * Put a key-value pair.
    * @param name the specified key-value pair name.
    * @param obj the specified key-value pair value.
    */
   public final void put(String name, String[] obj) {
      if(obj != null) {
         map.put(name, new ArrayHolder(obj));
      }
   }

   /**
    * Put a key-value pair.
    * @param name the specified key-value pair name.
    * @param obj the specified key-value pair value.
    */
   public final void put(String name, XMLSerializable[] obj) {
      if(obj != null) {
         map.put(name, new ArrayHolder(obj));
      }
   }

   /**
    * Put a key-value pair.
    * @param name the specified key-value pair name.
    * @param obj the specified key-value pair value.
    */
   public final void put(String name, DataSerializable[] obj) {
      if(obj != null) {
         map.put(name, new ArrayHolder(obj));
      }
   }

   /**
    * Get the value of a key-value pair.
    * @param name the specified name.
    * @return the value of the key-value pair, <tt>null</tt> if not found.
    */
   @Override
   public Object get(String name) {
      Object obj = map.get(name);

      if(obj instanceof ArrayHolder) {
         return ((ArrayHolder)obj).arr;
      }

      return obj;
   }

   /**
    * Get proper PairEntry.
    */
   @Override
   protected PairEntry createPairEntry(String key, Object val) {
      return new PairEntry2(key, val);
   }

   public static class ArrayHolder implements XMLSerializable {
      public ArrayHolder() {
      }

      public ArrayHolder(String[] arr) {
         this.arr = arr;
      }

      public ArrayHolder(XMLSerializable[] arr) {
         this.arr = arr;
      }

      public ArrayHolder(DataSerializable[] arr) {
         this.arr = arr;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         if(arr == null || arr.length == 0) {
            return;
         }

         Class cls = (arr[0] instanceof CustomSerializable)
            ? ((CustomSerializable) arr[0]).getSerializedClass()
            : arr[0].getClass();

         writer.print("<ArrayHolder string=\"");
         writer.print(arr[0] instanceof String);
         writer.print("\" class=\"");
         writer.print(cls.getName());
         writer.println("\">");

         for(int i = 0; i < arr.length; i++) {
            writer.println("<arrayElem>");
            writeElement(writer, arr[i]);
            writer.println("</arrayElem>");
         }

         writer.println("</ArrayHolder>");
      }

      private void writeElement(PrintWriter writer, Object val) {
         if(val instanceof String) {
            writer.print("<![CDATA[");
            writer.print(val);
            writer.println("]]>");
         }
         else if(val instanceof XMLSerializable) {
            ((XMLSerializable) val).writeXML(writer);
         }
         else if(val instanceof DataSerializable) {
            writer.print("<![CDATA[");
            byte[] arr =
               Base64.encodeBase64(Tool.convertObject((DataSerializable) val));
            writer.print(new String(arr));
            writer.println("]]>");
         }
         else {
            throw new RuntimeException("unsupported data found: " + val);
         }
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         boolean str =
            "true".equalsIgnoreCase(Tool.getAttribute(tag, "string"));

         String cls = Tool.getAttribute(tag, "class");
         NodeList nodes = Tool.getChildNodesByTagName(tag, "arrayElem");
         arr = new Object[nodes.getLength()];

         for(int i = 0; i < arr.length; i++) {
            Element temp = (Element) nodes.item(i);

            if(str) {
               arr[i] = Tool.getValue(temp);
            }
            else {
               arr[i] = Class.forName(cls).newInstance();

               if(arr[i] instanceof XMLSerializable) {
                  temp = Tool.getFirstChildNode(temp);
                  ((XMLSerializable) arr[i]).parseXML(temp);
               }
               else if(arr[i] instanceof DataSerializable) {
                  // do nothing
               }
            }
         }
      }

      private Object writeElement(Object val) {
         if(val instanceof String) {
            return val;
         }
         else if(val instanceof DataSerializable) {
            byte[] arr =
               Base64.encodeBase64(Tool.convertObject((DataSerializable) val));
            return new String(arr);
         }
         else {
            throw new RuntimeException("unsupported data found: " + val);
         }
      }

      private Object[] arr;
   }
}