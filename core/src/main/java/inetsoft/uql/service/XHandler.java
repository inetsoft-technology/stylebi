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
package inetsoft.uql.service;

import inetsoft.mv.MVSession;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.uql.*;
import inetsoft.uql.schema.XVariable;
import inetsoft.uql.util.*;
import inetsoft.util.DataCacheVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * XHandler defines the API for query handlers. A handler is responsible
 * for a particular protocol. All interfaces to a data source, including
 * connection, meta data retrieval, and query execution are processed
 * through a handler.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class XHandler implements java.io.Serializable {
   /**
    * Execute the query.
    * @param params parameters for query.
    * @return the result as a hierarchical tree.
    */
   public abstract XNode execute(XQuery query, VariableTable params,
                                 Principal user, DataCacheVisitor visitor)
      throws Exception;

   /**
    * Connect to the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    */
   public abstract void connect(XDataSource datasource, VariableTable params)
      throws Exception;

   /**
    * Test the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    * @throws Exception
    */
   public abstract void testDataSource(XDataSource datasource,
      VariableTable params) throws Exception;

   /**
    * Build the meta data of this data source as a XNode tree. This
    * method will rebuild the meta data tree everytime it's called.
    * The meta data should be cached by the caller.
    * @param mtype meta data type, defined in each data source.
    * @return return the root node of the meta data tree.
    */
   public abstract XNode getMetaData(XNode mtype) throws Exception;

   /**
    * Close the data source connection.
    */
   public abstract void close() throws Exception;

   /**
    * Set the session associated with this handler.
    */
   public void setSession(Object session) {
      this.session = session;
   }

   /**
    * Set the repository associated with this handler.
    */
   public void setRepository(XRepository repository) {
      this.repository = repository;
   }

   /**
    * Clear the internal cached states.
    */
   public void reset() {
      qparamMap.clear();
   }

   /**
    * Clear the internal cached states of a query.
    *
    * @param query the specified query
    */
   public void reset(XQuery query) {
      qparamMap.remove(getQueryKey(query));
   }

   /**
    * Apply max rows to the resultset.
    */
   public XNode postProcess(XNode root, XQuery query, VariableTable params)
      throws Exception {
      int max = query.getMaxRows();

      if(params != null && params.get(XQuery.HINT_MAX_ROWS) != null) {
         max = Integer.parseInt(params.get(XQuery.HINT_MAX_ROWS).toString());
      }

      if(max == 0) {
         return root;
      }

      if(root instanceof XSequenceNode) {
         int amax = 0;

         while(root.getChildCount() > max) {
            root.removeChild(root.getChildCount() - 1);
            amax = max;
         }

         if(amax > 0) {
            ((XSequenceNode) root).setAppliedMaxRows(amax);
         }
      }
      else if(root instanceof XTableNode) {
         root = new XTableNodeWrapper((XTableNode) root);
         ((XTableNodeWrapper) root).setMaxRows(max);
      }

      return root;
   }

   /**
    * Get the variable table to serve as base table. This contains all
    * query variables, whose values are calculated at runtime.
    */
   protected void prepareVariableTable(XQuery xquery, VariableTable vars) {
      String qkey = getQueryKey(xquery);
      VariableTable vt = (VariableTable) qparamMap.get(qkey);

      if(vt == null) {
         qparamMap.put(qkey, vt = new CachedVariableTable());
         // find all un-set variables in the query variables
         Enumeration keys = xquery.getVariableNames();

         while(keys.hasMoreElements()) {
            String name = (String) keys.nextElement();
            XVariable var = xquery.getVariable(name);
            vt.put(name, var);
         }
      }

      // @by larryl, remove old chain to avoid stack overflow
      vars.removeBaseTable(CachedVariableTable.class);
      vars.addBaseTable(vt);
      vars.setSession(session);
   }

   protected boolean isQueryCached(XQuery query, VariableTable vars, Principal user,
                                   DataCacheVisitor visitor) throws Exception
   {
      if(visitor == null) {
         return false;
      }

      if(AssetDataCache.isDebugData()) {
         return false;
      }

      final String key = getQueryKey(query, vars, user);
      return visitor.visitCache(key);
   }

   public void removeQueryCache(XQuery query, VariableTable vars, Principal user, Class<?> type) throws Exception {
      final String key = getQueryKey(query, vars, user) + type;
      XSessionManager.getSessionManager().removeCacheData(key);
   }

   /**
    * Get a query key for query caching.
    */
   public String getQueryKey(XQuery query, VariableTable qvars, Principal user) throws Exception {
      // @by jasons, bug1249326025439, if the _USER_ or _ROLES_ variables are
      // applied by a VPM, the values for these variables must be added to the
      // variable table so that the query keys are distinct for different users
      // or roles. If not, the VPM conditions may not be applied and the
      // incorrect data may be returned
      VariableTable kvars = new VariableTable();

      // copy only the parameters used in query to kvars so changes in parameters unrelated
      // to the query won't cause the cache to be missed. (60409)
      for(Enumeration e = query.getVariableNames(); e.hasMoreElements();) {
         String vname = (String) e.nextElement();

         if("_USER_".equals(vname) && !kvars.contains(vname)) {
            kvars.put(vname, user.getName());
         }
         else if("_ROLES_".equals(vname) && !kvars.contains(vname)) {
            kvars.put(vname, XUtil.getUserRoles(user));
         }
         else if("_GROUPS_".equals(vname) && !kvars.contains(vname)) {
            kvars.put(vname, XUtil.getUserGroups(user));
         }
         else {
            kvars.put(vname, qvars.get(vname));
         }
      }

      // we always use the cache since even if caching is turned off, we
      // still cache the data within one report (one XSession.execute)
      // ds + "__" + "query xml" + "__" + "qvars" + "__" + "queryMaxRow"
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      XDataSource source = query.getDataSource();
      writer.print(source == null ? "null" : source.getFullName());
      writer.print("__");
      query.writeXML(writer);
      writer.print("__");
      kvars.printKey(writer);

      if(user != null && Identity.UNKNOWN_USER.equals(user.getName())) {
         writer.print("__");
         writer.print(user.toString());
      }

      if(query.dependsOnMVSession()) {
         final MVSession mvSession = MVSession.getCurrentSession();
         writer.print(mvSession == null ? "" : mvSession.getSessionDiscriminator());
      }

      writer.flush();
      return buffer.toString();
   }

   /**
    * Get the key for qparamMap.
    */
   private static String getQueryKey(XQuery query) {
      String name = query.getName();
      XDataSource dx = query.getDataSource();
      String folder = query.getFolder();

      return name + "_" + folder + "_" + ((dx == null) ? "" : dx.getFullName());
   }

   // marker class for clean up
   private static class CachedVariableTable extends VariableTable {
   }

   protected Object session = null;
   protected XRepository repository = null;
   private Hashtable qparamMap = new Hashtable(); // XQuery -> VariableTable
}
