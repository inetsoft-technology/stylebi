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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Query plan tree model.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class QueryTreeModel implements TreeModel, AssetObject {
   /**
    * Constructor.
    */
   public QueryTreeModel() {
      super();
   }

   /**
    * Constructor.
    */
   public QueryTreeModel(QueryNode root) {
      this();
      setRoot(root);
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
   public void setRoot(QueryNode root) {
      this.root = root;
   }

   /**
    * Return the child of <code>parent</code> at index <code>index</code>
    * in the parent's child array.
    */
   @Override
   public Object getChild(Object parent, int index) {
      QueryNode[] nodes = ((QueryNode) parent).getNodes();

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
      QueryNode[] nodes = ((QueryNode) parent).getNodes();
      return nodes == null ? 0 : nodes.length;
   }

   /**
    * Check if a node is a leaf.
    */
   @Override
   public boolean isLeaf(Object obj) {
      return getChildCount(obj) == 0;
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
      QueryNode[] nodes = ((QueryNode) parent).getNodes();

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
   }

   /**
    * Remove tree model listener.
    */
   @Override
   public void removeTreeModelListener(TreeModelListener l) {
   }

   /**
    * Write xml.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<queryTreeModel class=\"" + getClass().getName() + "\" >");

      if(root != null) {
         writer.print("<rootNode>");
         root.writeXML(writer);
         writer.print("</rootNode>");
      }

      writer.print("</queryTreeModel>");
   }

   /**
    * Parse xml.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element rootNode = Tool.getChildNodeByTagName(tag, "rootNode");

      if(rootNode != null) {
         root = new QueryNode();
         root.parseXML(rootNode);
      }
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

   /**
    * Query tree node class.
    */
   @JsonSerialize(using = QueryNode.Serializer.class)
   public static class QueryNode implements AssetObject {
      public QueryNode() {
      }

      public QueryNode(String name) {
         this.name = name;
      }

      public QueryNode[] getNodes() {
         QueryNode[] subnodes = new QueryNode[nodes.size()];
         nodes.toArray(subnodes);
         return subnodes;
      }

      public void addNode(QueryNode node) {
         nodes.add(node);
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getRelation() {
         return relation;
      }

      public void setRelation(String relation) {
         this.relation = relation;
      }

      public String getIconPath() {
         return iconPath;
      }

      public void setIconPath(String iconPath) {
         this.iconPath = iconPath;
      }

      public void setDescription(String description) {
         this.description = description;
      }

      public String getDescription() {
         return description;
      }

      public void setTooltip(String tooltip) {
         this.tooltip = tooltip;
      }

      public String getTooltip() {
         return tooltip;
      }

      public String toString() {
         if(relation == null) {
            return name;
         }

         return name + ": [" + relation + "]";
      }

      public void writeContent(StringBuilder out) {
         writeContent(out, false);
      }

      public void writeContent(StringBuilder out, boolean ignoreName) {
         if(!ignoreName) {
            out.append(name);
         }

         out.append(relation);
         out.append(description);
         out.append(sql);

         for(int i = 0; i < nodes.size(); i++) {
            ((QueryNode) nodes.get(i)).writeContent(out, ignoreName);
         }
      }

      public boolean isSQLType() {
         return sql;
      }

      public void setSQLType(boolean sql) {
         this.sql = sql;
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof QueryNode)) {
            return false;
         }

         QueryNode obj0 = (QueryNode) obj;

         if(!name.equals(obj0.getName()) ||
            !Tool.equals(iconPath, obj0.getIconPath()) ||
            !Tool.equals(relation, obj0.getRelation()) ||
            !description.equals(obj0.getDescription()) ||
            obj0.isSQLType() != sql)
         {
            return false;
         }

         if(!Tool.equals(getNodes(), obj0.getNodes())) {
            return false;
         }

         return true;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.print("<node class=\"" + getClass().getName() + "\" name=\"" +
            name + "\" ");

         if(relation != null) {
            writer.print("relation=\"" + relation + "\" ");
         }

         if(iconPath != null) {
            writer.print("iconPath=\"" + iconPath + "\" ");
         }

         writer.println("description=\"" + Tool.byteEncode2(description) +
            "\" sql=\"" + sql + "\" >");
         writer.println("<nodes>");

         for(int i = 0; i < nodes.size(); i++) {
            ((QueryNode) nodes.get(i)).writeXML(writer);
         }

         writer.println("</nodes>");
         writer.println("</node>");
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         name = Tool.getAttribute(tag, "name");
         relation = Tool.getAttribute(tag, "relation");
         description = Tool.getAttribute(tag, "description");
         iconPath = Tool.getAttribute(tag, "iconPath");
         sql = "true".equals(Tool.getAttribute(tag, "sql"));

         Element nodesnode = Tool.getChildNodeByTagName(tag, "nodes");
         NodeList list = nodesnode.getChildNodes();
         nodes.clear();

         for(int i = 0; i < list.getLength(); i++) {
            QueryNode node = new QueryNode();
            node.parseXML((Element) list.item(i));
            addNode(node);
         }
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

      private List nodes = new ArrayList();
      private String description;
      private String tooltip;
      private String relation;
      private String name;
      private String iconPath;
      private boolean sql;


      public static final class Serializer extends StdSerializer<QueryNode> {
         public Serializer() {
            super(QueryNode.class);
         }

         @Override
         public void serialize(QueryNode node, JsonGenerator generator,
                               SerializerProvider provider) throws IOException
         {
            generator.writeStartObject();
            generator.writeStringField("name", node.getName());

            if(node.getRelation() != null) {
               generator.writeStringField("relation", node.getRelation());
            }

            if(node.getIconPath() != null) {
               generator.writeStringField("iconPath", node.getIconPath());
            }

            generator.writeStringField("description", node.getDescription());
            generator.writeStringField("tooltip", node.getTooltip());
            generator.writeBooleanField("sql", node.isSQLType());
            generator.writeObjectField("nodes", node.getNodes());

            generator.writeEndObject(); // node
         }
      }
   }

   private QueryNode root;
}
