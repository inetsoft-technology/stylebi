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
package inetsoft.uql.util;

import inetsoft.uql.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.schema.XTypeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * XAgent defines the common interface for providing various optional
 * functions for datasource and query. Each datasource type can choose to
 * supply an agent to support the operations defined in the agent. If
 * a datasource does not provide an agent, all features dependent on the
 * agent operations will be disabled.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class XAgent implements java.io.Serializable {
   /**
    * Return an agent for a datasource type. If an agent is not defined for
    * a datasource, a default agent is returned. The default agent implements
    * all operations as no-op.
    * @param dtype datasource type.
    * @return datasource agent.
    */
   public static XAgent getAgent(String dtype) {
      XAgent agent = (XAgent) agentmap.get(dtype);

      if(agent == null) {
         String cls = Config.getAgentClass(dtype);

         if(cls != null) {
            try {
               agent = (XAgent) Config.getClass(dtype, cls).newInstance();
               agentmap.put(dtype, agent);
            }
            catch(Exception ex) {
               LOG.error("Failed to create an agent: " + cls, ex);
            }
         }
      }

      if(agent == null) {
         agent = new XAgent() {};
         agentmap.put(dtype, agent);
      }

      return agent;
   }

   /**
    * Get an agent for the datasource of this query.
    */
   public static XAgent getAgent(XQuery query) {
      return getAgent(query.getDataSource());
   }

   /**
    * Get an agent for the datasource.
    */
   public static XAgent getAgent(XDataSource xds) {
      return getAgent(xds.getType());
   }

   /**
    * Get the fully qualified name of a meta data node.
    */
   public String getQualifiedName(XNode node, XDataSource xds) {
      return node.getName();
   }

   /**
    * Get the user name for the corresponding datasource
    */
   public String getUser(XDataSource xds) {
      return "";
   }

   /**
    * Get the table columns description.
    * @param table the table name.
    * @param xds datasource object.
    * @param session xengine session.
    * @return column nodes.
    */
   public XTypeNode[] getColumns(String table, XDataSource xds,
                                 Object session) throws Exception {
      return new XTypeNode[] {};
   }

   /**
    * Get the table columns description.
    * @param table the table name.
    * @param query query object.
    * @param session xengine session.
    * @return column nodes.
    */
   public XTypeNode[] getColumns(String table, XQuery query,
                                 Object session) throws Exception {
      return new XTypeNode[] {};
   }

   /**
    * Get a list of all tables that can be merged into this query safely.
    * @param query query object.
    * @return table alias and table names.
    */
   public Map getRelatedTables(XQuery query) {
      return new HashMap();
   }

   /**
    * Get a list of all tables of this query.
    * @param query the specified query object.
    * @return list of table names.
    */
   public Object[] getTables(XQuery query) {
      return new Object[] {};
   }

   /**
    * Get column data from database.
    *
    * @param model data model.
    * @param lname the logic model name.
    * @param ename the entity name.
    * @param aname the attribute name.
    * @param vars the variable table.
    * @param session xengine session.
    * @return null if retriving column data failed.
    */
   public XNode getModelData(XDataModel model, String lname, String ename,
                             String aname, VariableTable vars, Object session,
                             Principal user)
         throws Exception
   {
      return null;
   }

   /**
    * Get column data from database.
    * @param query query object.
    * @param column column name.
    * @param session xengine session.
    * @return null if retriving column data failed.
    */
   public XNode getQueryData(XQuery query, String column,
      VariableTable vars, Object session, Principal user) throws Exception
   {
      return null;
   }

   /**
    * Get column data from database.
    * @param query query object.
    * @param table table name.
    * @param column column name.
    * @param session xengine session.
    * @return null if retriving column data failed.
    */
   public XNode getQueryData(XQuery query, String table, String column,
      VariableTable vars, Object session, Principal user) throws Exception
   {
      return null;
   }

   /**
    * Check if the data source connection is reusable.
    * @return true if is reusable, false otherwise.
    */
   public boolean isConnectionReusable() {
      return false;
   }

   private static Hashtable agentmap = new Hashtable();

   private static final Logger LOG =
      LoggerFactory.getLogger(XAgent.class);
}

