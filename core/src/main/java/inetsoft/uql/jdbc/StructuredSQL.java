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
package inetsoft.uql.jdbc;

import inetsoft.uql.XNode;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.util.KeywordProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.expr.ExprParser;
import inetsoft.uql.util.expr.Token;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.StringReader;
import java.util.*;

/**
 * The StructuredSQL contains the information on a SQL select statement.
 * These include the column list, aliases, from clause, where clause,
 * and order by clause. The SQL supported by the StructuredSQL is limited.
 * It is used primarily to store the visually defined SQL statement.
 *
 * @deprecated 4.4
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
@Deprecated
public class StructuredSQL implements SQLDefinition {
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
   public static final String NOT_EQUAL = "!=";
   /**
    * Set the selection column list.
    */
   public void setSelection(XSelection xselect) {
      this.xselect = xselect;
   }

   /**
    * Get the selection column list.
    */
   @Override
   public XSelection getSelection() {
      return xselect;
   }

   /**
    * Set the selection condition for a column (where clause).
    */
   public void setCondition(String column, String cond) {
      if(cond == null) {
         conds.remove(column);
      }
      else {
         conds.put(column, cond);
      }
   }

   /**
    * Get the selection condition for a column.
    */
   public String getCondition(String column) {
      return (String) conds.get(column);
   }

   /**
    * Get all columns with associated condition.
    * @return column names.
    */
   public Enumeration getConditionColumns() {
      return conds.keys();
   }

   /**
    * Add a table to the 'from' clause.
    */
   public void addTable(String table) {
      if(tables.indexOf(table) < 0) {
         tables.addElement(table);
      }
   }

   /**
    * Get the number of tables in the 'from' clause.
    */
   public int getTableCount() {
      return tables.size();
   }

   /**
    * Get the specified table in the 'from' clause.
    */
   public String getTable(int idx) {
      return (String) tables.elementAt(idx);
   }

   /**
    * Add a join between two table columns.
    */
   public void addJoin(String table1, String column1, String table2,
                       String column2, String op) {
      joins.addElement(new Join(table1, column1, table2, column2, op));
   }

   /**
    * Get the number of joins defined in this SQL.
    */
   public int getJoinCount() {
      return joins.size();
   }

   /**
    * Get the specified join definition.
    */
   public Join getJoin(int idx) {
      return (Join) joins.elementAt(idx);
   }

   /**
    * Set the sorting order on a column.
    */
   public void setSorting(String column, String order) {
      if(order != null) {
         order = order.toLowerCase();
         if(order.startsWith("asc")) {
            order = "asc";
         }
         else if(order.startsWith("desc")) {
            order = "desc";
         }
         else {
            order = null;
         }
      }

      if(order == null) {
         sorting.remove(column);
      }
      else {
         sorting.put(column, order);
      }
   }

   /**
    * Get the sorting order of a column.
    */
   public String getSorting(String column) {
      return (String) sorting.get(column);
   }

   /**
    * Get all sorted columns.
    */
   public Enumeration getSortedColumns() {
      return sorting.keys();
   }

   /**
    * Get a variable value for a name. If the variable is not defined,
    * it returns null.
    * @param name variable name.
    * @return variable definition.
    */
   @Override
   public UserVariable getVariable(String name) {
      return null;
   }

   /**
    * Set the value of a variable.
    */
   @Override
   public void setVariable(String name, XValueNode value) {
   }

   /**
    * Select columns from the table.
    */
   @Override
   public XNode select(XNode root) {
      return root;
   }

   /**

   /**
    * Parse the XML element that contains information on this sql.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      NodeList nlist = Tool.getChildNodesByTagName(node, "select");

      if(nlist != null && nlist.getLength() > 0) {
         xselect = XSelection.parse(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(node, "condition");
      if(nlist != null) {
         for(int i = 0; i < nlist.getLength(); i++) {
            Element elem = (Element) nlist.item(i);

            conds.put(elem.getAttribute("column"), Tool.getValue(elem));
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "table");
      if(nlist != null) {
         tables = new Vector();
         for(int i = 0; i < nlist.getLength(); i++) {
            tables.addElement(Tool.getValue(nlist.item(i)));
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "join");
      if(nlist != null) {
         joins = new Vector();

         for(int i = 0; i < nlist.getLength(); i++) {
            Join join = new Join();

            join.parseXML((Element) nlist.item(i));
            joins.addElement(join);
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "sort");
      if(nlist != null) {
         for(int i = 0; i < nlist.getLength(); i++) {
            Element elem = (Element) nlist.item(i);

            sorting.put(elem.getAttribute("name"), elem.getAttribute("order"));
         }
      }
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<structured_sql>");
      writer.println("<select><![CDATA[" + xselect.toString() + "]]></select>");

      Enumeration ckeys = conds.keys();

      while(ckeys.hasMoreElements()) {
         String col = (String) ckeys.nextElement();

         writer.println("<condition column=\"" + Tool.escape(col) +
            "\"><![CDATA[" + conds.get(col) + "]]></condition>");
      }

      for(int i = 0; i < tables.size(); i++) {
         writer.println("<table>" +
            Tool.escape(tables.elementAt(i).toString()) + "</table>");
      }

      for(int i = 0; i < joins.size(); i++) {
         ((Join) joins.elementAt(i)).writeXML(writer);
      }

      Enumeration keys = sorting.keys();

      while(keys.hasMoreElements()) {
         String name = (String) keys.nextElement();

         writer.println("<sort name=\"" + Tool.escape(name) + "\" order=\"" +
            sorting.get(name) + "\"/>");
      }

      writer.println("</structured_sql>");
   }

   // defines a table join
   public static class Join {
      public Join() {
         super();
      }

      public Join(String t1, String c1, String t2, String c2, String op) {
         this.table1 = t1;
         this.column1 = c1;
         this.table2 = t2;
         this.column2 = c2;
         this.op = op;
      }

      @Override
      protected Object clone() throws CloneNotSupportedException {
         return super.clone();
      }

      public void parseXML(Element root) {
         table1 = Tool.getAttribute(root, "table1");
         table2 = Tool.getAttribute(root, "table2");
         column1 = Tool.getAttribute(root, "column1");
         column2 = Tool.getAttribute(root, "column2");
         op = Tool.getAttribute(root, "op");

         if(op == null || op.length() == 0) {
            op = EQUAL;
         }
      }

      public void writeXML(PrintWriter writer) {
         writer.println("<join table1=\"" + table1 + "\" column1=\"" + column1 +
            "\" table2=\"" + table2 + "\" column2=\"" + column2 + "\" op=\"" +
            op + "\"/>");
      }

      public void setKeywordProvider(KeywordProvider provider) {
         this.provider = provider;
      }

      public KeywordProvider getKeywordProvider() {
         return provider;
      }

      public String toString() {
         return XUtil.quoteName(table1, provider) + "." +
            XUtil.quoteName(column1, provider) + " " +
            op + " " + XUtil.quoteName(table2, provider) + "." +
            XUtil.quoteName(column2, provider);
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Join join = (Join) o;

         if(table1 != null ?
            !table1.equals(join.table1) : join.table1 != null)
         {
            return false;
         }

         if(column1 != null ?
            !column1.equals(join.column1) : join.column1 != null)
         {
            return false;
         }

         if(table2 != null ?
            !table2.equals(join.table2) : join.table2 != null)
         {
            return false;
         }

         if(column2 != null ?
            !column2.equals(join.column2) : join.column2 != null)
         {
            return false;
         }

         return !(op != null ? !op.equals(join.op) : join.op != null);

      }

      @Override
      public int hashCode() {
         int result = table1 != null ? table1.hashCode() : 0;
         result = 31 * result + (column1 != null ? column1.hashCode() : 0);
         result = 31 * result + (table2 != null ? table2.hashCode() : 0);
         result = 31 * result + (column2 != null ? column2.hashCode() : 0);
         result = 31 * result + (op != null ? op.hashCode() : 0);
         return result;
      }

      /**
       * op defines the join type, it's one of: =, *=, =*, >, <, >=, <=, and
       * !=.
       */
      public String table1, column1, table2, column2, op;
      private KeywordProvider provider;
   }

   /**
    * Convert to a SQL statement.
    */
   public String toString() {
      return toString(false);
   }

   /**
    * Get the data source.
    */
   @Override
   public JDBCDataSource getDataSource() {
      return ds;
   }

   /**
    * Set the data source.
    */
   @Override
   public void setDataSource(JDBCDataSource ds) {
      this.ds = ds;
   }

   /**
    * Convert to a SQL statement.
    */
   @Override
   public String getSQLString() {
      return toString(true);
   }

   /**
    * Convert to string.
    */
   private String toString(boolean sql) {
      SQLHelper helper = SQLHelper.getSQLHelper(ds);
      StringBuilder str = new StringBuilder(xselect.toString(sql));

      str.append(" from ");
      for(int i = 0; i < tables.size(); i++) {
         str.append(XUtil.quoteName((String) tables.elementAt(i), helper));
         if(i < tables.size() - 1) {
            str.append(",");
         }
      }

      // construct the column conditions
      boolean where = false;
      boolean first = true;
      Enumeration ckeys = conds.keys();

      // column conditions
      while(ckeys.hasMoreElements()) {
         String cname = (String) ckeys.nextElement();

         if(!first) {
            str.append(" and ");
         }
         else {
            str.append(" where ");
            where = true;
            first = false;
         }

         String cond = (String) conds.get(cname);

         try {
            str.append("(" +
               substitute(cond, "this", XUtil.quoteName(cname, helper)) + ")");
         }
         catch(Throwable e) {
            LOG.warn("Failed to build SQL condition: " + cond, e);
            str.append("(" + cond + ")");
         }
      }

      // join conditions
      for(int i = 0; i < joins.size(); i++) {
         if(!where) {
            str.append(" where ");
            where = true;
         }
         else {
            str.append(" and ");
         }

         Join join = (Join) joins.get(i);
         join.setKeywordProvider(helper);
         str.append("(" + join + ")");
      }

      if(sorting.size() > 0) {
         str.append(" order by ");
         first = true;
         Enumeration keys = sorting.keys();

         while(keys.hasMoreElements()) {
            String cname = (String) keys.nextElement();
            String sort = getSorting(cname);

            if(sort == null) {
               continue;
            }
            else if(!first) {
               str.append(",");
            }

            str.append(XUtil.quoteName(cname, helper) + " " + sort);
            first = false;
         }
      }

      return str.toString();
   }

   // replace string.
   String substitute(String str, String olds, String news) throws Exception {
      ExprParser parser = new ExprParser(new StringReader(str));
      Token token;
      Vector locs = new Vector(); // int[] = [begincol, endcol]

      while((token = parser.getNextToken()) != null && token.kind != 0) {
         if(token.image.equals(olds)) {
            locs.addElement(new int[] {token.beginColumn, token.endColumn});
         }
      }

      StringBuilder out = new StringBuilder(str);

      for(int i = locs.size() - 1; i >= 0; i--) {
         int[] loc = (int[]) locs.elementAt(i);

         out.replace(loc[0] - 1, loc[1], news);
      }

      return out.toString();
   }

   /**
    * Make a copy of this sql.
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

   XSelection xselect = new XSelection(); // column selection list
   Hashtable conds = new Hashtable(); // column path -> column condition
   Vector tables = new Vector(); // table list
   Vector joins = new Vector(); // Join
   Hashtable sorting = new Hashtable(); // column -> sorting (asc, desc)
   JDBCDataSource ds; // data source

   private static final Logger LOG =
      LoggerFactory.getLogger(StructuredSQL.class);
}
