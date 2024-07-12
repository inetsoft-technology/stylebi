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
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XValueNode;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * The is the interface implemented by all SQL definition classes:
 * StructuedSQL, FreeformSQL, and ProcedureSQL.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface SQLDefinition extends Serializable, Cloneable {
   /**
    * Get the selection column list.
    */
   XSelection getSelection();

   /**
    * Get a variable value for a name. If the variable is not defined,
    * it returns null.
    * @param name variable name.
    * @return variable definition.
    */
   UserVariable getVariable(String name);

   /**
    * Set the value of a variable.
    */
   void setVariable(String name, XValueNode value);

   /**
    * Select columns from the table.
    */
   XNode select(XNode root) throws Exception;

   /**
    * Parse the XML element that contains information on this sql.
    */
   void parseXML(Element node) throws Exception;

   /**
    * Generate the XML segment to represent this data source.
    */
   void writeXML(PrintWriter writer);

   /**
    * Get the data source.
    */
   JDBCDataSource getDataSource();

   /**
    * Set the data source.
    */
   void setDataSource(JDBCDataSource ds);

   /**
    * Convert to a SQL statement.
    */
   String getSQLString();

   /**
    * Make a copy of the sql.
    */
   Object clone();
}

