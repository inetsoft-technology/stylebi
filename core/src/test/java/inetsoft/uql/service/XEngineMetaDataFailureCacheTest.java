/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XNode;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77542: a metadata-retrieval failure (e.g. the data source's database was temporarily
 * unreachable) used to be cached in {@link XEngine#metaDataCache} as a permanent empty
 * {@link XNode}, with nothing re-attempting or expiring it short of the explicit
 * "refresh metadata" action or a server restart. This test exercises the fix: the cached
 * failure is only trusted for {@code META_DATA_FAILURE_RETRY_INTERVAL}, after which the next
 * request retries the data source instead of staying stuck on the empty result forever.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XEngineMetaDataFailureCacheTest {
   @Autowired
   private Cluster cluster;
   private String savedMetadataDir;

   @BeforeEach
   void saveMetadataDir() {
      savedMetadataDir = SreeEnv.getProperty("inetsoft.metadata.dir");
   }

   @AfterEach
   void restoreMetadataDir() {
      if(savedMetadataDir == null) {
         SreeEnv.remove("inetsoft.metadata.dir");
      }
      else {
         SreeEnv.setProperty("inetsoft.metadata.dir", savedMetadataDir);
      }
   }

   /**
    * Subclass that lets the test control whether a given call to the metadata-retrieval
    * extension point fails or succeeds, without needing a real JDBC data source/handler.
    */
   private static class TestEngine extends XEngine {
      final AtomicInteger invocationCount = new AtomicInteger();
      volatile boolean fail = true;

      TestEngine(Cluster cluster) {
         super(cluster, null, mock(DataSourceRegistry.class), mock(ConnectionPoolFactory.class));
      }

      @Override
      protected XNode getMetaDataInternal(Object session, XDataSource dx, XNode mtype)
         throws Exception
      {
         invocationCount.incrementAndGet();

         if(fail) {
            throw new java.sql.SQLException("simulated database outage");
         }

         XNode result = new XNode("TABLETYPES");
         result.addChild(new XNode("TABLE"));
         return result;
      }
   }

   @Test
   void retriesAfterFailureCacheExpires(@TempDir Path tempDir) throws Exception {
      // isolate the on-disk metadata cache so this test neither reads a leftover file from
      // a previous run nor leaves one behind for the next
      SreeEnv.setProperty("inetsoft.metadata.dir", tempDir.toString());

      TestEngine engine = new TestEngine(cluster);
      XDataSource dx = mock(XDataSource.class);
      when(dx.getFullName()).thenReturn("Bug77542TestDataSource");
      when(dx.isFromPortal()).thenReturn(true);

      XNode mtype = new XNode("TABLETYPES");
      mtype.setAttribute("type", "TABLETYPES");

      // 1st call: the simulated outage fails, but the failure is swallowed -- the caller
      // gets an empty node back, not an exception, matching "no error in the UI".
      XNode result1 = engine.getMetaData(null, dx, mtype, false);
      assertEquals(0, result1.getChildCount());
      assertEquals(1, engine.invocationCount.get());

      // 2nd call, immediately after: still within the failure-cache window, so the cached
      // empty node is reused and the data source is not retried yet.
      XNode result2 = engine.getMetaData(null, dx, mtype, false);
      assertEquals(0, result2.getChildCount());
      assertEquals(1, engine.invocationCount.get(), "should not retry before the TTL expires");

      // Simulate the TTL having elapsed (without sleeping META_DATA_FAILURE_RETRY_INTERVAL
      // in real time) by back-dating the recorded failure timestamp.
      Field timesField = XEngine.class.getDeclaredField("metaDataFailureTimes");
      timesField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Long> failureTimes = (Map<String, Long>) timesField.get(engine);
      assertEquals(1, failureTimes.size());
      String key = failureTimes.keySet().iterator().next();
      failureTimes.put(key, System.currentTimeMillis() - 10_000_000L);

      // The database has "recovered" for the next attempt.
      engine.fail = false;

      // 3rd call: the failure entry is now stale, so it's treated as a miss and the data
      // source is retried -- and this time it succeeds.
      XNode result3 = engine.getMetaData(null, dx, mtype, false);
      assertEquals(1, result3.getChildCount());
      assertEquals(2, engine.invocationCount.get(), "should retry once the TTL has expired");

      // 4th call: the real result is now cached (and the failure marker cleared), so the
      // data source is not re-queried again.
      XNode result4 = engine.getMetaData(null, dx, mtype, false);
      assertEquals(1, result4.getChildCount());
      assertEquals(2, engine.invocationCount.get(), "a successful result should be cached too");
   }
}
