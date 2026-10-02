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
package inetsoft.uql.rest.auth;

import inetsoft.uql.rest.auth.ClientCredentialsTokenCache.ClientCredentialsRequest;
import inetsoft.uql.rest.auth.ClientCredentialsTokenCache.TokenResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientCredentialsTokenCacheTest {
   @Test
   void reusesTokenUntilItExpires() throws Exception {
      final MutableClock clock = new MutableClock();
      final ClientCredentialsTokenCache cache = new ClientCredentialsTokenCache(clock);
      final AtomicInteger fetches = new AtomicInteger();
      final ClientCredentialsTokenCache.TokenFetcher fetcher =
         r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600);

      assertEquals("token1", cache.getAccessToken(REQUEST, fetcher));
      assertEquals("token1", cache.getAccessToken(REQUEST, fetcher));

      // still valid just before the renewal margin
      clock.advance(Duration.ofSeconds(3600).minus(ClientCredentialsTokenCache.EXPIRATION_MARGIN)
                       .minusSeconds(1));
      assertEquals("token1", cache.getAccessToken(REQUEST, fetcher));
      assertEquals(1, fetches.get());

      // renewed once inside the margin
      clock.advance(Duration.ofSeconds(1));
      assertEquals("token2", cache.getAccessToken(REQUEST, fetcher));
      assertEquals(2, fetches.get());
   }

   @Test
   void usesDefaultLifetimeWithoutExpiresIn() throws Exception {
      final MutableClock clock = new MutableClock();
      final ClientCredentialsTokenCache cache = new ClientCredentialsTokenCache(clock);
      final AtomicInteger fetches = new AtomicInteger();
      final ClientCredentialsTokenCache.TokenFetcher fetcher =
         r -> new TokenResponse("token" + fetches.incrementAndGet(), 0);

      cache.getAccessToken(REQUEST, fetcher);
      clock.advance(ClientCredentialsTokenCache.DEFAULT_LIFETIME
                       .minus(ClientCredentialsTokenCache.EXPIRATION_MARGIN).minusSeconds(1));
      assertEquals("token1", cache.getAccessToken(REQUEST, fetcher));

      clock.advance(Duration.ofSeconds(1));
      assertEquals("token2", cache.getAccessToken(REQUEST, fetcher));
   }

   @Test
   void shortLivedTokenIsStillCached() throws Exception {
      final MutableClock clock = new MutableClock();
      final ClientCredentialsTokenCache cache = new ClientCredentialsTokenCache(clock);
      final AtomicInteger fetches = new AtomicInteger();
      final ClientCredentialsTokenCache.TokenFetcher fetcher =
         r -> new TokenResponse("token" + fetches.incrementAndGet(), 20);

      cache.getAccessToken(REQUEST, fetcher);
      clock.advance(Duration.ofSeconds(9));
      assertEquals("token1", cache.getAccessToken(REQUEST, fetcher));

      clock.advance(Duration.ofSeconds(1));
      assertEquals("token2", cache.getAccessToken(REQUEST, fetcher));
   }

   @Test
   void concurrentRequestsFetchOnce() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());
      final AtomicInteger fetches = new AtomicInteger();
      final CountDownLatch fetching = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
      final ClientCredentialsTokenCache.TokenFetcher fetcher = r -> {
         fetches.incrementAndGet();
         fetching.countDown();
         release.await(10, TimeUnit.SECONDS);
         return new TokenResponse("token", 3600);
      };

      final int threads = 20;
      final ExecutorService executor = Executors.newFixedThreadPool(threads);

      try {
         final List<Future<String>> results = new ArrayList<>();

         for(int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> cache.getAccessToken(REQUEST, fetcher)));
         }

         assertTrue(fetching.await(10, TimeUnit.SECONDS));
         awaitWaitingThreads(cache, threads - 1);
         release.countDown();

         for(Future<String> result : results) {
            assertEquals("token", result.get(10, TimeUnit.SECONDS));
         }
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(1, fetches.get());
   }

   @Test
   void failedRequestIsNotCached() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());
      final AtomicInteger fetches = new AtomicInteger();

      assertThrows(IOException.class, () -> cache.getAccessToken(REQUEST, r -> {
         fetches.incrementAndGet();
         throw new IOException("401");
      }));

      assertEquals("token", cache.getAccessToken(REQUEST, r -> {
         fetches.incrementAndGet();
         return new TokenResponse("token", 3600);
      }));
      assertEquals(2, fetches.get());
   }

   @Test
   void emptyAccessTokenIsRejected() {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());

      assertThrows(IOException.class,
                   () -> cache.getAccessToken(REQUEST, r -> new TokenResponse("", 3600)));
      assertThrows(IOException.class,
                   () -> cache.getAccessToken(REQUEST, r -> new TokenResponse(null, 3600)));
   }

   @Test
   void changedCredentialsUseNewEntry() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());
      final ClientCredentialsRequest newSecret = new ClientCredentialsRequest(
         REQUEST.tokenUri(), REQUEST.clientId(), "other secret", REQUEST.scope(),
         REQUEST.audience(), REQUEST.clientAuthMethod());
      final ClientCredentialsRequest newScope = new ClientCredentialsRequest(
         REQUEST.tokenUri(), REQUEST.clientId(), REQUEST.clientSecret(), "read write",
         REQUEST.audience(), REQUEST.clientAuthMethod());

      assertEquals("a", cache.getAccessToken(REQUEST, r -> new TokenResponse("a", 3600)));
      assertEquals("b", cache.getAccessToken(newSecret, r -> new TokenResponse("b", 3600)));
      assertEquals("c", cache.getAccessToken(newScope, r -> new TokenResponse("c", 3600)));
      assertEquals("a", cache.getAccessToken(REQUEST, r -> fail("should be cached")));
      assertEquals(3, cache.size());
   }

   @Test
   void rejectedTokenIsDiscardedOnlyIfStillCached() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());

      cache.getAccessToken(REQUEST, r -> new TokenResponse("a", 3600));
      assertTrue(cache.invalidate(REQUEST, "a"));
      assertEquals("b", cache.getAccessToken(REQUEST, r -> new TokenResponse("b", 3600)));

      // a second request rejected with the old token doesn't discard its replacement, it is
      // retried with it
      assertTrue(cache.invalidate(REQUEST, "a"));
      assertEquals("b", cache.getAccessToken(REQUEST, r -> fail("should be cached")));
   }

   @Test
   void tokenRenewedAfterRejectionIsNotDiscarded() throws Exception {
      final MutableClock clock = new MutableClock();
      final ClientCredentialsTokenCache cache = new ClientCredentialsTokenCache(clock);
      final AtomicInteger fetches = new AtomicInteger();
      final ClientCredentialsTokenCache.TokenFetcher fetcher =
         r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600);

      cache.getAccessToken(REQUEST, fetcher);
      assertTrue(cache.invalidate(REQUEST, "token1"));
      assertEquals("token2", cache.getAccessToken(REQUEST, fetcher));

      // the new token is rejected too, so the cause isn't the token: no more token requests
      for(int i = 0; i < 10; i++) {
         assertFalse(cache.invalidate(REQUEST, "token2"));
         assertEquals("token2", cache.getAccessToken(REQUEST, fetcher));
      }

      assertEquals(2, fetches.get());

      // a later revocation of the new token is still recovered from
      clock.advance(ClientCredentialsTokenCache.REJECTION_RETRY_INTERVAL);
      assertTrue(cache.invalidate(REQUEST, "token2"));
      assertEquals("token3", cache.getAccessToken(REQUEST, fetcher));
   }

   @Test
   void tokenRenewedOnExpiryCanBeDiscarded() throws Exception {
      final MutableClock clock = new MutableClock();
      final ClientCredentialsTokenCache cache = new ClientCredentialsTokenCache(clock);
      final AtomicInteger fetches = new AtomicInteger();
      final ClientCredentialsTokenCache.TokenFetcher fetcher =
         r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600);

      cache.getAccessToken(REQUEST, fetcher);
      assertTrue(cache.invalidate(REQUEST, "token1"));
      cache.getAccessToken(REQUEST, fetcher);

      clock.advance(Duration.ofHours(1));
      assertEquals("token3", cache.getAccessToken(REQUEST, fetcher));
      assertTrue(cache.invalidate(REQUEST, "token3"));
   }

   @Test
   void requestsRejectedTogetherAreAllRetried() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());
      final AtomicInteger fetches = new AtomicInteger();
      final ClientCredentialsTokenCache.TokenFetcher fetcher =
         r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600);

      cache.getAccessToken(REQUEST, fetcher);

      // both requests used token1 and are rejected before either requests a new token
      assertTrue(cache.invalidate(REQUEST, "token1"));
      assertTrue(cache.invalidate(REQUEST, "token1"));

      assertEquals("token2", cache.getAccessToken(REQUEST, fetcher));
      assertEquals("token2", cache.getAccessToken(REQUEST, fetcher));
      assertEquals(2, fetches.get());
   }

   @Test
   void verifyRequestsTokenWithoutReplacingCachedToken() throws Exception {
      final MutableClock clock = new MutableClock();
      final ClientCredentialsTokenCache cache = new ClientCredentialsTokenCache(clock);
      final AtomicInteger fetches = new AtomicInteger();
      final ClientCredentialsTokenCache.TokenFetcher fetcher =
         r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600);

      assertEquals("token1", cache.getAccessToken(REQUEST, fetcher));
      cache.verify(REQUEST, fetcher);
      assertEquals(2, fetches.get());
      assertEquals("token1", cache.getAccessToken(REQUEST, fetcher));
   }

   @Test
   void successfulVerifyIsReusedBriefly() throws Exception {
      final MutableClock clock = new MutableClock();
      final ClientCredentialsTokenCache cache = new ClientCredentialsTokenCache(clock);
      final AtomicInteger fetches = new AtomicInteger();

      cache.verify(REQUEST, r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600));

      // e.g. the data source status refresh testing every data source
      for(int i = 0; i < 10; i++) {
         cache.verify(REQUEST, r -> fail("should reuse the result"));
      }

      assertEquals(1, fetches.get());

      clock.advance(ClientCredentialsTokenCache.VERIFY_INTERVAL);
      cache.verify(REQUEST, r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600));
      assertEquals(2, fetches.get());
   }

   @Test
   void failedVerifyIsNotReused() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());

      assertThrows(IOException.class, () -> cache.verify(REQUEST, r -> {
         throw new IOException("invalid_client");
      }));

      // the client was corrected in the authorization server, testing again shows it
      final AtomicInteger fetches = new AtomicInteger();
      cache.verify(REQUEST, r -> new TokenResponse("token" + fetches.incrementAndGet(), 3600));
      assertEquals(1, fetches.get());
   }

   @Test
   void unknownTokenIsNotRetried() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());

      assertFalse(cache.invalidate(REQUEST, "a"));
   }

   @Test
   void waitingThreadsShareFailure() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());
      final AtomicInteger fetches = new AtomicInteger();
      final CountDownLatch fetching = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
      final ClientCredentialsTokenCache.TokenFetcher fetcher = r -> {
         fetches.incrementAndGet();
         fetching.countDown();
         release.await(10, TimeUnit.SECONDS);
         throw new IOException("token endpoint unavailable");
      };

      final int threads = 10;
      final ExecutorService executor = Executors.newFixedThreadPool(threads);

      try {
         final List<Future<String>> results = new ArrayList<>();

         for(int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> cache.getAccessToken(REQUEST, fetcher)));
         }

         assertTrue(fetching.await(10, TimeUnit.SECONDS));
         awaitWaitingThreads(cache, threads - 1);
         release.countDown();

         for(Future<String> result : results) {
            final ExecutionException e =
               assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
            assertTrue(e.getCause().getMessage().contains("token endpoint unavailable"));
         }
      }
      finally {
         executor.shutdownNow();
      }

      // the threads waiting for the failed request don't each repeat it
      assertEquals(1, fetches.get());

      // a later request tries again
      assertEquals("token", cache.getAccessToken(REQUEST, r -> new TokenResponse("token", 3600)));
   }

   @Test
   void waitingForTokenIsInterruptible() throws Exception {
      final ClientCredentialsTokenCache cache =
         new ClientCredentialsTokenCache(Clock.systemUTC());
      final CountDownLatch fetching = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
      final ExecutorService executor = Executors.newFixedThreadPool(1);

      try {
         executor.submit(() -> cache.getAccessToken(REQUEST, r -> {
            fetching.countDown();
            release.await(10, TimeUnit.SECONDS);
            return new TokenResponse("token", 3600);
         }));
         assertTrue(fetching.await(10, TimeUnit.SECONDS));

         final AtomicReference<Throwable> thrown = new AtomicReference<>();
         final Thread waiting = new Thread(() -> {
            try {
               cache.getAccessToken(REQUEST, r -> fail("should wait"));
            }
            catch(Throwable e) {
               thrown.set(e);
            }
         });
         waiting.start();
         awaitWaitingThreads(cache, 1);

         // cancelling a query interrupts its thread, which stops waiting while the token
         // request on the other thread is still in progress
         waiting.interrupt();
         waiting.join(5000L);

         assertFalse(waiting.isAlive());
         assertInstanceOf(InterruptedException.class, thrown.get());
         assertEquals(1, release.getCount());
      }
      finally {
         release.countDown();
         executor.shutdownNow();
      }
   }

   @Test
   void secretIsNotExposed() {
      assertFalse(REQUEST.toString().contains(REQUEST.clientSecret()));
      assertFalse(REQUEST.cacheKey().contains(REQUEST.clientSecret()));
   }

   /**
    * Waits until the given number of threads are waiting for the token of REQUEST.
    */
   private static void awaitWaitingThreads(ClientCredentialsTokenCache cache, int count)
      throws InterruptedException
   {
      final long timeout = System.currentTimeMillis() + 10000L;

      while(cache.getWaitingThreadCount(REQUEST) < count) {
         assertTrue(System.currentTimeMillis() < timeout,
                    "timed out waiting for " + count + " threads to wait for the token");
         Thread.sleep(10L);
      }
   }

   private static final ClientCredentialsRequest REQUEST = new ClientCredentialsRequest(
      "https://auth.example.com/token", "client", "s3cr3t-value", "read", null,
      ClientAuthMethod.BASIC);

   private static final class MutableClock extends Clock {
      void advance(Duration duration) {
         instant = instant.plus(duration);
      }

      @Override
      public ZoneId getZone() {
         return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(ZoneId zone) {
         return this;
      }

      @Override
      public Instant instant() {
         return instant;
      }

      private volatile Instant instant = Instant.parse("2026-01-01T00:00:00Z");
   }
}
