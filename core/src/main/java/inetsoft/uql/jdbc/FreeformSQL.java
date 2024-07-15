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
import inetsoft.uql.util.sql.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.StringReader;

/**
 * FreeformSQL is a SQL query entered directly by user. The query is stored
 * as a string.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FreeformSQL implements SQLDefinition {
   /**
    * Create a freeform SQL.
    */
   public FreeformSQL() {
   }

   /**
    * Create a freeform SQL.
    */
   public FreeformSQL(String sql) {
      this.sql = sql;
   }

   /**
    * Get the free-from SQL string.
    */
   public String getSQL() {
      return sql;
   }

   /**
    * Set the free-from SQL string.
    */
   public void setSQL(String sql) {
      this.sql = sql;
      xselect = null;
   }

   /**
    * Get the selection column list.
    */
   @Override
   public XSelection getSelection() {
      if(xselect == null) {
         xselect = new SQLSelection();

         try {
            SQLParser parser = new SQLParser(new StringReader(sql));
            parser.query_spec((SQLSelection) xselect);
         }
         catch(ParseException e) {
         }
         catch(TokenMgrError e) {
         }
         catch(Exception e) {
            LOG.error("Failed to parse column selection from SQL query: " + sql, e);
         }
      }

      return xselect;
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
    * Parse the XML element that contains information on this sql.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      sql = Tool.getValue(node);
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<sql><![CDATA[" + sql + "]]></sql>");
   }

   /**
    * Convert to a SQL statement.
    */
   @Override
   public String getSQLString() {
      return sql;
   }

   /**
    * Convert to a SQL statement.
    */
   public String toString() {
      return sql;
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

   private String sql;
   private XSelection xselect;
   private JDBCDataSource ds;

   private static final Logger LOG =
      LoggerFactory.getLogger(FreeformSQL.class);
}
