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
package inetsoft.uql.xmla;

import inetsoft.util.xml.XMLPParser;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler for discover or execute.
 *
 * @version 10.1, 6/8/2009
 * @author InetSoft Technology Corp
 */
public class SAXHandler extends DefaultHandler {
   /**
    * Receive notification of the start of an element.
    *
    * <p>By default, do nothing.  Application writers may override this
    * method in a subclass to take specific actions at the start of
    * each element (such as allocating a new tree node or writing
    * output to a file).</p>
    *
    * @param uri The Namespace URI, or the empty string if the
    *        element has no Namespace URI or if Namespace
    *        processing is not being performed.
    * @param localName The local name (without prefix), or the
    *        empty string if Namespace processing is not being
    *        performed.
    * @param qName The qualified name (with prefix), or the
    *        empty string if qualified names are not available.
    * @param attributes The attributes attached to the element.  If
    *        there are no attributes, it shall be an empty
    *        Attributes object.
    * @exception org.xml.sax.SAXException Any SAX exception, possibly
    *            wrapping another exception.
    * @see org.xml.sax.ContentHandler#startElement
    */
   public void startElement() throws SAXException {
      lastTag = pparser.getName();

      if("cxmla:return".equals(lastTag)) {
         cubeType = Cube.MONDRIAN;
      }

      if("return".equals(lastTag) &&
         pparser.getAttributeValue("xmlns:xsd") != null) {
         cubeType = Cube.SAP;
      }

      if("xsd:simpleType".equals(lastTag) &&
         "uuid".equals(pparser.getAttributeValue("name")))
      {
         if(!Cube.MONDRIAN.equals(cubeType) && !Cube.SAP.equals(cubeType)) {
            cubeType = Cube.SQLSERVER;
         }
      }

      // process error
      if("Error".equals(lastTag)) {
         String error = pparser.getAttributeValue("Description");

         if(error != null && error.trim().length() > 0) {
            throw new RuntimeException(error);
         }
      }
   }

   /**
    * Receive notification of character data inside an element.
    *
    * <p>By default, do nothing.  Application writers may override this
    * method to take specific actions for each chunk of character data
    * (such as adding the data to a node or buffer, or printing it to
    * a file).</p>
    *
    * @param ch The characters.
    * @param start The start position in the character array.
    * @param length The number of characters to use from the
    *               character array.
    * @exception org.xml.sax.SAXException Any SAX exception, possibly
    *            wrapping another exception.
    * @see org.xml.sax.ContentHandler#characters
    */
   public void characters() {
      if("faultstring".equals(lastTag)) {
         throw new RuntimeException(getText());
      }
   }

   /**
    * Receive notification of the end of an element.
    *
    * <p>By default, do nothing.  Application writers may override this
    * method in a subclass to take specific actions at the end of
    * each element (such as finalising a tree node or writing
    * output to a file).</p>
    *
    * @param uri The Namespace URI, or the empty string if the
    *        element has no Namespace URI or if Namespace
    *        processing is not being performed.
    * @param localName The local name (without prefix), or the
    *        empty string if Namespace processing is not being
    *        performed.
    * @param qName The qualified name (with prefix), or the
    *        empty string if qualified names are not available.
    * @exception org.xml.sax.SAXException Any SAX exception, possibly
    *            wrapping another exception.
    * @see org.xml.sax.ContentHandler#endElement
    */
   public void endElement() {
      lastTag = pparser.getName();
   }

   /**
    * Get cube type.
    * @return cube type.
    */
   public String getCubeType() {
      return cubeType;
   }

   /**
    * Set XMLPParser.
    * @param pparser the specified XMLPParser.
    */
   public void setXMLPParser(XMLPParser pparser) {
      this.pparser = pparser;
   }

   /**
    * Get XMLPParser.
    * @return XMLPParser.
    */
   public XMLPParser getXMLPParser() {
      return pparser;
   }

   /**
    * Get trimed text.
    */
   protected String getText() {
      String txt = pparser.getText();

      while(txt.startsWith("\n")) {
         txt = txt.substring(1, txt.length());
      }

      return txt;
   }

   protected String lastTag;
   protected XMLPParser pparser;
   private String cubeType = Cube.ESSBASE;
   protected XMLAQuery query;
}