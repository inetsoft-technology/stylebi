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
package inetsoft.uql.asset.io;

import inetsoft.util.XMLTransformer;
import org.w3c.dom.*;

import java.util.*;

/**
 * This abstract class implement transform method to convert
 * old version asset.dat.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public abstract class WorksheetTransformer extends XMLTransformer {
   /**
    * Transform an xml document.
    */
   @Override
   public Node transform(Node doc) throws Exception {
      if(doc == null) {
         throw new Exception(" Missing document object!");
      }

      currentDoc = (Document) doc;
      NodeList nlist = currentDoc.getElementsByTagName("worksheet");
      Element root = currentDoc.getDocumentElement();

      if(nlist == null || nlist.getLength() <= 0) {
         throw new Exception(" Empty document object!");
      }

      for(int i = 0; i < nlist.getLength(); i++) {
         Element worksheet = (Element) nlist.item(i);

         if(worksheet == null) {
            throw new Exception(" Empty document object!");
         }

         processWorksheet(worksheet);
      }

      return currentDoc;
   }

   /**
    * Transform asset.dat.
    */
   private void processWorksheet(Element elem) throws Exception {
      NodeList nlist = elem.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         Node node = nlist.item(i);

         if(node.getNodeType() == Node.ELEMENT_NODE) {
            if(node.getNodeName().equals("Version")) {
               try {
                  String ver = getValue(node);
                  float version = Float.parseFloat(ver);
                  float currentVersion = Float.parseFloat(getVersion());
                  if(version > currentVersion) {
                     return;
                  }
               }
               catch(NumberFormatException e) {
                  return;
               }

               Element verElem = createElement("Version");
               setValue(verElem, getNextVersion());
               originalVersion = getValue(node);
               elem.replaceChild(verElem, node);

               continue;
            }

            processElement((Element) node);
         }
      }
   }

   /**
    * Transform each registry elements.
    * Handle each element changes right here.
    */
   protected abstract void processElement(Element elem) throws Exception;

   /**
    * Return current worksheet version.
    */
   protected abstract String getVersion();

   /**
    * Return the following version number.
    */
   protected abstract String getNextVersion();

   /**
    * Create an empty xml Element object.
    */
   protected final Element createElement(String tag) {
      Element elem = currentDoc.createElement(tag);
      return elem;
   }

   /**
    * Check if a string is blank.
    */
   protected static final boolean isBlank(String str) {
      if(str == null || str.equals("")) {
         return true;
      }

      return false;
   }

   /**
    * Replace value of a node.
    * @param elem the specified node.
    * @value the specified string value to be replaced.
    */
   protected static final void replaceValue(Node elem, String value) {
      Node child = elem.getChildNodes().item(0);

      Document doc= elem.getOwnerDocument();

      Node newNode = doc.createTextNode(value);
      elem.replaceChild(newNode, child);
   }

   /**
    * Get the attribute of an element. If the attribute does not exist this
    * method returns a null value
    * @param elem the Element that contains the attribute
    * @param name the name of the Attribute to get
    */
   protected static final String getAttribute(Element elem, String name) {
      Attr attr = elem.getAttributeNode(name);
      return (attr == null) ? null : attr.getValue();
   }

   /**
    * Get a child element by its tag name. If more than one elements have the
    * same tag name, the first one will be returned.
    * @param elem the parent element
    * @param name the tag name of the child element to retrieve
    * @return the retrieved child element if exists, null otherwise
    */
   public static Element getChildNodeByTagName(Element elem, String name) {
      NodeList nlist = elem.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         Node node = nlist.item(i);

         if((node instanceof Element) && name.equals(node.getNodeName())) {
            return (Element) node;
         }
      }

      return null;
   }

   /**
    * Get all the children of the element that has name as its tag name.
    * @param elem the parent element
    * @param name the tag name of the child node to retrieve
    * @return a NodeList of child nodes
    */
   public static NodeList getChildNodesByTagName(Element elem,
                       String name) {
      NodeList nlist = elem.getChildNodes();
      final int len = nlist.getLength();

      if(nlist != null && len > 0) {
         class NodeListImpl implements NodeList {
            @Override
            public int getLength() {
               return elems.size();
            }

            @Override
            public Node item(int index) {
               return (Node) elems.elementAt(index);
            }

            public void addItem(Node node) {
               elems.addElement(node);
            }

            Vector elems = new Vector(len);
         }

         NodeListImpl children = new NodeListImpl();

         for(int i = 0; i < len; i++) {
            if(nlist.item(i) instanceof Element) {
               // remove instanceof is slightly faster
               Element node = (Element) nlist.item(i);

               if(node.getTagName().equals(name)) {
                  children.addItem(node);
               }
            }
         }

         return children;
      }

      return nlist;
   }

   /**
    * Get the String value of the element. This method returns a null
    * if the element does not contain a value.
    */
   protected static final String getValue(Node elem) {
      return getValue(elem, false);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object
    */
   protected static final String getValue(Node elem, boolean multiline) {
      return getValue(elem, multiline, true);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object
    * @param trim true if trim newlines
    */
   protected static final String getValue(Node elem, boolean multiline,
                                          boolean trim) {
      String val = "";
      NodeList nlist = elem.getChildNodes();
      int len = nlist.getLength(); // optimize

      if(len == 0) {
         return elem.getNodeValue();
      }

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
    * Get the first element matches with special tag.
    */
   protected static final Element getElementByTag(Element elem, String tag) {
      NodeList nodes = elem.getChildNodes();

      for(int i = 0; i < nodes.getLength(); i++) {
         if(nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
            Element selem = (Element) nodes.item(i);

            if(selem.getTagName().equalsIgnoreCase(tag)) {
               return selem;
            }
         }
      }

      return null;
   }

   /**
    * Add a cdata value node to a node.
    * @param elem the specified node.
    * @param value the specified string value to be contained in cdata node.
    */
   protected static final void addCDATAValue(Node elem, String value) {
      Document doc= elem.getOwnerDocument();

      Node newNode = doc.createCDATASection(value);
      elem.appendChild(newNode);
   }
   
   /**
    * replace the cdata value of a node
    * @param elem the specified node.
    * @param value the specified string value to be replaced to the node
    */   
   protected static final void replaceCDATAValue(Node elem, String value) {
      Document doc = elem.getOwnerDocument();
      Node newNode = doc.createCDATASection(value);
      NodeList nlist = elem.getChildNodes();
      int len = nlist.getLength(); // optimize

      if(len == 0) {
         return;
      }

      for(int i = 0; i < len; i++) {
         Node child = nlist.item(i);

         if(child.getNodeType() == Element.CDATA_SECTION_NODE ||
            child.getNodeType() == Element.TEXT_NODE)
         {
            elem.replaceChild(newNode, child);
            break;
         }
      }
   }

   /**
    * Set value to a node.
    * @param elem the specified node.
    * @param value the specified string value to be set to the node.
    */
   protected static final void setValue(Node elem, String value) {
      Document doc= elem.getOwnerDocument();

      Node newNode = doc.createTextNode(value);
      elem.appendChild(newNode);
   }

   /**
    * Get the neighbor element.
    */
   protected Element getNeighborElement(Element elem, int delta) {
      Node pnode = elem.getParentNode();

      if(pnode == null) {
         return null;
      }

      NodeList nlist = pnode.getChildNodes();
      List elems = new ArrayList();
      int index = -1;

      for(int i = 0; i < nlist.getLength(); i++) {
         Node node = nlist.item(i);

         if(node instanceof Element) {
            if(node == elem) {
               index = elems.size();
            }

            elems.add(node);
         }
      }

      if(index == -1 || index + delta < 0 || index + delta >= elems.size()) {
         return null;
      }

      return (Element) elems.get(index + delta);
   }

   public String getOriginalVersion() {
      return originalVersion;
   }

   public void setOriginalVersion(String originalVersion) {
      this.originalVersion = originalVersion;
   }

   private static final String[][] encoding = {
      {"&amp;", "&lt;", "&gt;", "&#39;", "&quot;"},
      {"&", "<", ">", "'", "\""}
   };

   private static final Hashtable decoding = new Hashtable();

   static {
      for(int i = 0; i < encoding[0].length; i++) {
         decoding.put(encoding[0][i], encoding[1][i]);
      }
   }

   private String originalVersion;
   protected Document currentDoc;
}
