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
package inetsoft.uql.path;

import inetsoft.uql.*;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.TableTree;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.expr.ExprParser;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.StringReader;
import java.text.ParseException;
import java.util.Vector;

/**
 * A node path is used to perform tree selections. It can be used to
 * select a subtree from a xnode tree. The selection can be done using
 * only node path, or mixed with conditions at any sequence node.
 * A simple node path is the node name concatenated together from the
 * root of the tree, separated by dots:<pre>
 *  employee.name<pre>
 * A condition can be attached to a node:<pre>
 *  employee[state = 'NJ'].name
 * <pre>
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XNodePath implements java.io.Serializable, Cloneable {
   /**
    * Parse a node path string and create a node path object.
    */
   public static XNodePath parse(String path) throws ParseException {
      try {
         ExprParser parser = new ExprParser(new StringReader(path));
         XNodePath xpath = parser.xpath();

         return xpath;
      }
      catch(Exception ex) {
         LOG.error("Failed to parse node path: " + path, ex);
         throw new java.text.ParseException(ex.toString(), 0);
      }
   }

   /**
    * Parse a simple path that is a concatenation of node names without
    * any condition expression.
    */
   public static XNodePath parseSimplePath(String path) {
      XNodePath xpath = new XNodePath();
      int idx = 0;

      do {
         int ndx = path.indexOf('.', idx);

         xpath.add(path.substring(idx, (ndx > 0) ? ndx : path.length()));
         idx = ndx + 1;
      }
      while(idx > 0);

      return xpath;
   }

   /**
    * Create an empty node path.
    */
   public XNodePath() {
   }

   /**
    * Apply the path to the tree, and return selected nodes from the tree.
    * @param root tree root.
    * @param vars query variables.
    * @return root of selected nodes.
    */
   public XNode select(XNode root, VariableTable vars) throws Exception {
      // simulate a tree, each row of the table is treated as
      // a child of a sequence, each column is a child in the child
      if(root instanceof XTableNode) {
         // if select the table and no condition, return the table
         if(nodes.size() == 1 && getPathNode(0).getName().equals(root.getName())
            && getPathNode(0).getCondition() == null)
         {
            return root;
         }

         root = new TableTree((XTableNode) root);
      }

      XSequenceNode seq = new XSequenceNode();

      select(root, 0, seq, vars);

      // if selected a table node, don't add it to a sequence
      if(seq.getChildCount() == 1 && (seq.getChild(0) instanceof XTableNode)) {
         return seq.getChild(0);
      }
      // set the sequence node to have same name as the elements
      else if(seq.getChildCount() > 0) {
         seq.setName(seq.getChild(0).getName());
      }
      else if(getPathNodeCount() > 0) {
         seq.setName(getPathNode(getPathNodeCount() - 1).getName());
      }

      return seq;
   }

   /**
    * Select nodes from the tree and add them to the sequence.
    */
   private void select(XNode root, int idx, XSequenceNode seq,
		       VariableTable vars) throws Exception {
      PathNode pathnode = idx < nodes.size() ?
         (PathNode) nodes.elementAt(idx) : null;
      XNode res;
      boolean lastNode = true;

      // special handling for empty pathnode?
      if(pathnode != null && pathnode.getName().length() <= 0 && idx > 0) {
         pathnode = (PathNode) nodes.elementAt(idx - 1);
         res = (pathnode != null) ? pathnode.select(root, vars) : root;

         if(res != null) {
            seq.addChild(res);
         }

         return;
      }

      // @by larryl, check if this is the last path node,
      // discount attribute node
      for(int i = nodes.size() - 1; i > idx; i--) {
         PathNode node = (PathNode) nodes.get(i);

         // @by larryl, @name is stripped in in XSelect to ""
         if(node != null && node.getName().length() > 0) {
            lastNode = false;
            break;
         }
      }

      res = (pathnode != null) ? pathnode.select(root, vars) : root;

      if(res != null) {
         // this is the last node
         if(lastNode) {
            if(res instanceof XSequenceNode) {
               XUtil.merge(seq, (XSequenceNode) res);
            }
            else {
               seq.addChild(res);
            }

            return;
         }
         // sequence nodes have two levels for each PathNode level
         // param[] and param
         // lsf modified the conditions
         else if(res instanceof XSequenceNode &&
		 ((PathNode) nodes.elementAt(idx + 1)).getName().length() > 0) {
            for(int i = 0; i < res.getChildCount(); i++) {
               XNode child = res.getChild(i);
               int ocnt = seq.getChildCount();

               for(int j = 0; j < child.getChildCount(); j++) {
                  select(child.getChild(j), idx + 1, seq, vars);
               }

               // if a node is missing in a branch, add a null
               /* @by larryl, I remember this was added for the subtree
                  join. With the new logic (tree traversal), it should not be
                  necessary and it actually cause empty rows to be added
               if(seq.getChildCount() == ocnt) {
                  seq.addChild(new XNode(((PathNode) nodes.get(nodes.size() - 1))
                                  .getName()));
               }
               */
            }

            // @by charvi
            // @fixed bug1110791281063
            // Commenting the code that removes all the children since if
            // there is a condition specified, PathNode.select() will
            // create a new instance of XSequenceNode before adding
            // all the qualifying nodes as its children.  Applying the
            // condition in the following if statement will result in
            // an empty result set even though there might be qualifying
            // records.
            // In case there are no conditions specified in the query,
            // then PathNode.select() will be returning the root back
            // and hence res will be equal to root.
            //if(res != root) {
            //   res.removeAllChildren();
            //}
         }
         // go down to the child level
         else {
            for(int i = 0; i < res.getChildCount(); i++) {
               select(res.getChild(i), idx + 1, seq, vars);
            }
         }
      }
   }

   /**
    * Get the names of all variables used in the conditions in the path.
    */
   public String[] getVariables() {
      Vector vec = new Vector();

      for(int i = 0; i < getPathNodeCount(); i++) {
         PathNode pnode = getPathNode(i);
         ConditionExpression cond = pnode.getCondition();

         if(cond != null) {
            String[] vars = cond.getVariables();

            for(int j = 0; j < vars.length; j++) {
               vec.addElement(vars[j]);
            }
         }
      }

      String[] arr = new String[vec.size()];

      vec.copyInto(arr);
      return arr;
   }

   /**
    * Check if the node specified by the type is on the node path.
    * @return the matching node.
    */
   public PathNode find(XTypeNode root, XTypeNode node) {
      Vector tnodes = new Vector();

      for(XTypeNode rnode = node; rnode != root && rnode != null;
          rnode = (XTypeNode) rnode.getParent()) {
         tnodes.insertElementAt(rnode, 0);
      }

      tnodes.insertElementAt(root, 0);

      for(int i = 0; i < tnodes.size(); i++) {
         if(i >= nodes.size()) {
            return null;
         }

         XTypeNode tnode = (XTypeNode) tnodes.elementAt(i);
         PathNode pnode = (PathNode) nodes.elementAt(i);

         if(!pnode.getName().equals(tnode.getName())) {
            return null;
         }
      }

      return getPathNode(tnodes.size() - 1);
   }

   /**
    * Get the number of nodes from the path.
    */
   public int getPathNodeCount() {
      return nodes.size();
   }

   /**
    * Get the specified node.
    * @param idx node index.
    * @return specified path node.
    */
   public PathNode getPathNode(int idx) {
      return (PathNode) nodes.elementAt(idx);
   }

   /**
    * Set the node at the specified index.
    * @param idx node index.
    * @param node new path node.
    */
   public void setPathNode(int idx, PathNode node) {
      nodes.setElementAt(node, idx);
   }

   /**
    * Add a node to the path.
    */
   public void add(String name) {
      add(name, (ConditionExpression) null);
   }

   /**
    * Add a node to the path with an additional condition to select
    * nodes from the sequence.
    */
   public void add(String name, String cond) throws ParseException {
      add(name, ConditionExpression.parse(cond));
   }

   /**
    * Add a node to the path with an additional condition to select
    * nodes from the sequence.
    */
   public void add(String name, ConditionExpression cond) {
      nodes.addElement(new PathNode(name, cond));
   }

   /**
    * Add a node to the node path.
    */
   public void add(PathNode node) {
      nodes.addElement(node);
   }

   /**
    * This get the path of the nodes. The node condition is not included
    * in the path.
    * @param node the path node this path should include. Nodes following
    * the node is not added to the path. If the node is null, all nodes
    * are included.
    */
   public String getPath(PathNode node) {
      StringBuilder str = new StringBuilder();

      for(int i = 0; i < nodes.size(); i++) {
         if(i > 0) {
            str.append(".");
         }

         str.append(getPathNode(i).getName());

         if(node == getPathNode(i)) {
            break;
         }
      }

      return str.toString();
   }

   public String toString() {
      StringBuilder str = new StringBuilder();

      for(int i = 0; i < nodes.size(); i++) {
         if(i > 0) {
            str.append(".");
         }

         str.append(nodes.elementAt(i));
      }

      return str.toString();
   }

   /**
    * Write node path in XML representation.
    */
   public void writeXML(PrintWriter writer) {
      writer.print("<path>");

      for(int i = 0; i < nodes.size(); i++) {
         PathNode node = (PathNode) nodes.elementAt(i);
         ConditionExpression cond = node.getCondition();

         writer.print("<pathnode>");
         writer.print("<name>" + node.getName() + "</name>");

         if(cond != null) {
            writer.print("<condition><![CDATA[" + cond + "]]></condition>");
         }

         writer.println("</pathnode>");
      }

      writer.println("</path>");
   }

   /**
    * Parse the XML element that contains information of this node path.
    */
   public void parseXML(Element root) throws Exception {
      NodeList nlist = Tool.getChildNodesByTagName(root, "pathnode");

      // backward compatibility with 4.4, string representation of path
      if(nlist.getLength() == 0) {
         XNodePath path = parse(Tool.getValue(root));

         this.nodes = path.nodes;

         return;
      }

      for(int i = 0; i < nlist.getLength(); i++) {
         Element node = (Element) nlist.item(i);
         NodeList namelist = Tool.getChildNodesByTagName(node, "name");
         NodeList condlist = Tool.getChildNodesByTagName(node, "condition");

         if(namelist.getLength() > 0) {
            PathNode pnode = new PathNode(Tool.getValue(namelist.item(0)));

            if(condlist.getLength() > 0) {
               String cstr = null;

               try {
                  cstr = Tool.getValue(condlist.item(0));
                  ExprParser parser = new ExprParser(new StringReader(cstr));
                  ConditionExpression cond = parser.search_condition();

                  pnode.setCondition(cond);
               }
               catch(Exception ex) {
                  LOG.error("Failed to parse expression: " + cstr, ex);
               }
            }

            add(pnode);
         }
      }
   }

   /**
    * Returns a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         XNodePath path = (XNodePath) super.clone();

         path.nodes = new Vector();

         for(int i = 0; i < nodes.size(); i++) {
            PathNode pnode = (PathNode) nodes.elementAt(i);

            path.nodes.addElement(pnode.clone());
         }

         return path;
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private Vector nodes = new Vector();

   private static final Logger LOG =
      LoggerFactory.getLogger(XNodePath.class);
}
