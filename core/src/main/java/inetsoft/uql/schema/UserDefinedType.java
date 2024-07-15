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

import inetsoft.uql.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Vector;

/**
 * User defined type. A user defined type can be a composite type,
 * which contains other data items.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class UserDefinedType extends XTypeNode {
   /**
    * Create an user defined type.
    */
   public UserDefinedType() {
   }

   /**
    * Create an user defined type.
    */
   public UserDefinedType(String name) {
      super(name);
   }

   /**
    * Create a user defined type with a list of children.
    */
   public UserDefinedType(String name, XTypeNode[] children) {
      this(name);
      XTypeNode type = new XTypeNode(name);

      for(int i = 0; i < children.length; i++) {
         type.addChild(children[i]);
      }

      setUserType(type);
   }

   /**
    * Set the type of this user node.
    */
   public void setUserType(XTypeNode type) {
      this.type = type;
   }

   /**
    * Get the type of this user node.
    */
   public XTypeNode getUserType() {
      return type;
   }

   /**
    * Add an attribute to the type.
    */
   @Override
   public void addAttribute(XTypeNode attr) {
      type.addAttribute(attr);
   }

   /**
    * Set an attribute at an index.
    *
    * @param index the specified index
    * @param attr the specified attribute
    */
   @Override
   public void setAttribute(int index, XTypeNode attr) {
      type.setAttribute(index, attr);
   }

   /**
    * Get the number of attributes in this type.
    */
   @Override
   public int getAttributeCount() {
      return (type == null) ? 0 : type.getAttributeCount();
   }

   /**
    * Get the specified attribute.
    */
   @Override
   public XTypeNode getAttribute(int idx) {
      return (type == null) ? null : type.getAttribute(idx);
   }

   /**
    * Get the index of an attribute.
    *
    * @param attr the specified attribute
    * @return the index of the specified attribute
    */
   @Override
   public int getAttributeIndex(XTypeNode attr) {
      return type.getAttributeIndex(attr);
   }

   /**
    * Get the attribute type node with specified name.
    */
   @Override
   public XTypeNode getAttributeType(String name) {
      return (type == null) ? null : type.getAttributeType(name);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return (type == null) ? XSchema.STRING : type.getType();
   }

   /**
    * Return true if this is a primitive type.
    */
   @Override
   public boolean isPrimitive() {
      return (type == null) ? false : type.isPrimitive();
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    */
   @Override
   public XNode newInstance() {
      XNode node = type.newInstance();

      node.setName(getName());
      return node;
   }

   /**
    * Get the number of children under this node.
    */
   @Override
   public int getChildCount() {
      return (type == null) ? 0 : type.getChildCount();
   }

   /**
    * Get the specified child node.
    * @param idx child index.
    * @return child node.
    */
   @Override
   public XNode getChild(int idx) {
      if(uchildren.size() <= idx) {
         uchildren.setSize(idx + 1);
      }

      if(uchildren.elementAt(idx) == null) {
         uchildren.setElementAt(new UserNode(this, idx), idx);
      }

      return (XNode) uchildren.elementAt(idx);
   }

   /**
    * Find the index of the child in this node.
    */
   @Override
   public int getChildIndex(XNode child) {
      int idx = uchildren.indexOf(child);

      return (idx < 0) ? type.getChildIndex(child) : idx;
   }

   /**
    * Add a child to this node.
    */
   @Override
   public void addChild(XNode child) {
      type.addChild(child);
   }

   /**
    * Set the specified child of this node to the new child.
    */
   @Override
   public void setChild(int idx, XNode child) {
      type.setChild(idx, child);

      if(idx < uchildren.size()) {
         uchildren.setElementAt(null, idx);
      }
   }

   /**
    * Remove the specified child.
    */
   @Override
   public void removeChild(XNode child) {
      type.removeChild(child);
   }

   /**
    * Remove the specified child.
    */
   @Override
   public void removeChild(int idx) {
      type.removeChild(idx);
   }

   /**
    * Remove all child in the tree.
    */
   @Override
   public void removeAllChildren() {
      type.removeAllChildren();
   }

   /**
    * Insert a child to this node at the specified position.
    */
   @Override
   public void insertChild(int idx, XNode child) {
      type.insertChild(idx, child);
   }

   /**
    * Set the format string for the type. The meaning of the format
    * depends on the data type. For example, for date related formats,
    * the format string is used to construct a SimpleDateFormat
    * object.
    */
   @Override
   public void setFormat(String fmt) {
      type.setFormat(fmt);
   }

   /**
    * Get the format string of this data type.
    */
   @Override
   public String getFormat() {
      return type.getFormat();
   }

   /**
    * Get additional attributes string.
    */
   @Override
   protected String getAttributeString() {
      return type.getAttributeString();
   }

   /**
    * Get additional attributes string.
    */
   @Override
   protected void writeAdditionalAttributes(Map<String, Object> properties) {
      type.writeAdditionalAttributes(properties);
   }

   /**
    * Write the XML schema specification.
    */
   @Override
   public void writeTypeXML(PrintWriter writer) {
      writer.print("<type name=\"" + getName() + "\" type=\"" + getType() +
                   "\"");
      writeAttributes(writer);
      writer.println("/>");
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         UserDefinedType udf = (UserDefinedType) super.clone();

         if(type != null) {
            //@by jamshedd using cloneType() to handle recursive schemas
            if(type.isPrimitive()) {
               udf.type = (XTypeNode) type.clone();
            }
            else {
               udf.type = type.cloneType();
            }
         }

         udf.uchildren = new Vector();
         return udf;
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
      }

      return null;
   }

   /**
    * Create a primitive type node.
    * @param tp one of the primitive types defined in XSchema.
    */
   @Override
   public XTypeNode clone(String tp) {
      UserDefinedType typenode = (UserDefinedType) clone();

      if(type != null) {
         typenode.type = type.clone(tp);
      }

      return typenode;
   }

   // fake node to link a user defined type to its child, otherwise
   // the uplink is missing so the tree path can not be constructed
   static class UserNode extends UserDefinedType {
      public UserNode(UserDefinedType parent, int index) {
         this.parent = parent;
         this.type = (XTypeNode) parent.getUserType().getChild(index);
         setName(type.getName());
      }

      @Override
      public XNode getParent() {
         return parent;
      }

      @Override
      public void setMinOccurs(int min) {
         type.setMinOccurs(min);
      }

      @Override
      public int getMinOccurs() {
         return type.getMinOccurs();
      }

      @Override
      public void setMaxOccurs(int max) {
         type.setMaxOccurs(max);
      }

      @Override
      public int getMaxOccurs() {
         return type.getMaxOccurs();
      }

      public String toString() {
         return type.toString();
      }

      /**
       * Cloned use the real type.
       */
      @Override
      public Object clone() {
         return type == null ? super.clone() : type.clone();
      }

      /**
       * Create a primitive type node.
       * @param type one of the primitive types defined in XSchema.
       */
      @Override
      public XTypeNode clone(String tp) {
         if(type == null) {
            return super.clone(tp);
         }

         return type.clone(tp);
      }

      XNode parent;
   }

   // @by mikec, all logic in uql assume the user type be a non null value
   // put a default string type here to avoid NPE elsewhere.
   protected XTypeNode type = new StringType(); // real type
   private Vector uchildren = new Vector(); // this is a cache

   private static final Logger LOG =
      LoggerFactory.getLogger(UserDefinedType.class);
}

