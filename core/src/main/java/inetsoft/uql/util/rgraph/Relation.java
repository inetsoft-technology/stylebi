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
package inetsoft.uql.util.rgraph;


/**
 * A join relation.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Relation extends TableColumn {
   /**
    * Equal join.
    */
   public static final String EQUAL = "=";
   /**
    * Left outer join.
    */
   public static final String LEFT_OUTER = "*=";
   /**
    * Right outer join.
    */
   public static final String RIGHT_OUTER = "=*";
   /**
    * Full outer join.
    */
   public static final String FULL_OUTER = "*=*";
   /**
    * Greater than join.
    */
   public static final String GREATER = ">";
   /**
    * Less than join.
    */
   public static final String LESS = "<";
   /**
    * Greater than or equal to join.
    */
   public static final String GREATER_EQUAL = ">=";
   /**
    * Less than or equal to join.
    */
   public static final String LESS_EQUAL = "<=";
   /**
    * Not equal join.
    */
   public static final String NOT_EQUAL = "!=";
   public static final String NOT_EQUAL2 = "<>";
   /**
    * Create a relation.
    */
   public Relation(TableNode table, String column) {
      super(table, column);
   }

   /**
    * Create a relation.
    */
   public Relation(TableNode table, String column, String op) {
      super(table, column);
      this.op = op;
   }

   /**
    * Get the relation type, one of constants defined in this class.
    */
   public String getType() {
      return op;
   }

   /**
    * Set the relation type.
    */
   public void setType(String op) {
      this.op = op;
   }

   /**
    * Two relation are equal if they belong to the same table and have
    * same names, and use the same operator.
    */
   public boolean equals(Object obj) {
      if(super.equals(obj)) {
         return op.equals(((Relation) obj).op);
      }

      return false;
   }

   private String op = EQUAL;
}

