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

import org.w3c.dom.*;

import java.io.*;
import java.util.ArrayList;

/**
 * XMLTool, the xml toolkit.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5
 */
public class XMLTool {
   /**
    * Write an object.
    */
   public static void write(Object obj) {
      write(obj, System.err);
   }

   /**
    * Write an object.
    */
   public static void write(Object obj, OutputStream out) {
      PrintWriter fOut;

      try {
         fOut = new PrintWriter(new OutputStreamWriter(out, "UTF8"));
      }
      catch(UnsupportedEncodingException ex) {
         fOut = new PrintWriter(out);
      }

      if(obj instanceof Node) {
         write((Node) obj, fOut);
      }
      else if(obj instanceof XMLSerializable) {
         ((XMLSerializable) obj).writeXML(fOut);
      }
      else {
         fOut.println(obj);
      }

      fOut.flush();
   }

   /**
    * Write asset which after rename transformation.
    */
   public static void writeAssets(Object obj, OutputStream out, String className, String identifier) {
      PrintWriter fOut;

      try {
         fOut = new PrintWriter(new OutputStreamWriter(out, "UTF8"));
      }
      catch(UnsupportedEncodingException ex) {
         fOut = new PrintWriter(out);
      }

      if(obj instanceof Node) {
         write0((Node) obj, fOut, className, identifier);
      }
   }

   /**
    * Write a node for rename transformation result.
    */
   private static void write0(Node node, PrintWriter fOut, String className, String identifier) {
      if(node == null) {
         return;
      }

      if(node.getNodeType() == Node.DOCUMENT_NODE) {
         Document document = (Document)node;
         fOut.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         fOut.println("<?inetsoft-asset classname=\"" + className +
            "\" identifier=\"" + identifier + "\"?>");

         fOut.flush();
         write(document.getDoctype(), fOut);
         write(document.getDocumentElement(), fOut);
      }
      else {
         write(node, fOut);
      }
   }

   /**
    * Write a node.
    */
   private static void write(Node node, PrintWriter fOut) {
      if(node == null) {
         return;
      }

      short type = node.getNodeType();

      switch(type) {
      case Node.DOCUMENT_NODE:
         Document document = (Document)node;
         fOut.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         fOut.flush();
         write(document.getDoctype(), fOut);

         write(document.getDocumentElement(), fOut);
         break;
      case Node.DOCUMENT_TYPE_NODE:
         DocumentType doctype = (DocumentType)node;
         fOut.print("<!DOCTYPE ");
         fOut.print(doctype.getName());
         String publicId = doctype.getPublicId();
         String systemId = doctype.getSystemId();

         if(publicId != null) {
            fOut.print(" PUBLIC '");
            fOut.print(publicId);
            fOut.print("' '");
            fOut.print(systemId);
            fOut.print('\'');
         }
         else {
            fOut.print(" SYSTEM '");
            fOut.print(systemId);
            fOut.print('\'');
         }

         String internalSubset = doctype.getInternalSubset();

         if(internalSubset != null) {
            fOut.println(" [");
            fOut.print(internalSubset);
            fOut.print(']');
         }

         fOut.print(">\n");
         break;
      case Node.ELEMENT_NODE:
         fOut.print('<');
         fOut.print(node.getNodeName());
         Attr[] attrs = getAttributes(node.getAttributes());

         for(int i = 0; i < attrs.length; i++) {
            Attr attr = attrs[i];
            fOut.print(' ');
            fOut.print(attr.getNodeName());
            fOut.print("=\"");
            fOut.print(Tool.escape(attr.getNodeValue()));
            fOut.print('"');
         }

         fOut.print(">");
         fOut.flush();

         Node child = node.getFirstChild();

         while(child != null) {
            write(child, fOut);
            child = child.getNextSibling();
         }

         break;
      case Node.ENTITY_REFERENCE_NODE:
         fOut.print('&');
         fOut.print(node.getNodeName());
         fOut.print(';');
         fOut.flush();
         break;
      case Node.CDATA_SECTION_NODE:
         fOut.print("<![CDATA[");
         fOut.print(node.getNodeValue());
         fOut.print("]]>");
         fOut.flush();
         break;
      case Node.TEXT_NODE:
         String text = node.getNodeValue().trim();

         if(text.length() > 0) {
            fOut.print(text);
            fOut.flush();
         }

         break;
      case Node.PROCESSING_INSTRUCTION_NODE:
         fOut.print("<?");
         fOut.print(node.getNodeName());
         String data = node.getNodeValue();

         if(data != null && data.length() > 0) {
            fOut.print(' ');
            fOut.print(data);
         }

         fOut.println("?>\n");
         fOut.flush();
         break;
      }

      if(type == Node.ELEMENT_NODE) {
         fOut.print("</");
         fOut.print(node.getNodeName());
         fOut.print(">\n");
         fOut.flush();
      }
   }

