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
package inetsoft.uql.erm;

import inetsoft.uql.jdbc.XSet;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * An XRelationship object represents a relationship, or join, in the
 * underlying data source of a data model. A relationship is between a column
 * of one table to a column of another table.<p>Note that the order of the
 * table/column pairs is arbitrary and has no effect on the resulting join.
 *
 * @author  InetSoft Technology Corp.
 * @since   4.4
 */
public class XRelationship implements Cloneable, Serializable {
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
   public static final String NOT_EQUAL = "<>";

   /**
    * Constant specifying an identifying (1-to-n) relationship.
    *
    * @deprecated these types of relationships are no longer supported.
    */
   @Deprecated
   public static final int IDENTIFYING = 0;

   /**
    * Constant specifying a nonidentifying (m-to-n) relationship.
    *
    * @deprecated these types of relationships are no longer supported.
    */
   @Deprecated
   public static final int NONIDENTIFYING = 1;

   /**
    * Cardinality one.
    */
   public static final int ONE = 1;

   /**
    * Cardinality many.
    */
   public static final int MANY = 2;

   /**
    * Creates a new instance of XRelationship. Default constructor that should
    * only be used when loading the relationship from an XML file.
    */
   public XRelationship() {
      super();
   }

   /**
    * Constructs a new instance of XRelationship that defines a join between
    * the specified tables and columns. For nonidentifying relationships, the
    * order of the tables is is arbitrary.
    *
    * @param dependentTable the dependent table of the relationship.
    * @param dependentColumn the column in the dependent table of the
    *                        relationship.
    * @param independentTable the independent table of the relationship.
    * @param independentColumn the column in the independent table of the
    *                          relationship.
    */
   public XRelationship(String dependentTable, String dependentColumn,
			String independentTable, String independentColumn) {
      this(dependentTable, dependentColumn, independentTable,
           independentColumn, EQUAL);
   }

   /**
    * Constructs a new instance of XRelationship that defines a join between
    * the specified tables and columns. For nonidentifying relationships, the
    * order of the tables is is arbitrary.
    *
    * @param dependentTable the dependent table of the relationship.
    * @param dependentColumn the column in the dependent table of the
    *                        relationship.
    * @param independentTable the independent table of the relationship.
    * @param independentColumn the column in the independent table of the
    *                          relationship.
    * @param type the type of the relationship. May be either
    *             {@link #IDENTIFYING} or {@link #NONIDENTIFYING}.
    *
    * @deprecated these types of relationships are no longer supported.
    */
   @Deprecated
   public XRelationship(String dependentTable, String dependentColumn,
			String independentTable, String independentColumn,
			int type) {
      this(dependentTable, dependentColumn, independentTable,
           independentColumn, EQUAL);
   }

   /**
    * Constructs a new instance of XRelationship that defines a join between
    * the specified tables and columns. For nonidentifying relationships, the
    * order of the tables is is arbitrary.
    *
    * @param dependentTable the dependent table of the relationship.
    * @param dependentColumn the column in the dependent table of the
    *                        relationship.
    * @param independentTable the independent table of the relationship.
    * @param independentColumn the column in the independent table of the
    *                          relationship.
    * @param joinType the type of join that the relationship represents.
    */
   public XRelationship(String dependentTable, String dependentColumn,
			String independentTable, String independentColumn,
                        String joinType) {
      this.dependentTable = dependentTable;
      this.dependentColumn = dependentColumn;
      this.independentTable = independentTable;
      this.independentColumn = independentColumn;
      this.joinType = joinType;
   }

   /**
    * Sets the dependent table and column in this relationship. For
    * nonidentifying relationships, the order is arbitrary.
    *
    * @param table the table to be joined.
    * @param column the column on which to join.
    *
    * @since 5.0
    */
   public void setDependent(String table, String column) {
      dependentTable = table;
      dependentColumn = column;
   }

