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
package inetsoft.uql.util;

import inetsoft.uql.*;
import inetsoft.uql.schema.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.text.ParseException;
import java.util.Enumeration;

/**
 * XML related utility methods.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XMLUtil {
   /**
    * Create a XNode tree from XML DOM tree.
    */
   public static XNode createTree(Node elem, XTypeNode xtype) {
      elem = Tool.findRootElement(elem);
      XNode root = new XNode(elem.getNodeName());

      createTree(root, elem, xtype);
      return root;
   }

   /**
    * Create a XNode tree from XML DOM tree.
    */
   public static void createTree(XNode root, Node elem, XTypeNode xtype) {
      // type value mismatch, ignore
      if(xtype == null) {
         throw new RuntimeException("Node type missing: " + root);
      }

      NodeList nlist = elem.getChildNodes();
      XNode child = null;
      // look through the children of the xml node
      Runtime runtime = Runtime.getRuntime();

      for(int i = 0; i < nlist.getLength(); i++) {
         Node node = nlist.item(i);
         String value = null;

         // if this is a value node
         if(node.getNodeType() == Node.CDATA_SECTION_NODE) {
            value = trimEnd(node.getNodeValue());
         }
         // only non-blank values in plain text nodes are used
         else if(node.getNodeType() == Node.TEXT_NODE) {
            value = trimEnd(node.getNodeValue());

            if(value.length() == 0) {
               value = null;
            }
         }

         if(value != null) {
            try {
               if(root instanceof XValueNode) {
                  ((XValueNode) root).parse(value);
               }
               else {
                  root.setValue(value);
               }
            }
            catch(Exception e) {
               LOG.error("Failed to parse XValueNode", e);
            }
         }

         if(node.getNodeType() != Node.ELEMENT_NODE) {
            // delete used node to conserve space
            elem.removeChild(node);
            Tool.freeNode(node);
            i--;
            continue;
         }

         // @by larryl, store the actual name in name attribute instead
         // of tag (tag needs to be identifier)
         String tag = null;

         if(node instanceof Element) {
            tag = Tool.getAttribute((Element) node, "_node_name");

            // for 5.1 backward compatibility
            if(tag == null) {
               tag = ((Element) node).getTagName();
            }
         }

         XTypeNode roottype = xtype.getTypeNode(root.getPath());

         // if an unknown node, ignore
         if(roottype == null) {
            // delete used node to conserve space
            elem.removeChild(node);
            Tool.freeNode(node);
            i--;
            continue;
         }

         XTypeNode subtype = (root instanceof XSequenceNode) ?
            roottype : roottype.getTypeNode(root.getName() + "." + tag);

         // missing type definition, use string type
         if(subtype == null) {
            subtype = new StringType();
         }

         XNode sub = subtype.newInstance(false);

         populateAttributes(sub, node, subtype);
         sub.removeAllChildren();

         // if sequence, create an empty sequence node to hold it
         if(subtype.getMaxOccurs() > 1 && root.getChild(tag) == null) {
            XSequenceNode seq = new XSequenceNode(tag);
            String cls = (String) subtype.getAttribute("container_class");

            if(cls != null) {
               seq.setAttribute("container_class", cls);
            }

            root.addChild(seq);
         }

         sub.setName(tag);
         root.addChild(child = sub);
         createTree(sub, node, xtype);

         // delete used node to conserve space
         elem.removeChild(node);
         Tool.freeNode(node);
         i--;
      }
   }

   /**
    * Copy attributes from one type node to another.
    */
   public static void copyAttributes(XTypeNode from, XTypeNode to) {
      for(int i = 0; i < from.getAttributeCount(); i++) {
         to.addAttribute(from.getAttribute(i));
      }

      Enumeration<String> keys = from.getAttributeNames();

      while(keys.hasMoreElements()) {
         String key = keys.nextElement();
         to.setAttribute(key, from.getAttribute(key));
      }

      to.setMinOccurs(from.getMinOccurs());
      to.setMaxOccurs(from.getMaxOccurs());
   }

   /**
    * Print a value tree.
    */
   public static void printTree(XNode node, String ind) {
      if(node == null ||
         !(node.getParent() instanceof XSequenceNode) &&
         XUtil.isRecursive(null, node))
      {
         return;
      }

      if(node instanceof XSequenceNode) {
         System.err.print(ind + node.getName() + "[" + node.getChildCount() +
            "]");
      }
      else {
         System.err.print(ind + node.getName() + "<" + node.getChildCount() +
            ">");
      }

      Enumeration<String> attrs = node.getAttributeNames();

      while(attrs.hasMoreElements()) {
         String attr = attrs.nextElement();

         System.err.print(" " + attr + "=" + node.getAttribute(attr));
      }

      System.err.println(":" + node.getValue());

      if(node instanceof XTableNode) {
         XTableNode table = (XTableNode) node;

         // print header
         System.err.print(ind + "  ");
         for(int i = 0; i < table.getColCount(); i++) {
            System.err.print(table.getName(i) + ",");
         }

         System.err.println();

         while(table.next()) {
            System.err.print(ind + "  ");
            for(int i = 0; i < table.getColCount(); i++) {
               System.err.print(table.getObject(i) + ",");
            }

            System.err.println();
         }
      }

      boolean first = ind.length() == 0;

      ind += "  ";
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);

         printTree(child, ind);
      }
   }

   /**
    * Print a type tree.
    */
   public static void printTree(XTypeNode node, String ind) {
      if(node == null) {
         return;
      }

      if(node instanceof UserDefinedType) {
         XTypeNode type = ((UserDefinedType) node).getUserType();

         System.err.println(ind + node.getName() + ":(u) " + node.getType() +
            " " + node.getMinOccurs() + " - " + node.getMaxOccurs());
         printTree(type, ind);
         return;
      }
      else {
         String name = node.getClass().getName();

         System.err.println(ind + node.getName() + ": " + node.getType() + " " +
            node.getMinOccurs() + " - " + node.getMaxOccurs());
      }

      boolean first = ind.length() == 0;

      ind += "  ";
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);

         printTree(child, ind);
      }
   }

   /**
    * Set the attributes in the node from the DOM element.
    */
   private static void populateAttributes(XNode node, Node elem, XTypeNode tp) {
      if((node instanceof XValueNode) && (elem instanceof Element)) {
         ((XValueNode) node).setVariable(Tool.getAttribute((Element) elem,
            "variable"));

         // check null setting, this should be done in the XValueNode
         // but because the way the tree is currently created, it
         // is done here, should consider moving the logic for
         // XValueNode parsing to XValueNode
         String isnull = Tool.getAttribute((Element) elem, "null");

         if((isnull == null || !isnull.equalsIgnoreCase("true")) &&
            node.getValue() == null) {
            try {
               ((XValueNode) node).parse("");
            }
            catch(ParseException ex) {
            }
         }
      }

      NamedNodeMap map = elem.getAttributes();

      // @by mikec, for each attribute, use the predefined format to parse it.
      for(int i = 0; i < map.getLength(); i++) {
         Node attr = map.item(i);
         final String name = attr.getNodeName();

         // _node_name is internally used to store the name of the node
         // we don't add to attr list otherwise will get multiple
         if(name.equals("_node_name")) {
            continue;
         }

         final XTypeNode x = tp.getAttributeType(name);
         XNode n = null;
         Object value = attr.getNodeValue();

         if(x != null) {
            n = x.newInstance(false);
         }

         if(n instanceof XValueNode && value != null) {
            try {
               ((XValueNode) n).parse(value.toString());
               value = n.getValue();
            }
            catch(ParseException ex) {
            }
         }

         node.setAttribute(name, value);
      }
   }

   // trim white space from the end of the string
   private static String trimEnd(String str) {
      int i = str.length() - 1;

      for(; i >= 0 && Character.isWhitespace(str.charAt(i)); i--) {

      }

      return str.substring(0, i + 1);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XMLUtil.class);
}

