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
package inetsoft.uql.schema;

import inetsoft.uql.*;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

/**
 * A XTypeNode is a type definition. It can be one of the primitive
 * types as defined in the XSchema class. Or it can be a complex type
 * that is composed of children of other types.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XTypeNode extends XNode implements Comparable, XMLSerializable {
   /**
    * Specify '*' in the maxOccurs.
    */
   public static final int STAR = Integer.MAX_VALUE;
   /**
    * Create a type node.
    */
   public XTypeNode() {
   }

   /**
    * Create a type node.
    */
   public XTypeNode(String name) {
      super(name);
   }

   /**
    * Find the type node specified by the node path. The node path is the
    * path of the node in a value tree. The type of the tree is defined
    * by this XTypeNode.
    */
   public XTypeNode getTypeNode(String path) {
      XTypeNode node = this;

      // handle name with dots
      if(!path.startsWith(node.getName())) {
         return null;
      }

      // @by larryl, search from the end of the node name to allow, and
      // [] to be part of the name
      int nameEnd = node.getName().length() - 1;
      int idx = path.indexOf('.', nameEnd);
      String name2 = (idx >= 0) ? path.substring(0, idx) : path;
      int b1 = name2.indexOf('[', nameEnd);
      int b2 = name2.indexOf(']', nameEnd);

      if(b1 > 0 && b2 > 0) {
         name2 = name2.substring(0, b1);
      }

      if(node.getName().equals(name2)) {
         if(idx < 0) {
            return node;
         }

         // user defined type forwards search to the real type
         if(node instanceof UserDefinedType) {
            node = ((UserDefinedType) node).getUserType();
         }

         // find the child
         path = path.substring(idx + 1);

         for(int i = 0; i < node.getChildCount(); i++) {
            XTypeNode child = ((XTypeNode) node.getChild(i)).getTypeNode(path);

            if(child != null) {
               return child;
            }
         }
      }

      return null;
   }

   /**
    * Add an attribute to the type.
    */
   public void addAttribute(XTypeNode attr) {
      attrs.addElement(attr);
   }

   /**
    * Set an attribute at an index.
    *
    * @param index the specified index
    * @param attr the specified attribute
    */
   public void setAttribute(int index, XTypeNode attr) {
      attrs.setElementAt(attr, index);
   }

   /**
    * Get the number of attributes in this type.
    */
   public int getAttributeCount() {
      return attrs.size();
   }

   /**
    * Get the specified attribute.
    */
   public XTypeNode getAttribute(int idx) {
      return attrs.elementAt(idx);
   }

   /**
    * Get the index of an attribute.
    *
    * @param attr the specified attribute
    * @return the index of the specified attribute
    */
   public int getAttributeIndex(XTypeNode attr) {
      return attrs.indexOf(attr);
   }

   /**
    * Get the meta info.
    */
   public XMetaInfo getXMetaInfo() {
      return minfo;
   }

   /**
    * Set the meta info.
    */
   public void setXMetaInfo(XMetaInfo minfo) {
      this.minfo = minfo;
   }

   /**
    * Get the attribute type node with specified name.
    */
   public XTypeNode getAttributeType(String name) {
      for(int i = 0; i < attrs.size(); i++) {
         final XTypeNode x = getAttribute(i);

         if(x.getName().equals(name)) {
            return x;
         }
      }

      return null;
   }

   /**
    * Set the minimum number of instances allowed for this element.
    */
   public void setMinOccurs(int min) {
      minOccurs = min;
      suffix = null;
   }

   /**
    * Get the minimum number of instances allowed for this element.
    */
   public int getMinOccurs() {
      return minOccurs;
   }

   /**
    * Set the maximum number of instances allowed for this element.
    */
   public void setMaxOccurs(int max) {
      maxOccurs = max;
      suffix = null;
   }

   /**
    * Get the maximum number of instances allowed for this element.
    */
   public int getMaxOccurs() {
      return maxOccurs;
   }

   /**
    * Add a child to this node.
    * @param child child node.
    * @param sorted true to add child in sorted order.
    */
   @Override
   public void addChild(XNode child, boolean sorted) {
      // @by larryl, for XTypeNode, the assumption is that all children are
      // XTypeNode. if the unique is set to true, nodes with the same name
      // may be placed in a sequence node, and cause type cast exception.
      // This should not cause any backward compabitility problem since
      // a sequence node would cause problem anyway
      addChild(child, sorted, false);
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    */
   public XNode newInstance() {
      XNode root = new XNode(getName());
      String cls = (String) getAttribute("container_class");

      if(cls != null) {
         root.setAttribute("container_class", cls);
      }

      for(int i = 0; i < getChildCount(); i++) {
         XTypeNode type = (XTypeNode) getChild(i);

         if(type.getMaxOccurs() > 1) {
            XSequenceNode seq = new XSequenceNode(type.getName());

            if(cls != null) {
               seq.setAttribute("container_class", cls);
            }

            root.addChild(seq);

            for(int j = 0; j < type.getMinOccurs(); j++) {
               seq.addChild(type.newInstance());
            }
         }
         else if(type.getMinOccurs() > 0) {
            root.addChild(type.newInstance());
         }
      }

      return root;
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    * @param children true if tree should be created with children
    */
   public XNode newInstance(boolean children) {
      if(isPrimitive() || children) {
         return newInstance();
      }
      else {
         return new XNode(getName());
      }
   }

   /**
    * Return true if this is a primitive type.
    */
   public boolean isPrimitive() {
      return false;
   }

   /**
    * Check if this type is a numeric type.
    */
   public boolean isNumber() {
      return false;
   }

   /**
    * Check if this type is a date or time.
    */
   public boolean isDate() {
      return false;
   }

   /**
    * Check if two types are compatible. If two types are compatible, the
    * values of one value node can be assigned to the value of another
    * value node.
    */
   public boolean isCompatible(XTypeNode type) {
      return getType().equals(type.getType());
   }

   /**
    * Write this type and all types referenced by this type to XML.
    */
   public void writeTreeXML(PrintWriter writer) {
      Hashtable types = new Hashtable();

      walk(this, types);

      Enumeration elems = types.elements();

      while(elems.hasMoreElements()) {
         XTypeNode type = (XTypeNode) elems.nextElement();
         type.writeTypeXML(writer);
      }
   }

   /**
    * Check if typenode tp is a child node of this type node.
    */
   public boolean isTypeNodeExists(XTypeNode tp) {
      Hashtable types = new Hashtable();

      walk(this, types);

      return types.get(tp.getName()) != null;
   }

   /**
    * Walk the type tree and collect all type level types.
    */
   private void walk(XTypeNode root, Hashtable types) {
      // @by mikec, if a UserDefinedType have null UserType,
      // NPE will thrown here, catch null root to avoid the NPE.
      if(root == null || types.get(root.getName()) != null) {
         return;
      }

      types.put(root.getName(), root);

      if((root instanceof UserDefinedType) && !root.isPrimitive()) {
         walk(((UserDefinedType) root).getUserType(), types);
      }
      else {
         for(int i = 0; i < root.getChildCount(); i++) {
            XTypeNode child = (XTypeNode) root.getChild(i);

            if(!child.isPrimitive()) {
               walk(((UserDefinedType) child).getUserType(), types);
            }
         }
      }
   }

   /**
    * Parse the XML element that contains information on this
    * data source.
    */
   @Override
   public void parseXML(Element root) {
      NamedNodeMap map = root.getAttributes();

      for(int i = 0; i < map.getLength(); i++) {
         Node attr = map.item(i);
         String name = attr.getNodeName();
         String val = attr.getNodeValue();

         if(name.equals("minOccurs")) {
            setMinOccurs(Integer.parseInt(val));
         }
         else if(name.equals("maxOccurs")) {
            setMaxOccurs(val.equals("*") ?
                            XTypeNode.STAR :
                            Integer.parseInt(val));
         }
         else if(name.equals("format")) {
            setFormat(val);
         }
         else if(name.equals("container_class")) {
            setAttribute("container_class", val);
         }
      }

      // @by jasons handle attributes for primitive types
      if(isPrimitive() && root.getTagName().equals("element")) {
         NodeList nlist = Tool.getChildNodesByTagName(root, "attribute");

         for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
            Element elem = (Element) nlist.item(i);
            String name = Tool.getAttribute(elem, "name");
            String type = Tool.getAttribute(elem, "type");

            if(name != null && type != null) {
               XTypeNode attrnode = XSchema.createPrimitiveType(type);
               attrnode.setName(name);
               attrnode.parseXML(elem);
               addAttribute(attrnode);
            }
         }
      }
   }

   /**
    * Write the XML schema specification.
    */
   public void writeTypeXML(PrintWriter writer) {
      writer.print("<type name=\"" + getName() + "\"");
      writeAttributes(writer);
      writer.println(">");

      for(int i = 0; i < getChildCount(); i++) {
         XTypeNode elem = (XTypeNode) getChild(i);

         elem.writeXML(writer);
      }

      for(int i = 0; i < attrs.size(); i++) {
         XTypeNode attr = attrs.elementAt(i);

         writer.print("<attribute name=\"" + Tool.escape(attr.getName()) +
                         "\" type=\"" + Tool.escape(attr.getType()) + "\"");

         if(attr.getFormat() != null) {
            writer.print(" format=\"" + Tool.escape(attr.getFormat()) + "\"");
         }

         writer.println("/>");
      }

      writer.println("</type>");
   }

   /**
    * Write the XML schema specification.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<element name=\"" + Tool.escape(getName()) + "\" type=\"" +
                      getType() + "\"");

      writeAttributes(writer);

      // @by jasons, support attributes for primitive types
      if(isPrimitive() && getAttributeCount() > 0) {
         writer.println(">");

         for(int i = 0; i < getAttributeCount(); i++) {
            XTypeNode attr = getAttribute(i);

            if(attr.isPrimitive()) {
               writer.print("<attribute name=\"" + Tool.escape(attr.getName()) +
                               "\" type=\"" + attr.getType() + "\"");

               if(attr.getFormat() != null) {
                  writer.print(" format=\"" + Tool.escape(attr.getFormat()) +
                                  "\"");
               }

               writer.println("/>");
            }
         }

         writer.println("</element>");
      }
      else {
         writer.println("/>");
      }
   }

   /**
    * Write the min/max occurs attributes.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(getAttributeString());

      if(getFormat() != null) {
         writer.print(" format=\"" + Tool.escape(getFormat()) + "\"");
      }

      if(getMinOccurs() != 1) {
         writer.print(" minOccurs=\"" + getMinOccurs() + "\"");
      }

      if(getMaxOccurs() != 1) {
         if(getMaxOccurs() == XTypeNode.STAR) {
            writer.print(" maxOccurs=\"*\"");
         }
         else {
            writer.print(" maxOccurs=\"" + getMaxOccurs() + "\"");
         }
      }

      String cls = (String) getAttribute("container_class");

      if(cls != null) {
         writer.print(" container_class=\"" + cls + "\"");
      }
   }

   /**
    * Get additional attributes string.
    */
   protected void writeAdditionalAttributes(Map<String, Object> properties) {
      // NO-OP, delegate to subclasses.
   }

   /**
    * Get additional attributes string.
    */
   protected String getAttributeString() {
      return "";
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   public String getType() {
      return getName();
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   public String getSqlType() {
      Object val = getAttribute("sqltype");

      if(val == null) {
         return java.sql.Types.VARCHAR + "";
      }

      return val.toString();
   }


   /**
    * Set the format string for the type. The meaning of the format
    * depends on the data type. For example, for date related formats,
    * the format string is used to construct a SimpleDateFormat
    * object.
    */
   public void setFormat(String fmt) {
   }

   /**
    * Get the format string of this data type.
    */
   public String getFormat() {
      return null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      // set the suffix, cached
      if(suffix == null) {
         suffix = "";

         if(getMaxOccurs() == XTypeNode.STAR) {
            if(getMinOccurs() == 0) {
               suffix = "*";
            }
            else if(getMinOccurs() == 1) {
               suffix = "+";
            }
         }
         else if(getMaxOccurs() == 1 && getMinOccurs() == 0) {
            suffix = "?";
         }
      }

      return getName() + suffix;
   }

   /**
    * Compare to another object.
    */
   @Override
   public int compareTo(Object obj) {
      if(!(obj instanceof XTypeNode)) {
         return 1;
      }

      XTypeNode node2 = (XTypeNode) obj;
      return getName().compareTo(node2.getName());
   }

   /**
    * Clone a an output (XTypeNode) node of a datasource. The type node is
    * cloned by writing the type to XML and parse it back to handle
    * recursive type dependency.
    */
   public XTypeNode cloneType() throws Exception {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(bos, "utf-8"));
      pw.print("<output type=\"");
      pw.print(getType());
      pw.print("\">");
      writeTreeXML(pw);
      pw.print("</output>");
      pw.flush();
      pw.close();

      ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
      Document doc = Tool.parseXML(bis);
      Element elem = doc.getDocumentElement();
      XSchema schema = new XSchema(elem);

      return schema.getTypeNode(Tool.getAttribute(elem, "type"));
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         XTypeNode node = (XTypeNode) super.clone();

         // @by jamshedd deep clone the attrs vector
         // the clone method of the java.util.Vector class will only copy
         // the references
         node.attrs = Tool.deepCloneCollection(attrs);
         node.setName(getName());
         node.minOccurs = getMinOccurs();
         node.maxOccurs = getMaxOccurs();

         return node;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Create a primitive type node.
    * @param type one of the primitive types defined in XSchema.
    */
   public XTypeNode clone(String type) {
      XTypeNode typenode = XSchema.createPrimitiveType(type);

      if(typenode == null) {
         typenode = new XTypeNode();
      }

      try {
         typenode.attrs = (Vector) attrs.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone attributes", ex);
      }

      typenode.setName(getName());
      typenode.minOccurs = getMinOccurs();
      typenode.maxOccurs = getMaxOccurs();

      for(int i = 0; i < getChildCount(); i++) {
         XNode child = getChild(i);
         child = (XNode) child.clone();
         typenode.addChild(child);
      }

      return typenode;
   }

   /**
    * Check if two types are identical.
    */
   public boolean equals(Object obj) {
      try {
         XTypeNode node = (XTypeNode) obj;

         return node.getClass() == getClass() && super.equals(obj);
      }
      catch(Exception ex) {
      }

      return false;
   }

   private Vector<XTypeNode> attrs = new Vector<>();
   private int minOccurs = 1, maxOccurs = 1;
   private transient String suffix = null; // cached suffix
   private transient XMetaInfo minfo;
   private static final long serialVersionUID =  6662264928093131795L;

   private static final Logger LOG = LoggerFactory.getLogger(XTypeNode.class);
}
