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
package inetsoft.uql.erm.transform;

import inetsoft.util.Tool;
import inetsoft.util.XMLTool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * XPartitionTransformer, transforms a partition node.
 *
 * version 10.1
 * @author InetSoft Technology Corp.
 */
public class XPartitionTransformer implements ERMTransformer {
   /**
    * Init the real physical table list.
    */
   private void initPhysicalTables(Element elem, TransformDescriptor desc) {
      NodeList nl = Tool.getChildNodesByTagName(elem, "table");

      for(int i = 0; nl != null && i < nl.getLength(); i++) {
         Element elem0 = (Element) nl.item(i);
         String name = Tool.getAttribute(elem0, "name");
         tables.add(name);
         Element aliasNode = Tool.getChildNodeByTagName(elem0, "aliasTable");
         Element autoNode = Tool.getChildNodeByTagName(elem0, "autoAlias");

         if(aliasNode != null) {
            String rtable = Tool.getValue(aliasNode);
            aliases.add(name);
            tables.add(rtable);
         }

         if(autoNode != null) {
            NodeList jnodes =
               Tool.getChildNodesByTagName(autoNode, "incomingJoin");

            for(int j = 0; jnodes != null && j < jnodes.getLength();j++) {
               Element inode = (Element) jnodes.item(j);
               Element anode = Tool.getChildNodeByTagName(inode, "alias");
               Element pnode = Tool.getChildNodeByTagName(inode, "prefix");

               if(anode != null) {
                  String atable = Tool.getValue(anode);
                  aliases.add(atable);
               }

               if(pnode != null) {
                  String ptable = Tool.getValue(pnode);
                  pset.add(ptable);
               }
            }
         }
      }

      tables.removeAll(aliases);
   }

   /**
    * Transform the element according to the descriptor.
    * @param elem the element.
    * @param descriptor the transform descriptor.
    */
   @Override
   public void transform(Element elem, TransformDescriptor descriptor) {
      if(pelem != null) {
         initPhysicalTables(pelem, descriptor);
      }

      initPhysicalTables(elem, descriptor);

      // transform table, aliastable, autoalias table name of xpartition
      NodeList tnodes = Tool.getChildNodesByTagName(elem, "table");

      if(tnodes != null) {
         transformTables(tnodes, descriptor);
      }

      // transform table and column names of relationship
      NodeList relations = Tool.getChildNodesByTagName(elem, "relationship");

      if(relations != null) {
         transformRelationShips(relations, descriptor);
      }
   }

   /**
    * Set the parent element node.
    */
   public void setParentElement(Element pelem) {
      this.pelem = pelem;
   }

   /**
    * Get the tables of each partition node.
    */
   public Set getTables() {
      return tables;
   }

   /**
    * Set tables of the root partition.
    */
   public void setTables(Set tables) {
      this.tables = tables;
   }

   /**
    * Transform the table names.
    */
   private void transformTables(NodeList nodes, TransformDescriptor desc) {
      for(int i = 0; nodes != null && i < nodes.getLength(); i++) {
         Element elem0 = (Element) nodes.item(i);

         // transform table name of xpartition
         String name = Tool.getAttribute(elem0, "name");
         elem0.setAttribute("name", transformTable(name, desc));

         // transform auto alias table name of xpartition
         Element aNode = Tool.getChildNodeByTagName(elem0, "autoAlias");

         if(aNode != null) {
            NodeList jnodes =
               Tool.getChildNodesByTagName(aNode, "incomingJoin");

            for(int j = 0; jnodes != null && j < jnodes.getLength();j++) {
               Element inode = (Element) jnodes.item(j);
               Element tnode = Tool.getChildNodeByTagName(inode, "table");

               if(tnode != null) {
                  String name0 = Tool.getValue(tnode);
                  XMLTool.replaceValue(tnode, transformTable(name0, desc));
               }
            }
         }

         // transform alias table name of xpartition
         Element aliasTbl = Tool.getChildNodeByTagName(elem0, "aliasTable");

         if(aliasTbl != null) {
            String name0 = Tool.getValue(aliasTbl);
            XMLTool.replaceValue(aliasTbl, transformTable(name0, desc));
         }
      }
   }

   /**
    * Transform the table and columns names of relationships.
    */
   private void transformRelationShips(NodeList nodes, TransformDescriptor desc)
   {
      for(int i = 0; nodes != null && i < nodes.getLength(); i++) {
         Element elem0 = (Element) nodes.item(i);
         Element independent = Tool.getChildNodeByTagName(elem0, "independent");
         Element dependent = Tool.getChildNodeByTagName(elem0, "dependent");
         Element itable = Tool.getChildNodeByTagName(independent, "table");
         Element dtable = Tool.getChildNodeByTagName(dependent, "table");
         Element icolumn = Tool.getChildNodeByTagName(independent, "column");
         Element dcolumn = Tool.getChildNodeByTagName(dependent, "column");

         if(itable != null && icolumn != null) {
            String tname = Tool.getValue(itable);
            String cname = Tool.getValue(icolumn);
            desc.replaceColumnNode(icolumn, independent,
               desc.transformColName(cname));

            if(isPhysicalTable(tname)) {
               desc.replaceTableNode(itable, independent,
                  desc.transformColName(tname));
            }
         }

         if(dtable != null && dcolumn != null) {
            String tname = Tool.getValue(dtable);
            String cname = Tool.getValue(dcolumn);
            desc.replaceColumnNode(dcolumn, dependent,
               desc.transformColName(cname));

            if(isPhysicalTable(tname)) {
               desc.replaceTableNode(dtable, dependent,
                  desc.transformColName(tname));
            }
         }
      }
   }

   /**
    * Check if the table a physical table.
    * @param table the specified table.
    */
   private boolean isPhysicalTable(String table) {
      return tables.contains(table);
   }

   /**
    * Check if the table an alias table.
    * @param table the specified table.
    */
   private boolean isAliasTable(String table) {
       return aliases.contains(table);
   }

   /**
    * Transform table.
    * @param table the specified table.
    * @return the transformed table.
    */
   @Override
   public String transformTable(String table, TransformDescriptor descriptor) {
      if(isAliasTable(table)) {
         return table;
      }
      else if(isPhysicalTable(table)) {
         return descriptor.transformTableName(table);
      }
      else {
         Iterator it = pset.iterator();
         String prefix = null;

         while(it.hasNext()) {
            String tprefix = it.next() + "_";

            if(table.startsWith(tprefix)) {
               prefix = tprefix;
               break;
            }
         }

         if(prefix != null) {
            String table2 = table.substring(prefix.length());
            table2 = transformTable(table2, descriptor);
            return prefix + table2;
         }
         else {
            Iterator ait = aliases.iterator();
            String alias = null;

            while(ait.hasNext()) {
               String talias = ait.next() + "_";

               if(table.startsWith(talias)) {
                  alias = talias;
                  break;
               }
            }

            if(alias != null) {
               String atable = table.substring(alias.length());
               atable = transformTable(atable, descriptor);
               return alias + atable;
            }
            else {
               System.err.println("Table not found: " + table);
               return table;
            }
         }
      }
   }

   private Set tables = new HashSet(); // physical table set
   private Set aliases = new HashSet(); // alias set
   private Set pset = new HashSet(); // prefix set
   private Element pelem; // parent element node
}