   /**
    * Gets the dependent table in this relationship. For
    * nonidentifying relationships, the order is arbitrary.
    *
    * @return the table name.
    *
    * @since 5.0
    */
   public String getDependentTable() {
      return dependentTable;
   }

   /**
    * Gets the column on which the dependent table is joined. For
    * nonidentifying relationships, the order is arbitrary.
    *
    * @return the column name.
    *
    * @since 5.0
    */
   public String getDependentColumn() {
      return dependentColumn;
   }

   /**
    * Sets the independent table and column in this relationship. For
    * nonidentifying relationships, the order is arbitrary.
    *
    * @param table the table to be joined.
    * @param column the column on which to join.
    *
    * @since 5.0
    */
   public void setIndependent(String table, String column) {
      independentTable = table;
      independentColumn = column;
   }

   /**
    * Gets the independent table in this relationship. For
    * nonidentifying relationships, the order is arbitrary.
    *
    * @return the table name.
    *
    * @since 5.0
    */
   public String getIndependentTable() {
      return independentTable;
   }

   /**
    * Gets the column on which the independent table is joined. For
    * nonidentifying relationships, the order is arbitrary.
    *
    * @return the column name.
    *
    * @since 5.0
    */
   public String getIndependentColumn() {
      return independentColumn;
   }

   /**
    * Gets the type of this relationship.
    *
    * @return one of {@link #IDENTIFYING} or {@link #NONIDENTIFYING}.
    *
    * @since 5.0
    *
    * @deprecated these types of relationships are no longer supported.
    */
   @Deprecated
   public int getType() {
      return IDENTIFYING;
   }

   /**
    * Sets the type of this relationship.
    *
    * @param type one of {@link #IDENTIFYING} or {@link #NONIDENTIFYING}.
    *
    * @since 5.0
    *
    * @deprecated these types of relationships are no longer supported.
    */
   @Deprecated
   public void setType(int type) {
   }

   /**
    * Check if this is a weak join.
    */
   public boolean isWeakJoin() {
      return weak;
   }

   /**
    * Set the weak join flag.
    */
   public void setWeakJoin(boolean weak) {
      this.weak = weak;
   }

   /**
    * Gets the type of join that this relationship represents.
    *
    * @return joinType the join type.
    */
   public String getJoinType() {
      return joinType;
   }

   /**
    * Sets the type of join that this relationship represents.
    *
    * @param joinType the new join type.
    */
   public void setJoinType(String joinType) {
      this.joinType = joinType;
   }

   /**
    * Gets the order that this relationship represents.
    *
    * @return the order that this relationship represents.
    */
   public int getOrder() {
      return order;
   }

   /**
    * Sets the order that this relationship represents.
    *
    * @param order the new order.
    */
   public void setOrder(int order) {
      this.order = order;
   }

   /**
    * Gets the merging that this relationship represents.
    *
    * @return joinType the join type.
    */
   public String getMerging() {
      return merging;
   }

   /**
    * Get the dependent table cardinality.
    */
   public int getDependentCardinality() {
      return dependentCardinality;
   }

   /**
    * Set the dependent table cardinality.
    * @param dependentCardinality the dependentCardinality to be set.
    */
   public void setDependentCardinality(int dependentCardinality) {
      this.dependentCardinality = dependentCardinality;
   }

   /**
    * Get the independent table cardinality.
    */
   public int getIndependentCardinality() {
      return independentCardinality;
   }

   /**
    * Set the independent table cardinality.
    * @param independentCardinality the independentCardinality to be set.
    */
   public void setIndependentCardinality(int independentCardinality) {
      this.independentCardinality = independentCardinality;
   }

   /**
    * Sets the merging that this relationship represents.
    *
    * @param merging the new join type.
    */
   public void setMerging(String merging) {
      this.merging = merging;
   }

