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
package inetsoft.report.composition;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.*;
import java.util.*;

/**
 * Asset tree model.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AssetTreeModel implements TreeModel, AssetObject, DataSerializable {
   /**
    * Constructor.
    */
   public AssetTreeModel() {
      super();
   }

   /**
    * Constructor.
    */
   public AssetTreeModel(Node root) {
      this();
      this.root = root;
   }

   /**
    * Return the root of the tree. Return <code>null</code> only if the tree
    * has no nodes.
    */
   @Override
   public Object getRoot() {
      return root;
   }

   /**
    * Set the root of the tree.
    */
   public void setRoot(Node root) {
      this.root = root;
   }

   /**
    * Return the path to root.
    */
   public Node[] getPathToRoot(Node node) {
      List list = new ArrayList();
      getPathToRoot0(node, list);
      Node[] nodes = new Node[list.size()];
      list.toArray(nodes);

      return nodes;
   }

   /**
    * Collect the path to root.
    */
   private void getPathToRoot0(Node node, List path) {
      path.add(0, node);

      if(node.getParent() != null) {
         getPathToRoot0(node.getParent(), path);
      }
   }

   /**
    * Return the child of <code>parent</code> at index <code>index</code>
    * in the parent's child array.
    */
   @Override
   public Object getChild(Object parent, int index) {
      Node[] nodes = ((Node) parent).getNodes();

      if(nodes != null && index >= 0 && index < nodes.length) {
         return nodes[index];
      }

      return null;
   }

   /**
    * Return the number of children of <code>parent</code>.
    */
   @Override
   public int getChildCount(Object parent) {
      Node[] nodes = ((Node) parent).getNodes();
      return nodes == null ? 0 : nodes.length;
   }

   /**
    * Check if a node is a leaf.
    */
   @Override
   public boolean isLeaf(Object obj) {
      AssetEntry entry = ((Node) obj).getEntry();
      return !entry.isFolder();
   }

   /**
    * Value changed.
    */
   @Override
   public void valueForPathChanged(TreePath path, Object newValue) {
      // do nothing
   }

   /**
    * Return the index of child in parent.
    */
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

   /**
    * Add tree model listener.
    */
   @Override
   public void addTreeModelListener(TreeModelListener l) {
      listenerList.add(TreeModelListener.class, l);
   }

   /**
    * Remove tree model listener.
    */
   @Override
   public void removeTreeModelListener(TreeModelListener l) {
      listenerList.remove(TreeModelListener.class, l);
   }

   /**
    * Write xml.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<assetTreeModel class=\"" + getClass().getName()+ "\" >");

      if(root != null) {
         writer.print("<rootNode>");
         root.writeXML(writer);
         writer.print("</rootNode>");
      }

      writer.print("</assetTreeModel>");
   }

   /**
    * Parse xml.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element rootNode = Tool.getChildNodeByTagName(tag, "rootNode");

      if(rootNode != null) {
         root = new Node();
         root.parseXML(rootNode);
      }
   }

   /**
    * Write data to a DataOutputStream.
    * @param dos the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      try {
         dos.writeBoolean(root == null);

         if(root != null) {
            root.writeData(dos);
         }
      }
      catch (IOException e) {
      }
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      //do nothing
      return true;
   }

   /**
    * Tree node class.
    */
   public static class Node implements AssetObject, DataSerializable {
      public Node() {
         super();
      }

      public Node(AssetEntry entry) {
         this();
         this.entry = entry;
      }

      public void addNode(Node node) {
         node.setParent(this);
         nodes.add(node);
      }

      public void removeNode(int idx) {
         nodes.remove(idx);
      }

      public AssetEntry getEntry() {
         return entry;
      }

      public AssetEntry[] getEntrys() {
         ArrayList list = new ArrayList();
         list.add(entry);

         for(int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            list.addAll(Arrays.asList(node.getEntrys()));
         }

         AssetEntry[] entrys = new AssetEntry[list.size()];

         for(int i = 0; i < list.size(); i++) {
            entrys[i] = (AssetEntry) list.get(i);
         }

         return entrys;
      }

      public Node getNode(AssetEntry entry) {
         for(int i = 0; i < nodes.size(); i++) {
            Node child = nodes.get(i);

            if(entry.equals(child.getEntry())) {
               return child;
            }
         }

         return null;
      }

      public Node getNodeByEntry(AssetEntry entry) {
         for(int i = 0; i < nodes.size(); i++) {
            Node child = nodes.get(i);

            if(entry.equals(child.getEntry())) {
               return child;
            }

            if(child.getNodeByEntry(entry) != null) {
               return child.getNodeByEntry(entry);
            }
         }

         return null;
      }

      public Node[] getNodes() {
         Node[] subnodes = new Node[nodes.size()];
         nodes.toArray(subnodes);
         return subnodes;
      }

      public Node getParent() {
         return parent;
      }

      public void setParent(Node parent) {
         this.parent = parent;
      }

      public int getNodeCount() {
         return nodes.size();
      }

      public boolean isFolder() {
         return entry.isFolder();
      }

      public boolean isDataSource() {
         return entry.isDataSource();
      }

      public boolean isDataSourceFolder() {
         return entry.isDataSourceFolder();
      }

      public boolean isTable() {
         return entry.isTable();
      }

      public boolean isQuery() {
         return entry.isQuery();
      }

      public boolean isRequested() {
         return requested;
      }

      public void setRequested(boolean requested) {
         this.requested = requested;
      }

      public String toString() {
         return entry.toString();
      }

      public String toView() {
         return entry.toView();
      }

      public int hashCode() {
         return entry.hashCode();
      }

      public boolean equals(Object obj) {
         if(this == obj) {
            return true;
         }

         if(obj instanceof Node) {
            Node node2 = (Node) obj;
            return entry.equals(node2.entry);
         }

         return false;
      }

      public void setOneOff(boolean oneOff) {
         this.oneOff = oneOff;
      }

      public boolean isOneOff() {
         return oneOff;
      }

      // fix bug1305171805458
      /**
       * If the node is oneOff, should remove it after write to client.
       */
      private void removeOneOff(Node node) {
         if(node.isOneOff()) {
            for(int i = 0; i < nodes.size(); i++) {
               if(node.equals(nodes.get(i))) {
                  removeNode(i);
                  return;
               }
            }
         }
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.print("<node class=\"" + getClass().getName()+
            "\" requested=\"" + requested +"\" >");
         writer.print("<entry>");
         localizeEntry(entry);
         entry.writeXML(writer);
         writer.print("</entry>");
         writer.print("<nodes>");

         Node[] nodes0 = getNodes();

         for(int i = 0; i < nodes0.length; i++) {
            nodes0[i].writeXML(writer);
            removeOneOff(nodes0[i]);
         }

         writer.print("</nodes>");
         writer.print("</node>");
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         requested = "true".equals(Tool.getAttribute(tag, "requested"));
         Element entrynode = Tool.getChildNodeByTagName(tag, "entry");
         AssetEntry.createAssetEntry((Element) entrynode.getFirstChild());
         Element nodesnode = Tool.getChildNodeByTagName(tag, "nodes");
         NodeList list = nodesnode.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            Node node = new Node();
            node.parseXML((Element) list.item(i));
            nodes.add(node);
         }
      }

      /**
       * Write data to a DataOutputStream.
       * @param dos the destination DataOutputStream.
       */
      @Override
      public void writeData(DataOutputStream dos) {
         try {
            dos.writeBoolean(requested);
            localizeEntry(entry);
            entry.writeData(dos);
            dos.writeInt(nodes.size());
            Node[] nodes0 = getNodes();

            for(int i = 0; i < nodes0.length; i++) {
               nodes0[i].writeData(dos);
               removeOneOff(nodes0[i]);
            }
         }
         catch(IOException e) {
         }
      }

      /**
       * Localize asset entry.
       */
      private void localizeEntry(AssetEntry entry) {
         Catalog cata = Catalog.getCatalog(
            ThreadContext.getContextPrincipal(), Catalog.REPORT);
         String localStr = null;

         if(entry.getAlias() != null && !entry.getAlias().equals("") &&
            !entry.isReplet() && !entry.isRepositoryFolder())
         {
            localStr = cata.getIDString(entry.getAlias());
         }
         else {
            localStr = cata.getIDString(entry.getName());
         }

         if(localStr != null) {
            entry.setProperty("localStr", localStr);
         }
      }

      /**
       * Parse data from an InputStream.
       * @param input the source DataInputStream.
       * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
       */
      @Override
      public boolean parseData(DataInputStream input) {
         //do nothing
         return true;
      }

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

      public void printTree(String ind) {
         System.err.println(ind + entry.getName());

         for(Node node : nodes) {
            node.printTree(ind + "   ");
         }
      }

      private AssetEntry entry;
      private List<Node> nodes = new ArrayList<>();
      private boolean requested;
      private Node parent;
      private boolean oneOff = false;
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

   private Node root;
   private EventListenerList listenerList = new EventListenerList();
}
