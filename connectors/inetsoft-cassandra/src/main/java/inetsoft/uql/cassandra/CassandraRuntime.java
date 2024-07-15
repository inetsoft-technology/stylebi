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
package inetsoft.uql.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;

public class CassandraRuntime extends TabularRuntime {
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      CassandraQuery query0 = (CassandraQuery) query;
      CassandraDataSource ds = (CassandraDataSource) query.getDataSource();
      String qstr = query0.getQueryString().trim();
      CqlSession session = null;

      try {
         session = getSession(ds);
         SimpleStatement statement = SimpleStatement.newInstance(qstr);
         ResultSet result = session.execute(statement);
         int maxrows = TabularUtil.getMaxRows(query, params);
         return new CassandraTable(result, session, maxrows);
      }
      catch(Exception ex) {
         LOG.warn("Failed to execute Cassandra query: " + qstr, ex);
         Tool.addUserMessage("Failed to execute Cassandra query: " + qstr +
                                " (" + ex.getMessage() + ")");

         if(session != null) {
            try {
               session.close();
            }
            catch(Exception ex2) {
               LOG.warn("Failed to close connection: " + ds.getName(), ex2);
            }
         }
         handleError(params, ex, () -> null);
      }

      return null;
   }

   public void testDataSource(TabularDataSource ds0, VariableTable params) throws Exception {
      CassandraDataSource ds = (CassandraDataSource) ds0;
      CqlSession session = getSession(ds);
      session.close();
   }

   static CqlSessionBuilder getCluster(CassandraDataSource ds) throws Exception {
      InetSocketAddress addr = new InetSocketAddress(ds.getHost(), ds.getPort());
      CqlSessionBuilder builder = CqlSession.builder()
         .addContactPoint(addr)
         .withLocalDatacenter(ds.getDatacenter());

      if(ds.getUser() != null && ds.getPassword() != null) {
         builder = builder.withAuthCredentials(ds.getUser(), ds.getPassword());
      }

      if(ds.isSSL()) {
         builder = builder.withSslContext(SSLContext.getDefault());
      }

      return builder;
   }

   public static CqlSession getSession(CassandraDataSource ds) throws Exception {
      return getCluster(ds)
         .withKeyspace(ds.getKeyspace())
         .build();
   }

   private static final Logger LOG = LoggerFactory.getLogger(CassandraRuntime.class.getName());
}
