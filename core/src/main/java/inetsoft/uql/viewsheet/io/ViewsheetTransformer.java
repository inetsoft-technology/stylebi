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
package inetsoft.uql.viewsheet.io;

import inetsoft.util.Tool;
import inetsoft.util.XMLTransformer;
import org.w3c.dom.*;

import java.util.*;

/**
 * This abstract calss implement transform menthod to convert
 * old version asset.dat.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class ViewsheetTransformer extends XMLTransformer {
   /**
    * Transform an xml document.
    */
   @Override
   public Node transform(Node doc) throws Exception {
      if(doc == null) {
         throw new Exception(" Missing document object!");
      }

      currentDoc = (Document) doc;
      Node vnode = getViewSheetNode(currentDoc);
      NodeList nlist = vnode != null ?
         Tool.getChildNodesByTagName(vnode, "assembly") :
         currentDoc.getElementsByTagName("assembly");

      if(nlist == null) {
         return doc;
      }

      Element assembly = null;

      try {
         assembly = (Element) nlist.item(0);
      }
      catch(Exception ex) {
         // ignore
      }

      if(assembly == null) {
         return doc;
      }

      final boolean shouldTransform = processAssembly(assembly);

      if(shouldTransform) {
         processBookmarks();
      }

      return currentDoc;
   }

   /**
    * Transform asset.dat.
    *
    * @return true if the viewsheet is eligible to be transformed by this transformer
    */
   private boolean processAssembly(Element elem) throws Exception {
      final NodeList nlist = elem.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         final Node node = nlist.item(i);

         if(node.getNodeType() == Node.ELEMENT_NODE) {
            if("Version".equals(node.getNodeName())) {
               String ver = getValue(node);

               // there is no transformer for 12.1 -> 12.2, and 12.2 used "12.2" as the
               // version so 12.1 viewsheets would not be updated by the 12_3Transformer
               if("12.1".equals(ver)) {
                  ver = "12.2";
               }

               if(!getVersion().equals(ver)) {
                  return false;
               }

               final Element verElem = createElement("Version");
               final String nextVersion = getNextVersion();
               setValue(verElem, nextVersion);
               elem.replaceChild(verElem, node);
               appendViewsheetNode(elem);
            }
            else {
               processElement((Element) node);
            }
         }
      }

      return true;
   }

   /**
    * Process a viewsheet's bookmarks.
    */
   protected void processBookmarks() throws Exception {
      // no-op: overridden in subclass if necessary.
   }

   /**
    * Append viewsheet node to root.
    */
   protected void appendViewsheetNode(Element elem) {
      // do nothing, in version 10.2 will done
   }

   /**
    * Get viewsheet node.
    */
   protected Node getViewSheetNode(Document doc) {
      float currentVersion = Float.parseFloat(getVersion());

      if(currentVersion >= 11.0) {
         // override after version 11.0,
         // because has appended viewsheet node to root in version 10.2,
         Node vnode = Tool.getChildNodeByTagName(doc, "viewsheet");
         Node vnode1 = vnode == null ? null :
            Tool.getChildNodeByTagName(vnode, "viewsheet");
         return vnode1 == null ? vnode : vnode1;
      }

      return Tool.getChildNodeByTagName(doc, "viewsheet");
   }

   /**
    * Transform each registry elements.
    * Handle each element changes right here.
    */
   protected abstract void processElement(Element elem) throws Exception;

   /**
    * Return current template version.
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
      return currentDoc.createElement(tag);
   }

   /**
    * Check if a string is blank.
    */
   protected static boolean isBlank(String str) {
      return str == null || str.equals("");
   }

   /**
    * Replace value of a node.
    * @param elem the specified node.
    * @value the specified string value to be replaced.
    */
   protected static void replaceValue(Node elem, String value) {
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
   protected static String getAttribute(Element elem, String name) {
      if(elem == null) {
         return null;
      }

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
      if(elem == null) {
         return null;
      }

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
   protected static NodeList getChildNodesByTagName(Element elem, String name) {
      NodeList nlist = elem.getChildNodes();
      final int len = nlist.getLength();

      if(len > 0) {
         class NodeListImpl implements NodeList {
            @Override
            public int getLength() {
               return nodes.size();
            }

            @Override
            public Node item(int index) {
               return nodes.get(index);
            }

            public void addItem(Node node) {
               nodes.add(node);
            }

            private final List<Node> nodes = new ArrayList<>(len);
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
   protected static String getValue(Node elem) {
      return getValue(elem, false);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object
    */
   protected static String getValue(Node elem, boolean multiline) {
      return getValue(elem, multiline, true);
   }

   /**
    * Get the String value of the element. This method returns a null if
    * the element does not contain a value.
    * @param elem specifies the Element object
    * @param trim true if trim newlines
    */
   protected static String getValue(Node elem, boolean multiline,
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
            String eval = decoding.get(sval);

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
   protected static Element getElementByTag(Element elem, String tag) {
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
   protected static void addCDATAValue(Node elem, String value) {
      Document doc= elem.getOwnerDocument();

      Node newNode = doc.createCDATASection(value);
      elem.appendChild(newNode);
   }

   /**
    * Replace a cdata value node.
    * @param elem the specified node.
    * @param value the specified string value to be contained in cdata node.
    */
   protected static void replaceCDATAValue(Node elem, String value) {
      Document doc= elem.getOwnerDocument();
      Node newNode = doc.createCDATASection(value);

      NodeList nlist = elem.getChildNodes();
      int len = nlist.getLength(); // optimize

      if(len == 0) {
         return;
      }

      for(int i = 0; i < len; i++) {
         Node child = nlist.item(i);

         if(child.getNodeType() == Element.CDATA_SECTION_NODE) {
            elem.replaceChild(newNode, child);
            break;
         }
      }
   }

   /**
    * Replace a value text or cdata section node.
    * @param elem the specified node.
    * @param value the specified string value to be contained in cdata node.
    */
   protected static void replaceNodeValue(Node elem, String value) {
      Document doc= elem.getOwnerDocument();
      NodeList nlist = elem.getChildNodes();
      int len = nlist.getLength();

      for(int i = 0; i < len; i++) {
         Node child = nlist.item(i);

         if(child.getNodeType() == Element.CDATA_SECTION_NODE) {
            elem.replaceChild(doc.createCDATASection(value), child);
            break;
         }
         else if(child.getNodeType() == Element.TEXT_NODE) {
            elem.replaceChild(doc.createTextNode(value), child);
            break;
         }
      }
   }

   /**
    * Set value to a node.
    * @param elem the specified node.
    * @param value the specified string value to be set to the node.
    */
   protected static void setValue(Node elem, String value) {
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
      List<Node> elems = new ArrayList<>();
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

   /**
    * Reset chart aggregate ref's full name.
    */
   protected void processAggFullName(Element rnode) {
      if(rnode != null) {
         String value = Tool.getValue(rnode);

         // if the value is an expression variale, don't bc it
         if(value != null && !value.startsWith("=")) {
            int idx = value.indexOf(" of ");

            if(idx != -1) {
               StringBuilder sb = new StringBuilder();
               sb.append(value, 0, idx);
               sb.append("(");

               value = value.substring(idx + 4);
               idx = value.indexOf(" and ");

               if(idx != -1) {
                  sb.append(value, 0, idx);
                  sb.append(", ");
                  sb.append(value.substring(idx + 5));
               }
               else {
                  sb.append(value);
               }

               sb.append(")");
               replaceNodeValue(rnode, sb.toString());
            }
         }
      }
   }

   private static final Map<String, String> decoding;

   static {
      Map<String, String> map = new HashMap<>();
      map.put("&amp;", "&");
      map.put("&lt;", "<");
      map.put("&gt;", ">");
      map.put("&#39;", "'");
      map.put("&quot;", "\"");
      decoding = Collections.unmodifiableMap(map);
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string
    */
   public static String legacyByteDecode(String encString) {
      if(encString == null) {
         return null;
      }

      StringBuilder str = new StringBuilder();
      boolean arrayEnc = encString.startsWith("^[") && encString.endsWith("]^");
      int len = encString.length();

      for(int i = 0; i < len; i++) {
         char ch = encString.charAt(i);

         // @by larryl, skip the <![CDATA[]]> tags
         if(ch == '[' && !encString.startsWith("![CDATA[", i) &&
            (i < 8 || !encString.startsWith("<![CDATA[", i - 8)) &&
            // @by larryl, ignore ^[x]^, which is used for encoding array
            (!arrayEnc || i > 1))
         {
            int idx = encString.indexOf(']', i + 1);

            if(idx > i + 1) {
               try {
                  ch = (char) Integer.parseInt(encString.substring(i + 1, idx),
                     16);
                  i = idx;
               }
               catch(NumberFormatException ex) {
                  // if can't parse, treated as regular char
               }
            }
         }

         str.append(ch);
      }

      return str.toString();
   }

   protected Document currentDoc;
}
