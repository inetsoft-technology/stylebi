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
package inetsoft.uql;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.text.Format;
import java.util.*;

/**
 * All query results are represented as a tree. A result tree is consisted
 * of hierarchy of XNode objects. Each node has a name and a value. A
 * non-leaf node has a list of children nodes. The XNode is the base class
 * for all other node classes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XNode implements java.io.Serializable, Cloneable, Comparable {
   /**
    * Create an empty node.
    */
   public XNode() {
      super();
   }

   /**
    * Create an empty node with the specified name.
    * @param name node name.
    */
   public XNode(String name) {
      this();

      setName(name);
   }

   /**
    * Get the value of a node. The node is specified by the node path.
    * The node path can refer to the current node, or one of its child
    * node. Each node in a tree is uniquely identified by a node path.
    * When there are multiple nodes with the same name on the same
    * level of a subtree, the nodes are grouped in a sequence node.
    * Each node in a sequence can be uniquely identified by its index
    * in the sequence, e.g. parent.seq[2].
    * @param path node path.
    * @return node value.
    */
   public Object getValue(String path) {
      XNode node = getNode(path);

      if(node instanceof XSequenceNode && node.getChildCount() > 0) {
         node = node.getChild(0);
      }

      if(node != null) {
         int aidx = path.lastIndexOf('@');

         return aidx >= 0 ?
            node.getAttribute(path.substring(aidx + 1)) :
            node.getValue();
      }

      return null;
   }

   /**
    * Find the node specified by the node path. The path is a dot delimited
    * concatenation of node names from the root of the tree to the node.
    * The child of a sequence node is refered to as the name of the
    * sequence node followed by a sequence index, params[2].
    */
   public XNode getNode(String path) {
      if(path == null || path.length() == 0) {
         return this;
      }

      if(getName() == null) {
         return null;
      }

      // remove attribute
      int aidx = path.lastIndexOf('@');

      if(aidx >= 0) {
         path = path.substring(0, aidx);

         // remove dot
         if(path.endsWith(".")) {
            path = path.substring(0, path.length() - 1);
         }
      }

      // this allows a '.' in the node name
      int nameEnd = getName().length();
      int idx = path.indexOf('.', path.startsWith("this") ? 4 : nameEnd);
      String name2 = idx >= 0 ? path.substring(0, idx) : path;
      XNode node = this;
      // check for index [], by searching from the end of the name, we allow
      // [] to be part of a name without being recognized as an index
      int b1 = name2.indexOf('[', nameEnd);

      if(b1 > 0) {
         int b2 = name2.indexOf(']', b1 + 1);

         // if sequence index specified, get index and remove the [] from
         // name string
         if(b2 > 0) {
            int nth = Integer.parseInt(name2.substring(b1 + 1, b2));

            // @by billh, when node is not an XSequenceNode, if index is zero,
            // return the node itself, else return null; when node is an
            // XSequenceNode, call its method directly
            if(node instanceof XSequenceNode) {
               node = node.getChild(nth);
            }
            else if(nth != 0) {
               node = null;
            }

            name2 = name2.substring(0, b1);
         }
      }

      // no child
      if(node == null) {
         return null;
      }

      if(node.getName().equals(name2) || "this".equals(name2) ||
         Tool.equals(node.getAttribute("variable"), name2))
      {
         if(idx < 0) {
            return node;
         }

         // find the child
         path = this instanceof XSequenceNode ?
            path : path.substring(idx + 1);

         // remove .this element (self-reference, no-op)
         while(path.startsWith("this") &&
               (path.length() == 4 || path.charAt(4) == '.')) {
            path = path.substring(Math.min(5, path.length()));
         }

         // self
         if(path.length() == 0) {
            return node;
         }

         for(int i = 0; i < node.getChildCount(); i++) {
            XNode child = node.getChild(i).getNode(path);

            if(child != null) {
               return child;
            }
         }
      }

      return null;
   }

   /**
    * Get the full path of this node. The full path is the names from
    * the root node to this node separated by dots.
    */
   public String getPath() {
      return getPath(null);
   }

   /**
    * Get the full path of this node. The full path is the names from
    * the root node to this node separated by dots.
    * @param root root is the root node of the tree. This can be used
    * to get the path of a node in a subtree, which has a root that
    * is a child of another node on the original tree.
    */
   public String getPath(XNode root) {
      String path = "";

      for(XNode node = this; node != root && node != null;
          node = node.getParent()) {
         if(node.parent instanceof XSequenceNode) {
            int idx = node.parent.getChildIndex(node);

            node = node.getParent();
            path = node.getName() + "[" + idx + "]" +
               (path.length() == 0 ? "" : ".") + path;
         }
         else {
            path = node.getName() + (path.length() == 0 ? "" : ".") + path;
         }
      }

      return path;
   }

   /**
    * Get the node name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the node name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Set the node value.
    */
   public void setValue(Object value) {
      this.value = value;
   }

   /**
    * Get the node value.
    */
   public Object getValue() {
      return value;
   }

   /**
    * Get a node attribute value. If the attribute is not defined, it
    * returns null.
    * @param key attribute key.
    * @return attribute value.
    */
   public Object getAttribute(String key) {
      return attrmap == null ? null : attrmap.get(key);
   }

   /**
    * Define an attribute and its value.
    * @param key attribute key.
    * @param val attribute value.
    */
   public void setAttribute(String key, Object val) {
      untint();

      if(attrmap == null) {
         attrmap = new Hashtable<>();
      }

      if(val == null) {
         attrmap.remove(key);

         if(attrmap.size() == 0) {
            attrmap = null;
         }
      }
      else {
         attrmap.put(key, val);
      }
   }

   /**
    * Get all attribute names.
    */
   public Enumeration<String> getAttributeNames() {
      return attrmap == null ? Collections.emptyEnumeration() : attrmap.keys();
   }

   /**
    * Get the number of children under this node.
    */
   public int getChildCount() {
      return children == null ? 0 : children.size();
   }

   /**
    * Get the specified child node.
    * @param idx child index.
    * @return child node.
    */
   public XNode getChild(int idx) {
      if(children == null) {
         return null;
      }
      else if(idx < 0) {
         idx = children.size() + idx;
      }

      try {
         return children.elementAt(idx);
      }
      catch(Exception ex) {
         // out of range count as null
         return null;
      }
   }

   /**
    * Get the child node of this node with the specified name.
    */
   public XNode getChild(String name) {
      int cnt = getChildCount();

      for(int i = 0; i < cnt; i++) {
         XNode child = getChild(i);
         String cname = child.getName();

         if(cname != null && cname.equals(name)) {
            return child;
         }
      }

      return null;
   }

   /**
    * Find the index of the child in this node.
    */
   public int getChildIndex(XNode child) {
      if(children == null) {
         return -1;
      }

      return children.indexOf(child);
   }

   /**
    * Add a child to this node.
    */
   public void addChild(XNode child) {
      addChild(child, false);
   }

   /**
    * Add a child to this node.
    * @param child child node.
    * @param sorted true to add child in sorted order.
    */
   public void addChild(XNode child, boolean sorted) {
      addChild(child, sorted, true);
   }

   /**
    * Add a child to this node.
    * @param child child node.
    * @param sorted true to add child in sorted order.
    * @param uniq true to ensure the new child is unique. If child with same
    * same exists, create a sequence node and add all nodes with same name
    * to the sequence node.
    */
   public void addChild(XNode child, boolean sorted, boolean uniq) {
      initChildren();

      if(child != null) {
         if(uniq) {
            child = checkDuplicate(child);
         }

         if(child != null) {
            if(sorted) {
               int i = children.size() - 1;

               children.addElement(child);

               for(; i >= 0; i--) {
                  XNode node = children.elementAt(i);

                  if(child.getName().compareTo(node.getName()) >= 0) {
                     break;
                  }

                  children.setElementAt(node, i + 1);
               }

               children.setElementAt(child, i + 1);
            }
            else {
               children.addElement(child);
            }

            child.parent = this;
         }
      }
   }

   /**
    * This method is used to check if a child already exist with the
    * same name. If yes, it creates a sequence node to store the duplicate
    * nodes.
    */
   protected XNode checkDuplicate(XNode child) {
      XNode child2 = getChild(child.getName());

      if(child2 != null) {
         if(!(child2 instanceof XSequenceNode)) {
            XSequenceNode nchild = new XSequenceNode(child.getName());

            nchild.addChild(child2);
            nchild.addChild(child);

            // replace the child node with a sequence node
            child = nchild;
            children.removeElement(child2);

            return nchild;
         }
         else {
            // add to the existing sequence
            child2.addChild(child);
            return null;
         }
      }

      initChildren(); // may be null'ed in removeChild()
      return child;
   }

   /**
    * Set the specified child of this node to the new child.
    */
   public void setChild(int idx, XNode child) {
      initChildren();

      if(children != null && children.size() > idx) {
         // break the circular reference
         XNode ochild = children.get(idx);
         ochild.parent = null;
         children.setElementAt(child, idx);
      }
      else {
         children.add(child);
      }

      child.parent = this;
   }

   /**
    * Remove the specified child.
    */
   public void removeChild(XNode child) {
      removeChild(child, true);
   }

   /**
    * Remove the specified child.
    * @param permanent true if the caller intends to remove the child
    * permanently. Setting this parameter to true will remove
    * all references to the child's parent and the child's children.
    */
   public void removeChild(XNode child, boolean permanent) {
      if(permanent) {
         child.removeAllChildren();
      }

      child.parent = null;
      initChildren();
      children.removeElement(child);
   }

   /**
    * Remove the specified child.
    * @param idx the index of the child to remove
    */
   public void removeChild(int idx) {
      removeChild(idx, true);
   }

   /**
    * Remove the specified child.
    * @param idx the index of the child to remove
    * @param permanent true if the caller intends to remove the child
    * permanently. Setting this parameter to true will remove
    * all references to the child's parent and the child's children.
    */
   public void removeChild(int idx, boolean permanent) {
      if(idx < getChildCount()) {
         // make sure the memory is gc'd
         if(permanent) {
            getChild(idx).removeAllChildren();
            getChild(idx).parent = null;
         }

         children.removeElementAt(idx);

         // remove the vector if empty, optimization
         if(children.size() == 0) {
            children = null;
         }
      }
   }

   /**
    * Remove all child in the tree.
    */
   public void removeAllChildren() {
      while(getChildCount() > 0) {
         removeChild(0);
      }
   }

   /**
    * Insert a child to this node at the specified position.
    */
   public void insertChild(int idx, XNode child) {
      initChildren();
      children.insertElementAt(child, idx);
      child.parent = this;
   }

   /**
    * Get the parent of this node. If this node is the root node, it
    * returns null.
    */
   public XNode getParent() {
      return parent;
   }

   /**
    * Check if this node is the ancestor of another node.
    * @param node the specified node.
    * @return <tt>true</tt> if is the ancestor of the node, <tt>false</tt>
    * otherwise.
    */
   public boolean isAncestor(XNode node) {
      do {
         node = node.getParent();

         if(node == this) {
            return true;
         }
      }
      while(node != null);

      return false;
   }

   /**
    * Write the node XML representation.
    */
   public void writeXML(PrintWriter writer) {
      String tag = Tool.toIdentifier(getName());
      writer.print("<" + tag + " _node_name=\"" + Tool.escape(getName()) +
         "\"");

      if(attrmap != null) {
         Enumeration keys = attrmap.keys();

         while(keys.hasMoreElements()) {
            String attr = (String) keys.nextElement();
            writer.print(" " + attr + "=\"" +
               Tool.escape(attrmap.get(attr).toString()) + "\"");
         }
      }

      writer.println(">");

      for(int i = 0; i < getChildCount(); i++) {
         getChild(i).writeXML(writer);
      }

      writer.println("</" + tag + ">");
   }

   public String toString() {
      return value != null ? name + ": " + value : name;
   }

   /**
    * Untint a node. A node is tinted when it's first cloned. When the
    * cloned information is modified, the data is duplicated (untinted)
    * so the contents of the original node is not changed.
    */
   private void untint() {
      if(dirty) {
         if(attrmap != null) {
            attrmap = (Hashtable) attrmap.clone();
         }

         dirty = false;
      }
   }

   /**
    * Make sure the children list is initialized.
    */
   private void initChildren() {
      if(children == null) {
         children = new Vector<>(1);
      }
   }

   /**
    * Compare if two nodes are equal. Two nodes are considered equal if they
    * have same name.
    */
   public boolean equals(Object obj) {
      if(obj instanceof XNode) {
         XNode node = (XNode) obj;

         if(getParent() instanceof XSequenceNode &&
            getParent() == node.getParent())
         {
            return this == obj;
         }

         return name.equals(node.getName());
      }

      return false;
   }

   /**
    * Calculate hash code of a node.
    */
   public int hashCode() {
      return name.hashCode();
   }

   /**
    * Get the address of this node.
    */
   public int addr() {
      if(addr == 0) {
         addr = super.hashCode();
      }

      return addr;
   }

   /**
    * Check if equals another object in content.
    */
   public boolean eq(Object obj) {
      if(obj instanceof XNode) {
         XNode node2 = (XNode) obj;

         return node2.addr() == addr() &&
            Tool.equals(node2.getName(), getName());
      }

      return false;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         XNode node = (XNode) super.clone();
         node.dirty = true;
         node.addr = 0;
         cloneValue(node);

         if(children != null) {
            node.children = (Vector<XNode>) children.clone();
            int size = node.children.size();

            for(int i = 0; i < size; i++) {
               XNode child = children.get(i);

               child = (XNode) child.clone();
               child.parent = node;
               node.children.setElementAt(child, i);
            }
         }

         return node;
      }
      catch(Exception e) {
         LOG.error("Failed to clone XNode", e);
         return null;
      }
   }

   /**
    * Clone node value when necessary, do nothing by default.
    */
   protected void cloneValue(XNode node) {
   }

   /**
    * Compares this object with the specified object for order.
    *
    * @param obj the object with which to compare.
    *
    * @return a negative integer, zero, or a positive integer as this object is
    *         less than, equal to, or greater than the specified object.
    */
   private int compareTo(Object obj, boolean ignoreCase) {
      int result = 0;

      if(obj == null) {
         return 1;
      }

      String name1 = getName();
      String name2 = ((XNode) obj).getName();

      if(name1 == null && name2 != null) {
         result = -1;
      }
      else if(name1 != null && name2 == null) {
         result = 1;
      }
      else if(name1 != null && name2 != null) {
         result = ignoreCase ?
            name1.compareToIgnoreCase(name2) :
            name1.compareTo(name2);
      }

      return result;
   }

   /**
    * Compares this object with the specified object for order.
    *
    * @param obj the object with which to compare.
    *
    * @return a negative integer, zero, or a positive integer as this object is
    *         less than, equal to, or greater than the specified object.
    */
   @Override
   public int compareTo(Object obj) {
      return compareTo(obj, false);
   }

   /**
    * Sorts the children of this node by their names in the specified order.
    *
    * @param ascending <code>true</code> to sort the children in ascending
    *                  order; <code>false<code> to sort the children in
    *                  descending order.
    */
   public void sort(final boolean ascending, final boolean ignoreCase) {
      Comparator<XNode> c = (o1, o2) -> {
         int result = ignoreCase ?
            o1.compareTo(o2, ignoreCase) :
            o1.compareTo(o2);

         return ascending ? result : result * -1;
      };

      sort(c);
   }

   /**
    * Sorts the children of this node by their names in the specified order.
    *
    * @param ascending <code>true</code> to sort the children in ascending
    *                  order; <code>false<code> to sort the children in
    *                  descending order.
    */
   public void sort(final boolean ascending) {
      sort(ascending, false);
   }

   /**
    * Sorts the children of this node by their names.
    *
    * @param c the Comparator used to sort the children.
    */
   private void sort(Comparator<XNode> c) {
      if(children != null) {
         children.sort(c);
         Enumeration<XNode> iter = children.elements();

         while(iter.hasMoreElements()) {
            iter.nextElement().sort(c);
         }
      }
   }

   /**
    * Optimize serialization.
    */
   private void writeObject(java.io.ObjectOutputStream s)
      throws java.io.IOException
   {
      addr();
      s.defaultWriteObject();

      if(attrmap != null) {
         Enumeration<String> keys = attrmap.keys();
         Enumeration<Object> elements = attrmap.elements();

         while(keys.hasMoreElements()) {
            s.writeObject(keys.nextElement());
            s.writeObject(elements.nextElement());
         }
      }

      s.writeObject(null);
   }

   /**
    * Optimize serialization.
    */
   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();

      String key;

      while((key = ((String) s.readObject())) != null) {
         if(attrmap == null) {
            attrmap = new Hashtable<>();
         }

         Object val = s.readObject();
         attrmap.put(key, val);
      }
   }

   /**
    * Get the default format.
    */
   public Format getDefaultFormat() {
      return format;
   }

   /**
    * Set the default format.
    */
   public void setDefaultFormat(Format format) {
      this.format = format;
   }

   private Format format;
   private String name = "node";
   protected Object value = null;
   private transient Hashtable<String, Object> attrmap = null;
   private Vector<XNode> children = null;
   private XNode parent = null;
   private int addr = 0;
   // true if this is a cloned copy and needs to clone attrmap and children
   private transient boolean dirty = false;
   private static final long serialVersionUID =  5734319465708834463L;

   private static final Logger LOG = LoggerFactory.getLogger(XNode.class);
}
