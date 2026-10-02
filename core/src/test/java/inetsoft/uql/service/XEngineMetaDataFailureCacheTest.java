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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
      // when > 0, getMetaDataInternal blocks for this long before returning, so a test can
      // force a second concurrent caller to observe the pendingFlag in-flight-request state
      // instead of racing to fetch independently.
      volatile long delayMs = 0;

      TestEngine(Cluster cluster) {
         super(cluster, null, mock(DataSourceRegistry.class), mock(ConnectionPoolFactory.class));
      }

      @Override
      protected XNode getMetaDataInternal(Object session, XDataSource dx, XNode mtype)
         throws Exception
      {
         invocationCount.incrementAndGet();

         if(delayMs > 0) {
            Thread.sleep(delayMs);
         }

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

   /**
    * The fix dedupes concurrent *retries* (after a cached failure goes stale) using the same
    * pendingFlag in-flight-request mechanism that already deduped concurrent *first* attempts --
    * it doesn't add a new concurrency primitive. That only works because the staleness check
    * (reading the failure timestamp) and marking the key pending both happen inside the same
    * {@code synchronized(lock)} block in {@code getMetaData}, so two threads can't both decide
    * the cached failure is stale and both go fetch. This test forces that race: two threads
    * call getMetaData for the same already-stale key at (as close to) the same time, with the
    * retry fetch deliberately slowed down so the second thread's call falls inside the first
    * thread's fetch window instead of running before or after it. Only one retry should reach
    * getMetaDataInternal; the other should block and then receive the same (successful) result.
    */
   @Test
   void concurrentRequestsDedupeRetryAfterTtlExpires(@TempDir Path tempDir) throws Exception {
      SreeEnv.setProperty("inetsoft.metadata.dir", tempDir.toString());

      TestEngine engine = new TestEngine(cluster);
      XDataSource dx = mock(XDataSource.class);
      when(dx.getFullName()).thenReturn("Bug77542ConcurrentTestDataSource");
      when(dx.isFromPortal()).thenReturn(true);

      XNode mtype = new XNode("TABLETYPES");
      mtype.setAttribute("type", "TABLETYPES");

      // Prime a stale cached failure, same as the single-threaded test.
      engine.getMetaData(null, dx, mtype, false);
      assertEquals(1, engine.invocationCount.get());

      Field timesField = XEngine.class.getDeclaredField("metaDataFailureTimes");
      timesField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Long> failureTimes = (Map<String, Long>) timesField.get(engine);
      String key = failureTimes.keySet().iterator().next();
      failureTimes.put(key, System.currentTimeMillis() - 10_000_000L);

      // The database has "recovered", but the retry fetch is slow -- long enough that a
      // second caller starting shortly after the first is guaranteed to observe the
      // pendingFlag spin-wait rather than also deciding the key is stale and racing to fetch.
      engine.fail = false;
      engine.delayMs = 500;

      CountDownLatch bothStarted = new CountDownLatch(2);
      XNode[] results = new XNode[2];
      Runnable call = () -> {
         bothStarted.countDown();
         try {
            bothStarted.await(5, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
      };

      Thread t1 = new Thread(() -> {
         call.run();
         try {
            results[0] = engine.getMetaData(null, dx, mtype, false);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
      Thread t2 = new Thread(() -> {
         call.run();
         // give the first thread a small head start so it's the one that observes the
         // stale entry and marks the key pending, not both threads racing on entry.
         try {
            Thread.sleep(50);
         }
         catch(InterruptedException ignore) {
         }
         try {
            results[1] = engine.getMetaData(null, dx, mtype, false);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });

      t1.start();
      t2.start();
      t1.join(10_000);
      t2.join(10_000);

      assertEquals(2, engine.invocationCount.get(),
         "exactly one retry (total of 2 calls: the original failure + one retry) should " +
         "reach getMetaDataInternal, even with two concurrent callers");
      assertNotNull(results[0]);
      assertNotNull(results[1]);
      assertEquals(1, results[0].getChildCount());
      assertEquals(1, results[1].getChildCount());
   }
}
