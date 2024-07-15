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
package inetsoft.uql.rest;

import inetsoft.uql.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.QueryManager;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractRestRuntime extends TabularRuntime {
   protected abstract QueryRunner getQueryRunner(AbstractRestQuery query) throws Exception;

   @Override
   public XTableNode runQuery(TabularQuery tabularQuery, VariableTable params) {
      final AbstractRestQuery query = (AbstractRestQuery) tabularQuery;
      QueryManager manager = (QueryManager) query.getProperty("queryManager");
      ExecutorService executor = Executors.newSingleThreadExecutor(GroupedThread::new);
      boolean cancelled = false;
      XTableNode result = null;

      try {
         final QueryRunner queryRunner = getQueryRunner(query);

         if(manager != null) {
            manager.addPending(queryRunner);
         }

         TimedQueue.TimedRunnable timeoutRunnable = null;
         final boolean[] timedOut = { false };
         final List<UserMessage> userMsgs = new ArrayList<>();

         try {
            Future<XTableNode> future = executor.submit(() -> {
               if(queryRunner instanceof AbstractQueryRunner) {
                  AbstractQueryRunner<?> queryRunner2 = (AbstractQueryRunner<?>) queryRunner;
                  queryRunner2.setExecutionThread(Thread.currentThread());
                  queryRunner2.setLiveMode("true".equals(params.get(XQuery.HINT_PREVIEW)));
               }

               try {
                  return queryRunner.run();
               }
               finally {
                  userMsgs.add(Tool.getUserMessage());
               }
            });

            int timeout = query.getTimeout();

            if(timeout > 0) {
               // increase timeout for lookup queries
               if(query instanceof EndpointJsonQuery) {
                  timeout *= ((EndpointJsonQuery<?>) query).getLookupQueryDepth() * 2 + 1;
               }
               else if(query instanceof RestJsonQuery) {
                  timeout *= ((RestJsonQuery) query).getLookupCount() * 2 + 1;
               }

               TimedQueue.add(timeoutRunnable = new TimedQueue.TimedRunnable(timeout * 1000L) {
                  @Override
                  public void run() {
                     if(queryRunner instanceof AbstractQueryRunner) {
                        ((AbstractQueryRunner<?>) queryRunner).cancel();
                        timedOut[0] = true;
                     }
                  }
               });
            }

            result = future.get();
         }
         finally {
            if(timeoutRunnable != null) {
               TimedQueue.remove(timeoutRunnable);

               if(timedOut[0]) {
                  LOG.error("Query timed out. Failed to load data for " + query.getName());
                  Tool.addUserMessage(Catalog.getCatalog().getString("common.timeout",
                                                                     query.getName()),
                                      ConfirmException.ERROR);
               }
            }

            cancelled = QueryManager.isCancelled(queryRunner);

            if(manager != null) {
               manager.removePending(queryRunner);
            }

            if(queryRunner instanceof AbstractQueryRunner) {
               ((AbstractQueryRunner<?>) queryRunner).setExecutionThread(null);
            }

            userMsgs.stream().filter(m -> m != null).forEach(m -> Tool.addUserMessage(m));
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to run query.", ex);
         result = null;
      }

      if(cancelled) {
         throw new CancelledException();
      }

      return result;
   }

   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params) throws Exception {
      final AbstractRestDataSource restDS = (AbstractRestDataSource) ds;
      final String url = restDS.getURL();

      if(url == null || url.isEmpty()) {
         throw new IllegalStateException("REST datasource URL must not be empty.");
      }

      if(isHttpUrl(url)) {
         final ConnectionTester connectionTester = new ConnectionTester();

         if(!connectionTester.isReachable(url)) {
            throw new IOException("Failed to connect to host at " + url);
         }
      }
      else {
         InputStream input = new URL(url).openStream();

         try {
            input.close();
         }
         catch(Exception ex) {
            LOG.debug("Error closing test connection: " + url, ex);
         }
      }
   }

   protected AbstractRestDataSource getDataSource(AbstractRestQuery query) {
      return (AbstractRestDataSource) query.getDataSource();
   }

   public static boolean isHttpUrl(String url) {
      return url.startsWith("http");
   }

   private static final Logger LOG = LoggerFactory.getLogger(AbstractRestRuntime.class.getName());
}