   /**
    * Writes the XML representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   protected void writeXML(PrintWriter writer) {
      writer.println("<relationship weak=\"" + isWeakJoin() + "\" joinType=\"" +
            Tool.escape(getJoinType()) + "\" orderPriority=\"" + getOrder() +
            "\" merging=\"" + Tool.escape(getMerging()) + "\">");
      writer.println("<independent>");
      writer.println("<table><![CDATA[" + getIndependentTable() +"]]></table>");
      writer.println("<column><![CDATA[" + getIndependentColumn() +
                     "]]></column>");
      writer.println("<cardinality><![CDATA[" + getIndependentCardinality() +
                     "]]></cardinality>");
      writer.println("</independent>");
      writer.println("<dependent>");
      writer.println("<table><![CDATA[" + getDependentTable() + "]]></table>");
      writer.println("<column><![CDATA[" + getDependentColumn() +
                     "]]></column>");
      writer.println("<cardinality><![CDATA[" + getDependentCardinality() +
                     "]]></cardinality>");
      writer.println("</dependent>");
      writer.println("</relationship>");
   }

   /**
    * Read in a relationship definition from its XML representation.
    *
    * @param tag the XML element that represents this relationship.
    *
    * @throws DOMException if an error occurs while parsing the XML element.
    */
   public void parseXML(Element tag) throws DOMException {
      String attr = null;

      if((attr = Tool.getAttribute(tag, "weak")) != null) {
         setWeakJoin(attr.equals("true"));
      }

      if((attr = Tool.getAttribute(tag, "joinType")) != null) {
         setJoinType(attr);
      }

      if((attr = Tool.getAttribute(tag, "orderPriority")) != null) {
         setOrder(Integer.parseInt(attr));
      }

      if((attr = Tool.getAttribute(tag, "merging")) != null) {
         setMerging(attr);
      }

      String tbl = null;
      String col = null;
      int cardinality = 0;
      NodeList nl = Tool.getChildNodesByTagName(tag, "independent");

      if(nl == null || nl.getLength() < 1) {
         // backward compatibility
         nl = Tool.getChildNodesByTagName(tag, "right");
      }

      if(nl != null && nl.getLength() > 0) {
         Element map = (Element) nl.item(0);
         NodeList nl2 = Tool.getChildNodesByTagName(map, "table");

         if(nl2 != null && nl2.getLength() > 0) {
            tbl = Tool.getValue(nl2.item(0));
            tbl = tbl == null || tbl.equals("") ? null : tbl;
         }

         nl2 = Tool.getChildNodesByTagName(map, "column");

         if(nl2 != null && nl2.getLength() > 0) {
            col = Tool.getValue(nl2.item(0));
            col = col == null || col.equals("") ? null : col;
         }

         setIndependent(tbl, col);

         nl2 = Tool.getChildNodesByTagName(map, "cardinality");

         if(nl2 != null && nl2.getLength() > 0) {
            try {
               cardinality = Integer.parseInt(Tool.getValue(nl2.item(0)));
            }
            catch(Exception exp) {
               cardinality = 0;
            }
         }
         else if(nl2 == null) {
            //bug1374801973015, for bc.
            cardinality = 2;
         }

         setIndependentCardinality(cardinality);
      }

      nl = Tool.getChildNodesByTagName(tag, "dependent");

      if(nl == null || nl.getLength() < 1) {
         // backward compatibility
         nl = Tool.getChildNodesByTagName(tag, "left");
      }

      if(nl != null && nl.getLength() > 0) {
         Element map = (Element) nl.item(0);
         NodeList nl2 = Tool.getChildNodesByTagName(map, "table");

         if(nl2 != null && nl2.getLength() > 0) {
            tbl = Tool.getValue(nl2.item(0));
            tbl = tbl == null || tbl.equals("") ? null : tbl;
         }

         nl2 = Tool.getChildNodesByTagName(map, "column");

         if(nl2 != null && nl2.getLength() > 0) {
            col = Tool.getValue(nl2.item(0));
            col = col == null || col.equals("") ? null : col;
         }

         setDependent(tbl, col);

         nl2 = Tool.getChildNodesByTagName(map, "cardinality");

         if(nl2 != null && nl2.getLength() > 0) {
            try {
               cardinality = Integer.parseInt(Tool.getValue(nl2.item(0)));
            }
            catch(Exception exp) {
               cardinality = 0;
            }
         }
         else if(nl2 == null) {
            //bug1374801973015, for bc.
            cardinality = 2;
         }

         setDependentCardinality(cardinality);
      }
   }

