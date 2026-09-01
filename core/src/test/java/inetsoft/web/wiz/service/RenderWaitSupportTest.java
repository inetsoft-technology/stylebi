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
package inetsoft.web.wiz.service;

import inetsoft.util.ThreadContext;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@WizAgentTestSupport
class RenderWaitSupportTest {

   @Test
   void returnsTheWorkResultWhenItFinishesInTime() throws Exception {
      String result = RenderWaitSupport.awaitOrRetry(() -> "ready", 2_000, 2);
      assertEquals("ready", result);
   }

   @Test
   void throwsRenderNotReadyAfterTheTimeout() {
      assertThrows(RenderNotReadyException.class, () -> RenderWaitSupport.awaitOrRetry(() -> {
         Thread.sleep(3_000);
         return null;
      }, 500, 1));
   }

   /**
    * Bug #76350 follow-on (item A) originally found: {@code RenderWaitSupport} runs its work on
    * a plain JDK virtual thread ({@code Executors.newVirtualThreadPerTaskExecutor()}), not a
    * {@link inetsoft.util.GroupedThread}. {@code ThreadContext.getContextPrincipal()} checks
    * {@code GroupedThread} first and only falls back to a plain, non-inheritable
    * {@code ThreadLocal<Principal>} otherwise — a value set on the calling thread before
    * {@code awaitOrRetry} is invoked was NOT visible inside the wrapped {@code Callable}, even
    * though the callable runs synchronously (from the caller's point of view) within the same
    * method call.
    *
    * <p>This was not new to item A's fix — {@code ViewsheetEditService.ensureTableDataReady}
    * (bug #76331) already crosses this exact same executor boundary in production. It was later
    * confirmed (PSM-004, reopened) that this gap actually causes a production-visible
    * failure: {@code GraphTypeUtil.checkChartStylePermission} reads
    * {@code ThreadContext.getContextPrincipal()} and denies every chart style when it is null,
    * permanently caching a bogus {@code -1} chart type onto the shared, live
    * {@code ChartInfo}/{@code ChartAggregateRef} object. {@code RenderWaitSupport.awaitOrRetry}
    * now captures the calling thread's principal (and locale) and re-installs them inside the
    * wrapped {@code Callable}, clearing them afterward, so this test now pins the fixed
    * behavior: the principal set on the calling thread IS visible inside the callable.</p>
    */
   @Test
   void principalSetOnTheCallingThreadIsVisibleInsideTheWrappedCallable() throws Exception {
      Principal principal = TestPrincipals.user("alice", "host-org");
      ThreadContext.setPrincipal(principal);

      try {
         AtomicReference<Principal> seenInsideCallable = new AtomicReference<>();

         RenderWaitSupport.awaitOrRetry(() -> {
            seenInsideCallable.set(ThreadContext.getContextPrincipal());
            return null;
         }, 2_000, 2);

         assertEquals(principal, seenInsideCallable.get(),
            "RenderWaitSupport.awaitOrRetry should propagate the calling thread's principal " +
            "onto the virtual thread it runs work on (PSM-004, reopened); if this " +
            "fails, the principal-propagation fix has regressed.");
      }
      finally {
         ThreadContext.setPrincipal(null);
      }
   }

   /**
    * Companion to the principal-propagation test above: the fix captures
    * {@code ThreadContext.getLocale()} the same way and for the same reason, so it should
    * propagate identically.
    */
   @Test
   void localeSetOnTheCallingThreadIsVisibleInsideTheWrappedCallable() throws Exception {
      Locale locale = Locale.GERMANY;
      ThreadContext.setLocale(locale);

      try {
         AtomicReference<Locale> seenInsideCallable = new AtomicReference<>();

         RenderWaitSupport.awaitOrRetry(() -> {
            seenInsideCallable.set(ThreadContext.getLocale());
            return null;
         }, 2_000, 2);

         assertEquals(locale, seenInsideCallable.get(),
            "RenderWaitSupport.awaitOrRetry should propagate the calling thread's locale onto " +
            "the virtual thread it runs work on, the same as the principal.");
      }
      finally {
         ThreadContext.setLocale(null);
      }
   }

   /**
    * The fix wraps {@code work} in its own try/finally to clear the propagated principal/locale
    * off the virtual thread afterward -- this guards that the wrapping does not interfere with
    * {@code awaitOrRetry}'s existing exception-unwrapping behavior (the {@code ExecutionException}
    * cause should still surface as-is).
    */
   @Test
   void exceptionFromWorkStillPropagatesThroughTheContextPropagatingWrapper() {
      ThreadContext.setPrincipal(TestPrincipals.user("bob", "host-org"));

      try {
         IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> RenderWaitSupport.awaitOrRetry(() -> {
               throw new IllegalStateException("boom");
            }, 2_000, 2));

         assertEquals("boom", thrown.getMessage());
      }
      finally {
         ThreadContext.setPrincipal(null);
      }
   }
}
