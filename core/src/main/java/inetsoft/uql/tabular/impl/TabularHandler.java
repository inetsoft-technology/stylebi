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
package inetsoft.uql.tabular.impl;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.uql.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.XHandler;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.*;
import inetsoft.util.DataCacheVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

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
         (TabularDataSource<?>) XUtil.getDatasource(user, query.getDataSource()).clone();
      query.setDataSource(xds);
      TabularUtil.replaceVariables(xds, params);
      TabularUtil.replaceVariables(query, params);

      if(isQueryCached(query, params, user, visitor)) {
         return new XNode();
      }

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
   private TabularRuntime getRuntime(String type) {
      TabularRuntime runtime = runtimes.get(type);

      if(runtime == null) {
         try {
            runtime = (TabularRuntime) Config.getClass(type, Config.getRuntime(type))
               .getDeclaredConstructor().newInstance();
         }
         catch(Exception ex) {
            LOG.error("Failed to create tabular runtime: " +
                        type + " " + Config.getRuntime(type), ex);
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
