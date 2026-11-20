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

package inetsoft.uql.rest;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.test.TestHttpResponse;
import inetsoft.uql.rest.pagination.HttpHeader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the REST connector does with a response that is not successful: it gives up and says so,
 * except for a 429 that tells when to try again, which it waits for and repeats.
 */
@EndpointsSpringContext
@SreeHome
class RestErrorHandlerTest {
   @Test
   void aSuccessfulCallGivesItsValue() throws Exception {
      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> "value");

      assertTrue(response.shouldContinue());
      assertEquals("value", response.getHandledResult());
   }

   @Test
   void anErrorThatIsNotAResponseIsNotHandled() {
      assertThrows(IOException.class, () -> new RestErrorHandler().catchError(() -> {
         throw new IOException("connection reset");
      }));
   }

   @Test
   void anErrorStatusStopsTheQueryWithoutRetrying() throws Exception {
      AtomicInteger calls = new AtomicInteger();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         calls.incrementAndGet();
         throw failure(500, null);
      });

      assertFalse(response.shouldContinue());
      assertNull(response.getHandledResult());
      assertEquals(1, calls.get());
   }

   @Test
   void aTooManyRequestsWithoutAnAdviceIsNotRetried() throws Exception {
      AtomicInteger calls = new AtomicInteger();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         calls.incrementAndGet();
         throw failure(429, null);
      });

      assertFalse(response.shouldContinue());
      assertEquals(1, calls.get());
   }

   @Test
   void onlyATooManyRequestsIsRetriedEvenWhenAnotherErrorAdvisesAWait() throws Exception {
      // a 503 with a Retry-After is common, but only the 429 is repeated
      AtomicInteger calls = new AtomicInteger();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         calls.incrementAndGet();
         throw failure(503, "1");
      });

      assertFalse(response.shouldContinue());
      assertEquals(1, calls.get());
   }

   @Test
   void aTooManyRequestsIsRetriedAfterTheAdvisedWait() throws Exception {
      AtomicInteger calls = new AtomicInteger();
      long start = System.nanoTime();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         if(calls.incrementAndGet() == 1) {
            throw failure(429, "1");
         }

         return "second try";
      });

      assertTrue(response.shouldContinue());
      assertEquals("second try", response.getHandledResult());
      assertEquals(2, calls.get());
      assertTrue(millisSince(start) >= 900, "did not wait for the advised second");
   }

   @Test
   void aWaitThatIsTooLongIsNotWaitedFor() throws Exception {
      AtomicInteger calls = new AtomicInteger();
      long start = System.nanoTime();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         calls.incrementAndGet();
         throw failure(429, "3600");
      });

      assertFalse(response.shouldContinue());
      assertEquals(1, calls.get());
      assertTrue(millisSince(start) < 2000, "waited for an hour-long advice");
   }

   @Test
   void anAdviceThatCannotBeReadIsNotRetried() throws Exception {
      AtomicInteger calls = new AtomicInteger();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         calls.incrementAndGet();
         throw failure(429, "soon");
      });

      assertFalse(response.shouldContinue());
      assertEquals(1, calls.get());
   }

   @Test
   void aRetryThatFailsWithAnotherErrorStops() throws Exception {
      AtomicInteger calls = new AtomicInteger();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         if(calls.incrementAndGet() == 1) {
            throw failure(429, "1");
         }

         throw failure(500, null);
      });

      assertFalse(response.shouldContinue());
      assertEquals(2, calls.get());
   }

   @Test
   @Disabled("Bug #81940: a service that keeps answering 429 with a short Retry-After is retried " +
             "for ever, with no limit on the attempts; enable when it is fixed")
   void theRetriesOfATooManyRequestsHaveALimit() throws Exception {
      AtomicInteger calls = new AtomicInteger();
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<RestErrorResponse<String>> result = executor.submit(
            () -> new RestErrorHandler().catchError(() -> {
               calls.incrementAndGet();
               throw failure(429, "0");
            }));

         try {
            RestErrorResponse<String> response = result.get(3, TimeUnit.SECONDS);
            assertFalse(response.shouldContinue());
            assertTrue(calls.get() <= 20, calls.get() + " attempts");
         }
         catch(TimeoutException e) {
            fail("still retrying after 3 seconds and " + calls.get() + " attempts");
         }
      }
      finally {
         executor.shutdownNow();
      }
   }

   @Test
   @Disabled("Bug #81941: a Retry-After that is a date in the past makes the wait negative, and " +
             "the query fails with an IllegalArgumentException instead of retrying at once; " +
             "enable when it is fixed")
   void anAdviceThatIsADateInThePastRetriesAtOnce() throws Exception {
      AtomicInteger calls = new AtomicInteger();

      RestErrorResponse<String> response = new RestErrorHandler().catchError(() -> {
         if(calls.incrementAndGet() == 1) {
            throw failure(429, "Wed, 21 Oct 2015 07:28:00 GMT");
         }

         return "second try";
      });

      assertTrue(response.shouldContinue());
      assertEquals("second try", response.getHandledResult());
   }

   private static RestResponseException failure(int status, String retryAfter) {
      List<HttpHeader> headers = retryAfter == null ? List.of()
         : List.of(new HttpHeader("Retry-After", retryAfter));
      return new RestResponseException("HTTP " + status, new TestHttpResponse("", headers) {
         @Override
         public int getResponseStatusCode() {
            return status;
         }
      });
   }

   private static long millisSince(long nanos) {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos);
   }
}