   /**
    * Get attributes.
    */
   protected static Attr[] getAttributes(NamedNodeMap attrs) {
      int len = (attrs != null) ? attrs.getLength() : 0;
      Attr[] array = new Attr[len];

      for(int i = 0; i < len; i++) {
         array[i] = (Attr)attrs.item(i);
      }

      return array;
   }

   /**
    * Get the String value of the element. This method returns a null
    * if the element does not contain a value.
    */
   public static String getValue(Node elem) {
      return getValue(elem, false);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object
    */
   public static String getValue(Node elem, boolean multiline) {
      String val = "";
      NodeList nlist = elem.getChildNodes();
      int len = nlist.getLength(); // optimize

      for(int i = 0; i < len; i++) {
         Node child = nlist.item(i);

         if(child.getNodeType() == Element.TEXT_NODE) {
            String sval = child.getNodeValue();

            // empty string in tag (not in cdata) is ignored
            if(sval.trim().length() > 0) {
               val = multiline ? (val + sval) : sval;
            }
         }
         else if(child.getNodeType() == Element.CDATA_SECTION_NODE) {
            val = child.getNodeValue();
            break;
         }
         /*
         else if(child.getNodeType() == Element.ENTITY_REFERENCE_NODE) {
            String sval = "&" + child.getNodeName() + ";";
            String eval = (String) decoding.get(sval);

            if(eval != null) {
               val = multiline ? (val + eval) : eval;
            }
         }
         */
      }

      // remove surrounding newlines
      while(val.startsWith("\n")) {
         val = val.substring(1);
      }

      while(val.endsWith("\n")) {
         val = val.substring(0, val.length() - 1);
      }

      return val.equals("") ? null : val;
   }

   /**
    * Set value.
    */
   public static void setValue(Node elem, String value) {
      Document doc= elem.getOwnerDocument();
      Node newNode = doc.createTextNode(value);
      elem.appendChild(newNode);
   }

   /**
    * Replace value.
    */
   public static void replaceValue(Node elem, String value) {
      Node child = elem.getChildNodes().item(0);
      Document doc = elem.getOwnerDocument();
      Node newNode = null;

      if(child.getNodeType() == Element.CDATA_SECTION_NODE) {
         newNode = doc.createCDATASection(value);
      }
      else if(child.getNodeType() == Element.TEXT_NODE) {
         newNode = doc.createTextNode(value);
      }

      if(newNode != null) {
         elem.replaceChild(newNode, child);
      }
   }

   /**
    * Add cdata value.
    */
   public static void addCDATAValue(Node elem, String value) {
      Document doc = elem.getOwnerDocument();
      Node newNode = doc.createCDATASection(value);
      elem.appendChild(newNode);
   }

   /**
    * Write XML as byte stream.
    */
   public static void writeXMLSerializableAsData(
      DataOutputStream dos, XMLSerializable value)
         throws IOException
   {
      StringWriter writer = new StringWriter();
      value.writeXML(new PrintWriter(writer));
      ArrayList<String> strings = getStrings(writer.toString());
      dos.writeInt(strings.size());

      for(String str : strings) {
         dos.writeUTF(str);
      }
   }

   /**
    * Split the string to an array, since method writeUTF in DataOutputStream
    * allow no more than 65535 characters once.
    */
   private static ArrayList<String> getStrings(String str) {
      int start = 0;
      int end = countUTFBytes(str);
      ArrayList<String> array = new ArrayList<>();

      while(true) {
         if(end == -1) {
            array.add(str);

            return array;
         }

         array.add(str.substring(start, end));
         start = end;
         str = str.substring(start, str.length());
         end = countUTFBytes(str);
         start = 0;
      }
   }

   /**
    * Get a proper cursor to split the string.
    */
   private static int countUTFBytes(String str) {
      int strlen = str.length();
      int utflen = 0;
      int c = 0;

      for (int i = 0; i < strlen; i++) {
         c = str.charAt(i);

         if(c >= 0x0001 && c <= 0x007F) {
            utflen++;
         }
         else if(c > 0x07FF) {
            utflen += 3;
         }
         else {
            utflen += 2;
         }

         if(utflen > 65535) {
            return i - 1;
         }
      }

      return -1;
   }
}
