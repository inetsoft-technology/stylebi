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

package inetsoft.uql.rest.json;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.datasource.clockify.ClockifyDataSource;
import inetsoft.uql.rest.datasource.sfreports.SalesforceReportsDataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The quota of a data source (requests per second and simultaneous connections), which every
 * request of an endpoint query passes through, shared by all queries of the same data source. The
 * quotas live in a JVM-wide cache keyed by the name of the data source, so each test uses names of
 * its own.
 */
@EndpointsSpringContext
@SreeHome
class RestQuotaManagerTest {
   @Test
   void aDataSourceThatIsNotAnEndpointOneIsNotLimited() throws Exception {
      RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setName("quota-custom");
      long start = System.nanoTime();

      for(int i = 0; i < 30; i++) {
         RestQuotaManager.withQuota(dataSource, () -> "ok");
      }

      assertTrue(millisSince(start) < 1000, "a custom REST data source was slowed down");
   }

   @Test
   void theRateLimitSpacesTheRequests() throws Exception {
      LimitedDataSource dataSource = limited("quota-rate", 10, -1);
      long start = System.nanoTime();

      for(int i = 0; i < 6; i++) {
         RestQuotaManager.withQuota(dataSource, () -> "ok");
      }

      // six calls at ten a second: the first is free, the other five wait a tenth each
      long elapsed = millisSince(start);
      assertTrue(elapsed >= 400, "six calls took " + elapsed + " ms, the rate was not applied");
      assertTrue(elapsed < 3000, "six calls took " + elapsed + " ms");
   }

   @Test
   void noMoreConnectionsThanTheQuotaAllowRunAtOnce() throws Exception {
      LimitedDataSource dataSource = limited("quota-connections", -1, 2);

      assertEquals(2, peakConcurrency(dataSource, 8));
   }

