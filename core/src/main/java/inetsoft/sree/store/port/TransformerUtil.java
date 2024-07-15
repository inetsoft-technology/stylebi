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
package inetsoft.sree.store.port;

import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.Hashtable;

/**
 * XML transformer utility functions.
 *
 * @version 7.0, 3/15/2005
 * @author InetSoft Technology Corp
 */
public class TransformerUtil {
   /**
    * Parse XML Data from an InputStream.
    * @param input - source InputStream with the XML Data.
    */
   public static Document parseXML(InputStream input) throws Exception {
      return Tool.safeParseXMLByDocumentBuilder(input);
   }

   /**
    * Load old file index. If loadind fails, create a empty xml file.
    */
   public static Document load(String path) throws IOException {
      File file = FileSystemService.getInstance().getFile(path);

      if(!file.exists()) {
         return null;
      }

      InputStream istream = new FileInputStream(file);

      if(istream != null) {
         istream = new BufferedInputStream(istream);
      }

      try {
         Document doc = parseXML(istream);
         return doc;
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      return null;
   }

   /**
    * Save in xml format.
    */
   public static void save(String xmlfile, Document doc) throws IOException
   {
      File file = FileSystemService.getInstance().getFile(xmlfile);
      OutputStream output = new FileOutputStream(file);

      try {
         Result result = new StreamResult(output);
         Source source = new DOMSource(doc);

         TransformerFactory factory = TransformerFactory.newInstance();
         factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
         Transformer trans = factory.newTransformer();
         trans.transform(source, result);
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }
      finally {
         if(output != null) {
            output.close();
         }
      }
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object
    * @param trim true if trim newlines
    */
   public static String getValue(Node elem, boolean multiline, boolean trim) {
      if(elem == null) {
         return null;
      }

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
         else if(child.getNodeType() == Element.ENTITY_REFERENCE_NODE) {
            String sval = "&" + child.getNodeName() + ";";
            String eval = (String) decoding.get(sval);

            if(eval != null) {
               val = multiline ? (val + eval) : eval;
            }
         }
      }

      if(val == null) {
         return null;
      }

      // remove surrounding newlines
      while(trim && val.startsWith("\n")) {
         val = val.substring(1);
      }

      while(trim && val.endsWith("\n")) {
         val = val.substring(0, val.length() - 1);
      }

      return val.equals("") ? null : val;
   }

   /**
    * Set the String value of the element.
    */
   public static void setValue(Node elem, String value) {
      if(elem == null) {
         return;
      }

      NodeList nlist = elem.getChildNodes();
      int len = nlist.getLength(); // optimize

      for(int i = 0; i < len; i++) {
         Node child = nlist.item(i);

         if(child.getNodeType() == Element.TEXT_NODE ||
            child.getNodeType() == Element.CDATA_SECTION_NODE)
         {
            child.setNodeValue(value);
         }
      }
   }

   // html encoding mapping
   private static final String[][] encoding2 = {
      {"&amp;", "&lt;", "&gt;", "&apos;", "&quot;"},
      {"&", "<", ">", "'", "\""}
   };

   // image data decoding map.
   private static Hashtable decoding;
   private static final Logger LOG = LoggerFactory.getLogger(TransformerUtil.class);

   static {
      decoding = new Hashtable();
      for(int i = 0; i < encoding2[0].length; i++) {
         decoding.put(encoding2[0][i], encoding2[1][i]);
      }
   }
}
