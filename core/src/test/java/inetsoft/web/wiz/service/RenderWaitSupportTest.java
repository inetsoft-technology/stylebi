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
    * Bug #76350 follow-on (item A) refute round: {@code RenderWaitSupport} runs its work on a
    * plain JDK virtual thread ({@code Executors.newVirtualThreadPerTaskExecutor()}), not a
    * {@link inetsoft.util.GroupedThread}. {@code ThreadContext.getContextPrincipal()} checks
    * {@code GroupedThread} first and only falls back to a plain, non-inheritable
    * {@code ThreadLocal<Principal>} otherwise — a value set on the calling thread before
    * {@code awaitOrRetry} is invoked is NOT visible inside the wrapped {@code Callable}, even
    * though the callable runs synchronously (from the caller's point of view) within the same
    * method call.
    *
    * <p>This is not new to item A's fix — {@code ViewsheetEditService.ensureTableDataReady}
    * (bug #76331) already crosses this exact same executor boundary in production, and its own
    * test suite ({@code ViewsheetEditServiceTest}, {@code ScriptImageServiceTest},
    * {@code ViewsheetAssemblyAgentControllerTest}) does not exercise a join-backed table, so it
    * never actually observes this gap either way (confirmed by reading those tests directly: all
    * three simulate "slow" by stubbing a mocked sandbox/service method with
    * {@code Thread.sleep}, never by running a real {@code JoinQuery}). This test pins the
    * mechanism itself down directly — with real {@code ThreadContext}, no mocks — so item A's own
    * fix (which calls this same {@code awaitOrRetry} entry point, not a new one) is confirmed not
    * to introduce a NEW instance of the problem, even though it does not fix the pre-existing one
    * either; that is a larger, cross-cutting gap in {@code RenderWaitSupport} itself, out of
    * scope for this item.</p>
    */
   @Test
   void principalSetOnTheCallingThreadIsNotVisibleInsideTheWrappedCallable() throws Exception {
      Principal principal = TestPrincipals.user("alice", "host-org");
      ThreadContext.setPrincipal(principal);

      try {
         AtomicReference<Principal> seenInsideCallable = new AtomicReference<>();

         RenderWaitSupport.awaitOrRetry(() -> {
            seenInsideCallable.set(ThreadContext.getContextPrincipal());
            return null;
         }, 2_000, 2);

         assertNotEquals(principal, seenInsideCallable.get(),
            "documents a real, pre-existing gap (not introduced by item A): a plain " +
            "ThreadLocal principal set on the calling thread does not propagate onto " +
            "RenderWaitSupport's virtual-thread executor. If this assertion ever starts " +
            "failing, RenderWaitSupport has started propagating context correctly -- update " +
            "this test's expectation, and reconsider whether the pre-existing gap noted in " +
            "bug #76331's fix and this item's write-up still applies.");
      }
      finally {
         ThreadContext.setPrincipal(null);
      }
   }
}