   @Test
   void aFailingCallGivesItsConnectionBack() throws Exception {
      LimitedDataSource dataSource = limited("quota-failing", -1, 1);

      assertThrows(IllegalStateException.class, () -> RestQuotaManager.withQuota(
         dataSource, (RestQuotaManager.QuotaConstrainedMethod) () -> {
            throw new IllegalStateException("boom");
         }));

      // with the only connection lost, this call would wait for ever
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<String> next = executor.submit(() -> RestQuotaManager.withQuota(dataSource, () -> "ok"));
         assertEquals("ok", next.get(5, TimeUnit.SECONDS));
      }
      finally {
         executor.shutdownNow();
      }
   }

   @Test
   void aCallThatIsCancelledWhileItWaitsDoesNotLoseAConnection() throws Exception {
      LimitedDataSource dataSource = limited("quota-cancel", -1, 1);
      ExecutorService executor = Executors.newFixedThreadPool(3);

      try {
         CountDownLatch holding = new CountDownLatch(1);
         CountDownLatch release = new CountDownLatch(1);
         Future<String> holder = executor.submit(() -> RestQuotaManager.withQuota(dataSource, () -> {
            holding.countDown();
            release.await(10, TimeUnit.SECONDS);
            return "held";
         }));
         assertTrue(holding.await(5, TimeUnit.SECONDS));

         // a second call waits for the only connection and is cancelled
         Future<String> waiting = executor.submit(
            () -> RestQuotaManager.withQuota(dataSource, () -> "never"));
         Thread.sleep(200);
         waiting.cancel(true);
         release.countDown();
         assertEquals("held", holder.get(5, TimeUnit.SECONDS));

         // the connection is still there, and still only one
         assertEquals(1, peakConcurrency(dataSource, 4));
      }
      finally {
         executor.shutdownNow();
      }
   }

   @Test
   void dataSourcesWithDifferentNamesDoNotShareAQuota() throws Exception {
      LimitedDataSource first = limited("quota-first", -1, 1);
      LimitedDataSource second = limited("quota-second", -1, 1);
      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
         CountDownLatch bothInside = new CountDownLatch(2);
         List<Future<String>> calls = new ArrayList<>();

         for(LimitedDataSource dataSource : List.of(first, second)) {
            calls.add(executor.submit(() -> RestQuotaManager.withQuota(dataSource, () -> {
               bothInside.countDown();
               return bothInside.await(3, TimeUnit.SECONDS) ? "together" : "alone";
            })));
         }

         for(Future<String> call : calls) {
            assertEquals("together", call.get(5, TimeUnit.SECONDS));
         }
      }
      finally {
         executor.shutdownNow();
      }
   }

   @Test
   @Disabled("the quota is cached by the name of the data source only, so a data source that is " +
             "given other limits under the same name keeps the old ones while it is in use; " +
             "no Redmine bug yet")
   void aDataSourceWithNewLimitsGetsThem() throws Exception {
      assertEquals(1, peakConcurrency(limited("quota-changed", -1, 1), 4));

      // the same name, now allowed four connections (the data source was edited or replaced)
      assertEquals(4, peakConcurrency(limited("quota-changed", -1, 4), 8));
   }

   @Test
   @Disabled("the quota is cached by the name of the data source only, so a Salesforce account " +
             "whose type is changed keeps the limits of the old type; no Redmine bug yet")
   void aSalesforceAccountThatChangesTypeGetsTheLimitsOfTheNewType() throws Exception {
      // a developer account is allowed one request a second, a sandbox almost six
      SalesforceReportsDataSource developer = new SalesforceReportsDataSource();
      developer.setName("quota-salesforce");
      developer.setAccountType("developer");
      RestQuotaManager.withQuota(developer, () -> "ok");

      SalesforceReportsDataSource sandbox = new SalesforceReportsDataSource();
      sandbox.setName("quota-salesforce");
      sandbox.setAccountType("sandbox");
      long start = System.nanoTime();

      for(int i = 0; i < 6; i++) {
         RestQuotaManager.withQuota(sandbox, () -> "ok");
      }

      // five waits of about 170 ms with the rate of a sandbox, five of a second with the old one
      long elapsed = millisSince(start);
      assertTrue(elapsed < 2500, "six calls took " + elapsed + " ms, with the rate of the old type");
   }

   /** Runs the calls on separate threads and returns the most that were inside at once. */
   private static int peakConcurrency(LimitedDataSource dataSource, int calls) throws Exception {
      AtomicInteger inside = new AtomicInteger();
      AtomicInteger peak = new AtomicInteger();
      ExecutorService executor = Executors.newFixedThreadPool(calls);

      try {
         List<Future<String>> futures = new ArrayList<>();

         for(int i = 0; i < calls; i++) {
            futures.add(executor.submit(() -> RestQuotaManager.withQuota(dataSource, () -> {
               peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
               Thread.sleep(100);
               inside.decrementAndGet();
               return "ok";
            })));
         }

         for(Future<String> future : futures) {
            future.get(10, TimeUnit.SECONDS);
         }
      }
      finally {
         executor.shutdownNow();
      }

      return peak.get();
   }

   private static LimitedDataSource limited(String name, double requestsPerSecond,
                                            int maxConnections)
   {
      LimitedDataSource dataSource = new LimitedDataSource(requestsPerSecond, maxConnections);
      dataSource.setName(name);
      return dataSource;
   }

   private static long millisSince(long nanos) {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos);
   }

   /** An endpoint data source whose quota the test chooses. */
   private static final class LimitedDataSource extends ClockifyDataSource {
      LimitedDataSource(double requestsPerSecond, int maxConnections) {
         this.rate = requestsPerSecond;
         this.connections = maxConnections;
      }

      @Override
      public double getRequestsPerSecond() {
         return rate;
      }

      @Override
      public int getMaxConnections() {
         return connections;
      }

      private final double rate;
      private final int connections;
   }
}
