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
package inetsoft.report.composition;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.DataSerializable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * AssemblyTreeModel is a tree model that provides data for the worksheet
 * explorer tree.
 */
public class AssemblyTreeModel implements TreeModel, AssetObject, DataSerializable {
   /**
    * Constructor.
    */
   public AssemblyTreeModel() {
      super();
   }

   /**
    * Constructor.
    */
   public AssemblyTreeModel(Node root) {
      this();
      setRoot(root);
   }

   /**
    * Set the root node to the model.
    */
   public void setRoot(Node root) {
      this.root = root;
   }

   @Override
   public Node getRoot() {
      return this.root;
   }

   @Override
   public void addTreeModelListener(TreeModelListener l) {
      listenerList.add(TreeModelListener.class, l);
   }

   @Override
   public Object getChild(Object parent, int index) {
      Node[] nodes = ((Node) parent).getNodes();

      if(nodes != null && index >= 0 && index < nodes.length) {
         return nodes[index];
      }

      return null;
   }

   @Override
   public int getChildCount(Object parent) {
      Node[] nodes = ((Node) parent).getNodes();
      return nodes == null ? 0 : nodes.length;
   }

   @Override
   public int getIndexOfChild(Object parent, Object child) {
      Node[] nodes = ((Node) parent).getNodes();

      for(int i = 0; nodes != null && i < nodes.length; i++) {
         if(nodes[i].equals(child)) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public boolean isLeaf(Object node) {
      return ((Node) node).getNodeCount() == 0;
   }

   @Override
   public void removeTreeModelListener(TreeModelListener l) {
      listenerList.remove(TreeModelListener.class, l);
   }

   @Override
   public void valueForPathChanged(TreePath path, Object newValue) {
      // do nothing
   }

   /**
    * Get the corresponding treepath.
    */
   public TreePath getCorrespondingPath(TreePath path) {
      if(path == null) {
         return null;
      }

      Node currNode = getRoot();
      TreePath result = new TreePath(currNode);
      Object[] objs = path.getPath();

      for(int i = 1; i < objs.length; i++) {
         Node node1 = (Node) objs[i];
         String name1 = node1.getEntry().getName();
         Node[] nodes = currNode.getNodes();
         int j = 0;

         for(;j < nodes.length; j++) {
            if(nodes[j].getEntry().getName().equals(name1)) {
               result = result.pathByAddingChild(nodes[j]);
               currNode = nodes[j];
               break;
            }
         }

         if(j == nodes.length) {
            return null;
         }
      }

      return result;
   }

   @Override
   public boolean parseData(DataInputStream input) {
      //do nothing
      return true;
   }

   @Override
   public void writeData(DataOutputStream output) throws IOException {
      try{
         output.writeBoolean(root == null);

         if(root != null) {
            root.writeData(output);
         }
      }
      catch (IOException e) {
      }
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element rootNode = Tool.getChildNodeByTagName(tag, "rootNode");

      if(rootNode != null) {
         root = new Node();
         root.parseXML(rootNode);
      }
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<AssemblyTreeModel class=\"" + getClass().getName() +
         "\">");

      if(root != null) {
         writer.println("<rootNode>");
         root.writeXML(writer);
         writer.println("</rootNode>");
      }

      writer.println("</AssemblyTreeModel>");
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException e) {
         return this;
      }
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    */
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(!(obj instanceof AssemblyTreeModel)) {
         return false;
      }

      AssemblyTreeModel model = (AssemblyTreeModel) obj;
      return equals(model.getRoot(), getRoot());
   }

