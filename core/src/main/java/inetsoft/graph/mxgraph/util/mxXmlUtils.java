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
package inetsoft.graph.mxgraph.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contains various XML helper methods for use with mxGraph.
 */
public class mxXmlUtils {

   private static final Logger log = Logger.getLogger(mxXmlUtils.class.getName());

   /**
    *
    */
   private static DocumentBuilderFactory documentBuilderFactory = null;

   /**
    *
    */
   public static DocumentBuilder getDocumentBuilder()
   {
      if(documentBuilderFactory == null) {
         documentBuilderFactory = DocumentBuilderFactory.newInstance();
         documentBuilderFactory.setExpandEntityReferences(false);
         documentBuilderFactory.setXIncludeAware(false);
         documentBuilderFactory.setValidating(false);

         try {
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         }
         catch(ParserConfigurationException e) {
            log.log(Level.SEVERE, "Failed to set feature", e);
         }
      }

      try {
         return documentBuilderFactory.newDocumentBuilder();
      }
      catch(Exception e) {
         log.log(Level.SEVERE, "Failed to construct a document builder", e);
      }

      return null;
   }

   /**
    * Returns a new document for the given XML string. External entities and DTDs are ignored.
    *
    * @param xml String that represents the XML data.
    *
    * @return Returns a new XML document.
    */
   public static Document parseXml(String xml)
   {
      try {
         return getDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      }
      catch(Exception e) {
         log.log(Level.SEVERE, "Failed to parse XML", e);
      }

      return null;
   }

   /**
    * Returns a string that represents the given node.
    *
    * @param node Node to return the XML for.
    *
    * @return Returns an XML string.
    */
   public static String getXml(Node node)
   {
      try {
         TransformerFactory factory = TransformerFactory.newInstance();
         factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
         Transformer tf = factory.newTransformer();

         tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
         tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

         StreamResult dest = new StreamResult(new StringWriter());
         tf.transform(new DOMSource(node), dest);

         return dest.getWriter().toString();
      }
      catch(Exception e) {
         log.log(Level.SEVERE, "Failed to convert XML object to string", e);
      }

      return "";
   }
}
