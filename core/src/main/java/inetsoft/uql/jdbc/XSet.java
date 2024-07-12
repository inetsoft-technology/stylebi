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
package inetsoft.uql.jdbc;

import inetsoft.uql.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * The XSet extends XFilterNode to store information of
 * set condition of where clause and having clause.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XSet extends XFilterNode {
   /**
    * 'And' relation.
    */
   public static final String AND = "and";
   /**
    * 'Or' relation.
    */
   public static final String OR = "or";
   public static final String XML_TAG = "XSet";
   /**
    * Create a default set.
    */
   public XSet() {
   }

   /**
    * Create a set with 'and' or 'or' relation.
    */
   public XSet(String relation) {
      this.relation = relation;
   }

   public String toString() {
      String str = (isIsNot() ? "not " : "") + "(";

      for(int i = 0; i < getChildCount(); i++) {
         if(i > 0) {
            str += " " + getRelation() + " ";
         }

         str += getChild(i);
      }

      return str + ")";
   }

   /**
    * Parse XML definition.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      this.setRelation(node.getAttribute("relation"));
      this.setIsNot(Boolean.parseBoolean(node.getAttribute("isnot")));
      this.setGroup(Boolean.parseBoolean(node.getAttribute("group")));
      this.setClause(node.getAttribute("clause"));
      NodeList childrenList = node.getChildNodes();
      Element child;
      int nameIndex = 0;

      if(childrenList != null) {
         for(int i = 0; i < childrenList.getLength(); i++) {
            if(!(childrenList.item(i) instanceof Element)) {
               continue;
            }

            child = (Element) childrenList.item(i);
            XFilterNode condition = null;

            if(child.getTagName().equals(XUnaryCondition.XML_TAG)) {
               condition = new XUnaryCondition();
            }
            else if(child.getTagName().equals(XExpressionCondition.XML_TAG)) {
               condition = new XExpressionCondition();
            }
            else if(child.getTagName().equals(XBinaryCondition.XML_TAG)) {
               condition = new XBinaryCondition();
            }
            else if(child.getTagName().equals(XTrinaryCondition.XML_TAG)) {
               condition = new XTrinaryCondition();
            }
            else if(child.getTagName().equals(XJoin.XML_TAG)) {
               condition = new XJoin();
            }
            else if(child.getTagName().equals(XSet.XML_TAG)) {
               condition = new XSet();
            }

            if(condition != null) {
               condition.parseXML(child);
               condition.setName("name" + nameIndex++);
               this.addChild(condition);
            }
         }
      }
   }

   /**
    * Write XML definition.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<");
      writer.print(XML_TAG);
      writer.print(" relation=" + "\"" + getRelation() + "\"");
      writer.print(" isnot=" + (isIsNot() ? "\"true\"" : "\"false\""));
      writer.print(" group=" + (isGroup() ? "\"true\"" : "\"false\""));
      writer.print(" clause=\"" + getClause() + "\"");
      writer.println(">");

      int childCount;
      XNode node;

      childCount = this.getChildCount();

      for(int i = 0; i < childCount; i++) {
         node = this.getChild(i);

         if(node instanceof XFilterNode) {
            node.writeXML(writer);
         }
         else {
            writeXML(writer);
         }
      }

      writer.println("</" + XML_TAG + ">");
   }

   /**
    * Get the relation between nodes.
    */
   public String getRelation() {
      return relation;
   }

   /**
    * Set the relation between nodes.
    */
   public void setRelation(String relation) {
      this.relation = relation;
   }

   /**
    * Get the dependent table for this set of joins (to check if we need parentheses)
    */
   public String getDependentTable() {
      return dependentTable;
   }

   public void setDependentTable(String dependentTable) {
      this.dependentTable = dependentTable;
   }

   /**
    * Get the independent table for this set of joins (to check if we need to force AND merging)
    */
   public String getIndependentTable() {
      return independentTable;
   }

   public void setIndependentTable(String independentTable) {
      this.independentTable = independentTable;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
         return null;
      }
   }

   private String relation;
   private String dependentTable;
   private String independentTable;
   private static final Logger LOG = LoggerFactory.getLogger(XSet.class);
}

