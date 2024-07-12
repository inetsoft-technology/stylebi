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
package inetsoft.report.pdf;

import java.util.Vector;

/**
 * Node class is used in the PDF generation process to create the TOC
 * nodes in PDF.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
class Node {
   /**
    * Create a node with specified label and parent.
    * @param tree parent tree.
    * @param label node label.
    */
   Node(String label) {
      this.label = label;
   }

   /**
    * Returns the child <code>TreeNode</code> at index 
    * <code>childIndex</code>.
    */
   public Node getChild(int childIndex) {
      if(children == null) {
         return null;
      }

      return (Node) children.elementAt(childIndex);
   }

   /**
    * Returns the number of children <code>TreeNode</code>s the receiver
    * contains.
    */
   public int getChildCount() {
      if(children == null) {
         return 0;
      }

      return children.size();
   }

   /**
    * Get the total number of nodes under this sub-tree.
    */
   public int getNodeCount() {
      if(getChildCount() == 0) {
         return 0;
      }

      int cnt = getChildCount();

      for(int i = 0; i < getChildCount(); i++) {
         cnt += getChild(i).getNodeCount();
      }

      return cnt;
   }

   /**
    * Returns the parent <code>TreeNode</code> of the receiver.
    */
   public Node getParent() {
      return parent;
   }

   /**
    * Add a child to this node.
    * @param child node.
    */
   void addChild(Node child) {
      if(child.parent != null) {
         child.parent.removeChild(child);
      }

      child.parent = this;

      if(children == null) {
         children = new Vector();
      }

      child.index = children.size();
      children.addElement(child);
   }

   /**
    * Remove a child of this node.
    * @param child child node.
    */
   void removeChild(Node child) {
      if(children == null) {
         return;
      }

      children.removeElementAt(child.index);

      for(int i = child.index; i < getChildCount(); i++) {
         getChild(i).index = i;
      }

      child.parent = null;
      child.index = 0;
   }

   /**
    * Set the label of this node. This does not cause a repaint(). The 
    * caller is responsible for repainting the widget.
    * @param label new node label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Return node label.
    * @return node label.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Set the PDF page ID.
    */
   public void setPageID(String id) {
      pageId = id;
   }

   /**
    * Get the PDF page ID.
    */
   public String getPageID() {
      return pageId;
   }

   /**
    * Set the page y position.
    */
   public void setPageY(int y) {
      pageY = (short) y;
   }

   /**
    * Get the page y position.
    */
   public int getPageY() {
      return pageY;
   }

   /**
    * Get the PDF ID of this node.
    */
   public int getID() {
      return id;
   }

   /**
    * Set the PDF ID of this node.
    */
   public void setID(int id) {
      this.id = id;
   }

   /**
    * Get the next sibling of this node.
    */
   public Node getNext() {
      if(parent != null && index < parent.getChildCount() - 1) {
         return parent.getChild(index + 1);
      }

      return null;
   }

   /**
    * Convert to full path string.
    * @return node path.
    */
   public String toString() {
      return getLabel();
   }

   private String label;
   private Node parent = null;	// parent node
   private Vector children = null; // children, created on demand for memory
   private String pageId = null;
   private short pageY = 0;
   private int id = 0;
   private int index = 0; // index in parent
}