   /**
    * Gets a textual representation of this relationship.
    *
    * @return a string representation of this object. This value will have the
    *         format <CODE>XRelationship: <I>table1</i>.<i>column</i> -&gt;
    *         <i>table2</i>.<i>column</i></CODE>
    */
   public String toString() {
      return "XRelationship: " + getDependentTable() + "." +
         getDependentColumn() + "[" + getDependentCardinality() + "] " +
         joinType + " " + getIndependentTable() + "." + getIndependentColumn() +
         "[" + getIndependentCardinality() + "]";
   }

   /**
    * Creates and returns a copy of this relationship object.
    *
    * @return a clone of this instance.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    * @param obj the reference object with which to compare.
    */
   public boolean equals(Object obj) {
      return equals0(obj, true);
   }

   /**
    * Indicates whether other object is "equal to" this one without cardinality.
    * @param obj the reference object with which to compare.
    */
   public boolean equalContents(Object obj) {
      return equals0(obj, false);
   }

   /**
    * Indicates whether some other object is "equal to" this one.
    *
    * @param obj the reference object with which to compare.
    *
    * @since 5.0
    */
   private boolean equals0(Object obj, boolean containsCardinality) {
      if(!(obj instanceof XRelationship)) {
         return false;
      }

      XRelationship that = (XRelationship) obj;

      // joins that are equal or not equal are symmetric, so the order of the
      // tables is not important
      if(getJoinType().equals(EQUAL) || getJoinType().equals(NOT_EQUAL)) {
         if(this.getDependentTable().equals(that.getDependentTable())) {
            return
               this.getDependentColumn().equals(that.getDependentColumn()) &&
               this.getIndependentTable().equals(that.getIndependentTable()) &&
               this.getIndependentColumn().equals(that.getIndependentColumn())&&
               (!containsCardinality ||
               this.getIndependentCardinality() ==
                  that.getIndependentCardinality() &&
               this.getDependentCardinality() ==
                  that.getDependentCardinality());
         }
         else if(this.getIndependentTable().equals(that.getDependentTable())) {
            return
               this.getIndependentColumn().equals(that.getDependentColumn()) &&
               this.getDependentTable().equals(that.getIndependentTable()) &&
               this.getDependentColumn().equals(that.getIndependentColumn()) &&
               (!containsCardinality ||
               this.getIndependentCardinality() ==
                  that.getIndependentCardinality() &&
               this.getDependentCardinality() ==
                  that.getDependentCardinality());
         }
      }

      return this.getDependentTable().equals(that.getDependentTable()) &&
         this.getDependentColumn().equals(that.getDependentColumn()) &&
         this.getIndependentTable().equals(that.getIndependentTable()) &&
         this.getIndependentColumn().equals(that.getIndependentColumn()) &&
         (!containsCardinality ||
         this.getIndependentCardinality() ==
            that.getIndependentCardinality() &&
         this.getDependentCardinality() ==
            that.getDependentCardinality());
   }

   private String dependentTable;
   private String dependentColumn;
   private int dependentCardinality;
   private String independentTable;
   private String independentColumn;
   private int independentCardinality;
   private boolean weak = false; // weak join
   private String joinType = EQUAL;
   private int order = 1;
   private String merging = XSet.AND;

   private static final Logger LOG =
      LoggerFactory.getLogger(XRelationship.class);
}
