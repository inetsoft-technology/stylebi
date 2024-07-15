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
package inetsoft.uql.xmla;

import inetsoft.util.Tool;

/**
 * The XMLASet extends XMLANode to store information of
 * set condition.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class XMLASet extends XMLANode {
   /**
    * 'And' relation.
    */
   public static final String AND = "and";
   /**
    * 'Or' relation.
    */
   public static final String OR = "or";

   /**
    * Create a default set.
    */
   public XMLASet() {
   }

   /**
    * Create a set with 'and' or 'or' relation.
    */
   public XMLASet(String relation) {
      this.relation = relation;
      setValue(relation);
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
    * Compare if two nodes are equal. Two nodes are considered equal if they
    * have same name.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof XMLASet)) {
         return false;
      }

      XMLASet set = (XMLASet) obj;

      if(!Tool.equals(set.relation, relation)) {
         return false;
      }

      if(set.getChildCount() != getChildCount()) {
         return false;
      }

      for(int i = 0; i < getChildCount(); i++) {
         if(!Tool.equals(getChild(i), set.getChild(i))) {
            return false;
         }
      }

      return true;
   }

   /**
    * Calculate hash code of a node.
    */
   public int hashCode() {
      int hash = relation == null ? 0 : relation.hashCode() / 2;
      int cnt = getChildCount();

      for(int i = 0; i < getChildCount(); i++) {
         if(getChild(i) == null) {
            continue;
         }

         int h0 = getChild(i).hashCode();

         hash += h0 / cnt;
      }

      return hash;
   }

   private String relation;
}