   /**
    * Indicates whether the first node equals the second one.
    */
   private boolean equals(Node n1, Node n2) {
      if(!Tool.equals(n1, n2)) {
         return false;
      }

      Node[] children1 = n1.getNodes();
      Node[] children2 = n2.getNodes();

      if(children1.length != children2.length) {
         return false;
      }

      for(int i = 0; i < children1.length; i++) {
         if(!equals(children1[i], children2[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get max number of Icon in the tree.
    */
   public int getMaxIconNumber() {
      Node node = getRoot();

      if(node == null) {
         return 0;
      }

      int count = 0;
      TableAssemblyEntry[] entrys = node.getEntrys();

      for(int i = 0; i < entrys.length; i++) {
         ToolTipContainer tipContainer = entrys[i].getTipContainer();
         int icount = 0;

         if(tipContainer != null) {
            String aggregate = tipContainer.getAggregate();
            String condition = tipContainer.getCondition();
            String join = tipContainer.getJoin();
            icount = aggregate == null ? icount : icount + 1;
            icount = condition == null ? icount : icount + 1;
            icount = join == null ? icount : icount + 1;
         }

         count = icount > count ? icount : count;

         if(count == 3) {
            break;
         }
      }

      return count;
   }

   /**
    * Get the level of the node in the tree.
    * @param rootNode the tree's root.
    * @param node tree node.
    */
   public int getLevel(Node rootNode, Node node) {
      int level = 0;
      int i;

      for(i = 0; i < rootNode.getNodeCount(); i++) {
         Node nodeChild = (Node) getChild(rootNode, i);

         if(node.equals(nodeChild)) {
            level = 1;
         }
         else if(nodeChild.getNodeCount() > 0) {
            level = 1 + getLevel(nodeChild, node);
         }

         if(level > 0) {
            break;
         }
      }

      if(i == rootNode.getNodeCount() && level == 0) {
         level = -1;
      }

      return level;
   }

   /**
    * Tree node class.
    */
   public static class Node implements AssetObject, DataSerializable {
      /**
       * Constructor.
       */
      public Node() {
         super();
      }

      /**
       * Constructor.
       */
      public Node(TableAssemblyEntry entry) {
         this();
         this.entry = entry;
         // ignore position in comparison
         entry.setPosition(new Point(0, 0));
      }

      /**
       * Add node as child node.
       */
      public void addNode(Node node) {
         nodes.add(node);
      }

      /**
       * Get the assembly entry.
       */
      public TableAssemblyEntry getEntry() {
         return entry;
      }

      /**
       * Get all the sub entries.
       */
      public TableAssemblyEntry[] getEntrys() {
         List<TableAssemblyEntry> list = new ArrayList<>();
         list.add(entry);

         for(int i = 0; i < nodes.size(); i++) {
            Node node = (Node) nodes.get(i);
            list.addAll(Arrays.asList(node.getEntrys()));
         }

         TableAssemblyEntry[] entrys = new TableAssemblyEntry[list.size()];

         for(int i = 0; i < list.size(); i++) {
            entrys[i] = (TableAssemblyEntry) list.get(i);
         }

         return entrys;
      }

      /**
       * Get the number of the nodes.
       */
      public int getNodeCount() {
         return nodes.size();
      }

      /**
       * Get the parent of the node.
       */
      public Node getParent() {
         return parent;
      }

      /**
       * Get the children nodes.
       */
      public Node[] getNodes() {
         return nodes.toArray(new Node[] {});
      }

      /**
       * Remove the ith node.
       */
      public Node removeNode(int i) {
         return nodes.remove(i);
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         tag = Tool.getChildNodeByTagName(tag, "node");
         Element entrynode = Tool.getChildNodeByTagName(tag, "entry");

         if(entrynode != null) {
            entry = new TableAssemblyEntry();
            entry.parseXML(Tool.getFirstChildNode(entrynode));
         }

         Element nodesnode = Tool.getChildNodeByTagName(tag, "nodes");
         NodeList list = nodesnode.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            Node node = new Node();
            node.parseXML((Element) list.item(i));
            nodes.add(node);
         }
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.print("<node class=\"" + getClass().getName()+ "\">");
         writer.print("<entry>");

         if(entry != null) {
            entry.writeXML(writer);
         }

         writer.print("</entry>");
         writer.print("<nodes>");

         for(int i = 0; i < nodes.size(); i++) {
            ((Node) nodes.get(i)).writeXML(writer);
         }

         writer.print("</nodes>");
         writer.print("</node>");
      }

      @Override
      public boolean parseData(DataInputStream input) {
         return true;
      }

      @Override
      public void writeData(DataOutputStream output) throws IOException {
         try {
            entry.writeData(output);
            output.writeInt(nodes.size());

            for(int i = 0; i < nodes.size(); i++) {
               ((Node) nodes.get(i)).writeData(output);
            }
         }
         catch(IOException e) {
         }
      }

      /**
       * Indicates whether some other object is "equal to" this one.
       */
      public boolean equals(Object obj) {
         if(this == obj) {
            return true;
         }

         if(!(obj instanceof Node)) {
            return false;
         }

         Node node = (Node) obj;
         return entry.equals(node.entry);
      }

      /**
       * Clone this object.
       * @return the cloned object.
       */
      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            // ignore it
         }

         return null;
      }

      private TableAssemblyEntry entry;
      private List<Node> nodes = new ArrayList<>();
      private Node parent;
   }

   private Node root;
   private EventListenerList listenerList = new EventListenerList();
}