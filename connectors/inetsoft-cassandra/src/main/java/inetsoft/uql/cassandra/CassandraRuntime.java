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

public class CassandraRuntime extends TabularRuntime implements TabularCatalogProvider {
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      CassandraQuery query0 = (CassandraQuery) query;
      CassandraDataSource ds = (CassandraDataSource) query.getDataSource();
      String qstr = query0.getQueryString().trim();
      CqlSession session = null;

      try {
         // Bug #79589: the connector is read-only; refuse before any session is opened
         requireReadOnlyQuery(qstr);
         session = openSession(ds);
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

   /**
    * Opens the session used to run a user query. Seam so tests can prove a rejected statement
    * never reaches a session.
    */
   CqlSession openSession(CassandraDataSource ds) throws Exception {
      return getSession(ds);
   }

   /**
    * Bug #79589: only a single SELECT statement is allowed. Leading whitespace and comments
    * (--, //, block) and keyword case are tolerated; a trailing semicolon is tolerated; any other
    * statement, or anything after the first statement terminator, is rejected. Applied to the
    * final query text (after variable substitution).
    *
    * @throws IllegalArgumentException if the text is not a single SELECT statement.
    */
   static void requireReadOnlyQuery(String cql) {
      String msg = "Only a single SELECT statement is allowed; the Cassandra connector is read-only.";

      if(cql == null) {
         throw new IllegalArgumentException(msg);
      }

      int n = cql.length();
      int i = 0;
      boolean selectSeen = false;
      boolean terminated = false;

      while(i < n) {
         char c = cql.charAt(i);
         char c2 = i + 1 < n ? cql.charAt(i + 1) : '\0';

         if(Character.isWhitespace(c)) {
            i++;
         }
         else if((c == '-' && c2 == '-') || (c == '/' && c2 == '/')) {
            while(i < n && cql.charAt(i) != '\n' && cql.charAt(i) != '\r') {
               i++;
            }
         }
         else if(c == '/' && c2 == '*') {
            int end = cql.indexOf("*/", i + 2);

            if(end < 0) {
               throw new IllegalArgumentException(msg);
            }

            i = end + 2;
         }
         else if(terminated) {
            // content after the statement terminator
            throw new IllegalArgumentException(msg);
         }
         else if(!selectSeen) {
            if(!cql.regionMatches(true, i, "select", 0, 6) ||
               (i + 6 < n && (Character.isLetterOrDigit(cql.charAt(i + 6)) ||
                              cql.charAt(i + 6) == '_')))
            {
               throw new IllegalArgumentException(msg);
            }

            selectSeen = true;
            i += 6;
         }
         else if(c == ';') {
            terminated = true;
            i++;
         }
         else if(c == '\'' || c == '"') {
            // string literal / quoted identifier; a doubled quote is an escaped quote
            i++;

            while(true) {
               if(i >= n) {
                  throw new IllegalArgumentException(msg);
               }

               if(cql.charAt(i) == c) {
                  if(i + 1 < n && cql.charAt(i + 1) == c) {
                     i += 2;
                     continue;
                  }

                  i++;
                  break;
               }

               i++;
            }
         }
         else if(c == '$' && c2 == '$') {
            int end = cql.indexOf("$$", i + 2);

            if(end < 0) {
               throw new IllegalArgumentException(msg);
            }

            i = end + 2;
         }
         else {
            i++;
         }
      }

      if(!selectSeen) {
         throw new IllegalArgumentException(msg);
      }
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

   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      CassandraDataSource ds = (CassandraDataSource) dataSource;
      requireKeyspace(ds);

      // A4: no catch around session establishment. A connection or permission failure here must
      // propagate as an exception, not be absorbed into an empty catalog — do not add one.
      try(CqlSession session = getSession(ds)) {
         return CassandraCatalog.listDatasets(session);
      }
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      CassandraDataSource ds = (CassandraDataSource) dataSource;
      requireKeyspace(ds);

      // A4: no catch around session establishment. A connection or permission failure here must
      // propagate as an exception, not be absorbed into an empty schema — do not add one.
      try(CqlSession session = getSession(ds)) {
         return CassandraCatalog.describeDataset(session, datasetId);
      }
   }

   /**
    * A4: an unconfigured keyspace must throw, not silently enumerate nothing (and must not be
    * confused with "the keyspace exists but has no tables", which is a different, legal, empty
    * result). Checked before {@link #getSession} is even called, so a blank/null keyspace never
    * reaches the driver at all.
    */
   private static void requireKeyspace(CassandraDataSource ds) throws Exception {
      if(ds.getKeyspace() == null || ds.getKeyspace().isBlank()) {
         throw new Exception("Data source '" + ds.getName() +
            "' has no keyspace configured; cannot enumerate its tables.");
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(CassandraRuntime.class.getName());
}
