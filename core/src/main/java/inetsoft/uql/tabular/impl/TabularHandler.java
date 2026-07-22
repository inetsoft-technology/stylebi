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
package inetsoft.uql.tabular.impl;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.uql.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.service.XHandler;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.*;
import inetsoft.util.DataCacheVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.security.Principal;
import java.util.*;

/**
 * This class implements the runtime of tabular services.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TabularHandler extends XHandler {
   public TabularHandler() {
      // no-op
   }

   /**
    * Execute the query.
    * @param params parameters for query.
    * @return the result as a hierarchical tree.
    */
   @Override
   public XNode execute(XQuery query, VariableTable params,
                        Principal user, DataCacheVisitor visitor)
      throws Exception
   {
      TabularRuntime runtime = getRuntime(query.getType());
      TabularQuery oquery = (TabularQuery) query;
      query = (TabularQuery) query.clone();
      TabularDataSource<?> xds =
         (TabularDataSource<?>) ConnectionProcessor.getInstance().getDatasource(user, query.getDataSource()).clone();
      query.setDataSource(xds);

      TabularUtil.fillNullVariablesWithEmptyString(runtime, query, params);

      // Check the cache before substituting variables so that getQueryKey sees
      // the unsubstituted data source (variable names still visible) and
      // produces the same key as the cache-clear paths (Bug #75737).
      if(isQueryCached(query, params, user, visitor)) {
         return new XNode();
      }

      TabularUtil.replaceVariables(xds, params);
      TabularUtil.replaceVariables(query, params);

      TabularExecutor executor = Drivers.getInstance().getTabularExecutor(xds);
      XTableNode tbl = null;

      // support custom execution
      if(executor != null) {
         tbl = executor.execute(xds, (TabularQuery) query, runtime, params);
      }

      if(tbl == null) {
         tbl = runtime.runQuery((TabularQuery) query, params);
      }

      if(tbl != null && tbl.getColCount() > 0) {
         int max = TabularUtil.getMaxRows(query, params);

         if(max > 0 && tbl instanceof XTableTableNode) {
            XTable xtable = ((XTableTableNode) tbl).getXTable();

            if(xtable instanceof TableLens) {
               tbl = new XTableTableNode(PostProcessor.maxrows((TableLens) xtable, max));
            }
         }

         XTypeNode[] cols = new XTypeNode[tbl.getColCount()];

         for(int i = 0; i < cols.length; i++) {
            cols[i] = XSchema.createPrimitiveType(tbl.getName(i), tbl.getType(i));
         }

         oquery.setOutputColumns(cols);
      }
      else {
         XTypeNode queryTypeNode = query.getOutputType(null, false);
         XNodeMetaTable xNodeMetaTable = new XNodeMetaTable(false, queryTypeNode, false, true);
         return new XTableTableNode(xNodeMetaTable);
      }

      return tbl;
   }

   /**
    * Get a query key for query caching. In addition to the base key, the
    * resolved values of the variables embedded in the tabular data source
    * fields (e.g. "Query HTTP Parameters", URL, or authentication parameters)
    * are included so that a change in any of those values produces a distinct
    * cache key. Without this, a stale cached result would be returned after the
    * variable value changes (Bug #75737).
    *
    * Only the resolved variable values are used -- not a serialization of the
    * whole data source -- so that encrypted credential/secret fields (whose
    * ciphertext changes on every call because a fresh random IV is generated)
    * do not make the key non-deterministic and defeat caching/eviction for
    * authenticated data sources. This relies on getQueryKey being called with
    * the unsubstituted data source: execute() checks the cache before
    * substituting, and the cache-clear paths (removeQueryCache/clearQueryCache)
    * also pass the unsubstituted data source, so the variable names are visible
    * and the write, read, and remove keys stay identical.
    */
   @Override
   public String getQueryKey(XQuery query, VariableTable qvars, Principal user) throws Exception {
      String key = super.getQueryKey(query, qvars, user);
      XDataSource source = query.getDataSource();

      if(source instanceof TabularDataSource) {
         TreeMap<String, String> values = new TreeMap<>();

         // exclude secret/password fields so credential values are never
         // embedded in the (in-memory, cluster-replicated) cache key
         for(UserVariable var : TabularUtil.findVariables(source, true)) {
            String name = var == null ? null : var.getName();

            if(name != null && !values.containsKey(name)) {
               Object value = qvars == null ? null : qvars.get(name);
               values.put(name, variableValueToKey(value));
            }
         }

         if(!values.isEmpty()) {
            key = key + "__" + values;
         }
      }

      return key;
   }

   /**
    * Produce a deterministic, content-based string for a variable value used in
    * the cache key. Missing values are normalized to "" so the key matches
    * whether or not fillNullVariablesWithEmptyString has been applied. Array
    * values are joined by content (mirroring XUtil.replaceVariable) so that two
    * arrays with equal elements produce the same key rather than relying on
    * Object.toString()'s identity hash.
    */
   private static String variableValueToKey(Object value) {
      if(value == null) {
         return "";
      }

      if(value.getClass().isArray()) {
         StringBuilder buf = new StringBuilder();
         int len = Array.getLength(value);

         for(int i = 0; i < len; i++) {
            if(i > 0) {
               buf.append(',');
            }

            buf.append(Array.get(value, i));
         }

         return buf.toString();
      }

      return value.toString();
   }

   /**
    * Connect to the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    */
   @Override
   public void connect(XDataSource datasource, VariableTable params) {
      dataSource = (TabularDataSource<?>) datasource;
      connectParameters = params;
   }

   /**
    * Test the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    */
   @Override
   public void testDataSource(XDataSource datasource, VariableTable params) throws Exception {
      datasource = (TabularDataSource<?>) datasource.clone();
      TabularUtil.replaceVariables(datasource, params);

      TabularRuntime runtime = getRuntime(datasource.getType());
      runtime.testDataSource((TabularDataSource<?>) datasource, params);
   }

   /**
    * Build the meta data of this data source as a XNode tree. This
    * method will rebuild the meta data tree every time it's called.
    * The meta data should be cached by the caller.
    * @param mtype meta data type, defined in each data source.
    * @return return the root node of the meta data tree.
    */
   @Override
   public XNode getMetaData(XNode mtype) throws Exception {
      if(dataSource == null) {
         return null;
      }

      TabularDataSource<?> ds = (TabularDataSource<?>) dataSource.clone();
      TabularUtil.replaceVariables(ds, connectParameters);
      return getRuntime(ds.getType()).getMetaData(mtype, ds, connectParameters);
   }

   /**
    * Close the data source connection.
    */
   @Override
   public void close() {
      // no-op
   }

   /**
    * Get the runtime for the specified type.
    */
   public TabularRuntime getRuntime(String type) {
      TabularRuntime runtime = runtimes.get(type);

      if(runtime == null) {
         try {
            runtime = (TabularRuntime) Config.getConfig().getClass(type, Config.getConfig().getRuntime(type))
               .getDeclaredConstructor().newInstance();
         }
         catch(Exception ex) {
            LOG.error("Failed to create tabular runtime: " +
                        type + " " + Config.getConfig().getRuntime(type), ex);
         }

         runtimes.put(type, runtime);
      }

      return runtime;
   }

   private TabularDataSource<?> dataSource;
   private VariableTable connectParameters;
   private final Map<String, TabularRuntime> runtimes = new HashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(TabularHandler.class);
}
