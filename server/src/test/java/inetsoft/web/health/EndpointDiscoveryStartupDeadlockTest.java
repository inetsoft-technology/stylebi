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
package inetsoft.web.health;

import inetsoft.web.health.EndpointDiscoveryDeadlockHarness.Attempt;
import inetsoft.web.health.EndpointDiscoveryDeadlockHarness.Options;
import inetsoft.web.health.EndpointDiscoveryDeadlockHarness.Outcome;
import inetsoft.web.health.EndpointDiscoveryDeadlockHarness.Shape;
import org.junit.jupiter.api.Test;

import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76974: the server can hang forever at startup when a request on the server port lazily
 * initializes the DispatcherServlet while main refreshes the management child context. Both
 * threads run endpoint discovery on the shared parent {@code webEndpointDiscoverer}; the request
 * thread holds the parent singleton lock (inside {@code healthEndpointWebMvcHandlerMapping}) and
 * waits for a {@code filterEndpoints} bin, main holds that bin (inside
 * {@code EndpointDiscoverer.getFilterEndpoint}) and waits for the parent singleton lock.
 * <p>
 * The {@code baseline*} tests prove that {@link EndpointDiscoveryDeadlockHarness} forces that
 * deadlock, with the thread dump's frames, when no fix is present. They must keep passing after
 * the fix, because they never include it. The {@code startupCompletes*} tests run the same
 * forcing with {@link #FIX_SOURCES} and require startup to complete.
 */
class EndpointDiscoveryStartupDeadlockTest {
   /**
    * THE FIX PLUG POINT. Configuration or component classes of the fix, added as sources to the
    * harness application of the regression tests (never to the baseline tests).
    */
   private static final List<Class<?>> FIX_SOURCES = List.of();

   /** The request thread inserts 11 keys into a 16-bin table: P(collision) = 1 - (15/16)^11. */
   private static final int BASELINE_MAX_ATTEMPTS = 20;
   private static final int REGRESSION_ATTEMPTS = 10;
   private static final int NO_PARENT_LOCK_ATTEMPTS = 5;

   private static final String MAIN = "bug76974-main";
   private static final String REGISTRY =
      "org.springframework.beans.factory.support.DefaultSingletonBeanRegistry";
   private static final String BEAN_FACTORY =
      "org.springframework.beans.factory.support.AbstractBeanFactory";
   private static final String DISCOVERER =
      "org.springframework.boot.actuate.endpoint.annotation.EndpointDiscoverer";
   private static final String ENDPOINT_BEAN = DISCOVERER + "$EndpointBean";
   private static final String CHM = "java.util.concurrent.ConcurrentHashMap";
   private static final String RESERVATION = CHM + "$ReservationNode";
   private static final String HEALTH_MAPPING_CONFIG =
      "org.springframework.boot.actuate.autoconfigure.health." +
      "HealthEndpointWebExtensionConfiguration$MvcAdditionalHealthEndpointPathsConfiguration";
   private static final String CHILD_MAPPING_CONFIG =
      "org.springframework.boot.actuate.autoconfigure.endpoint.web.servlet." +
      "WebMvcEndpointManagementContextConfiguration";
   private static final String CHILD_INITIALIZER =
      "org.springframework.boot.actuate.autoconfigure.web.server.ChildManagementContextInitializer";
   private static final String DISPATCHER = "org.springframework.web.servlet.DispatcherServlet";
   private static final String WRAPPER = "org.apache.catalina.core.StandardWrapper";

   @Test
   void baselineHarnessReproducesTheDumpsDeadlockWithoutFix() {
      Attempt deadlock = firstDeadlock(Shape.MAIN_AND_SERVER_REQUEST, List.of(),
                                       BASELINE_MAX_ATTEMPTS);
      assertDumpShape(deadlock);
      assertEquals(MAIN, deadlock.childSideThread.getName(), deadlock.describe());
   }

   @Test
   void baselineHarnessReproducesTheManagementRequestShapeWithoutFix() {
      Attempt deadlock = firstDeadlock(Shape.MANAGEMENT_REQUEST_AND_SERVER_REQUEST, List.of(),
                                       BASELINE_MAX_ATTEMPTS);
      assertDumpShape(deadlock);
      assertNotEquals(MAIN, deadlock.childSideThread.getName(), deadlock.describe());
      assertTrue(deadlock.deadlockedInfo(deadlock.harnessMain).isEmpty(),
                 "main must not be in the cycle\n" + deadlock.describe());
   }

   @Test
   void startupCompletesWhenServerRequestRacesManagementContextRefresh() {
      assertStartupCompletes(Shape.MAIN_AND_SERVER_REQUEST);
   }

   @Test
   void startupCompletesWhenManagementAndServerRequestsRace() {
      assertStartupCompletes(Shape.MANAGEMENT_REQUEST_AND_SERVER_REQUEST);
   }

   /**
    * Row C-R5 (LB2): the same overlap, but the request thread runs discovery from a controller
    * method rather than a parent bean factory method, so it never holds the parent singleton
    * lock across its discovery. That does not deadlock, even though main is parked on the
    * parent lock while holding a reserved bin.
    */
   @Test
   void overlappingDiscoveryOutsideTheParentLockDoesNotDeadlock() {
      for(int i = 1; i <= NO_PARENT_LOCK_ATTEMPTS; i++) {
         Attempt attempt = EndpointDiscoveryDeadlockHarness.run(
            Options.of(Shape.DISCOVERY_OUTSIDE_PARENT_LOCK, List.of()));
         assertEquals(Outcome.STARTED, attempt.outcome, "attempt " + i + ": " + attempt.describe());
         assertTrue(attempt.childSideParked && attempt.requestHeldParentLock &&
                       attempt.childSideBlockedOnParentLock,
                    "harness did not force the overlap, attempt " + i + ": " + attempt.describe());
      }
   }

   private static void assertStartupCompletes(Shape shape) {
      for(int i = 1; i <= REGRESSION_ATTEMPTS; i++) {
         Attempt attempt = EndpointDiscoveryDeadlockHarness.run(Options.of(shape, FIX_SOURCES));
         assertNotEquals(Outcome.DEADLOCK, attempt.outcome,
                         "startup deadlocked, attempt " + i + ": " + attempt.describe());
         assertEquals(Outcome.STARTED, attempt.outcome, "attempt " + i + ": " + attempt.describe());

         if(attempt.requestHeldParentLock) {
            assertTrue(attempt.overlapObserved,
                       "the request held the parent singleton lock but main was not inside the " +
                          "management context refresh, attempt " + i + ": " + attempt.describe());
         }
      }
   }

   private static Attempt firstDeadlock(Shape shape, List<Class<?>> sources, int maxAttempts) {
      List<String> others = new ArrayList<>();

      for(int i = 1; i <= maxAttempts; i++) {
         Attempt attempt = EndpointDiscoveryDeadlockHarness.run(Options.of(shape, sources));

         if(attempt.outcome == Outcome.DEADLOCK) {
            return attempt;
         }

         assertEquals(Outcome.STARTED, attempt.outcome, "attempt " + i + ": " + attempt.describe());
         assertTrue(attempt.childSideParked && attempt.requestHeldParentLock &&
                       attempt.childSideBlockedOnParentLock,
                    "harness did not force the interleaving, attempt " + i + ": " +
                       attempt.describe());
         others.add("attempt " + i + ": no bin collision, started in " + attempt.durationMs + " ms");
      }

      return fail("no deadlock in " + maxAttempts + " forced attempts: " + others);
   }

   /**
    * The two deadlocked threads must match the dump: the child side waits for the parent
    * singleton lock at {@code getSingleton} inside {@code getFilterEndpoint}'s mapping function
    * while holding the bin's ReservationNode; the request thread holds the parent singleton lock
    * inside {@code healthEndpointWebMvcHandlerMapping} and is blocked on that ReservationNode at
    * {@code computeIfAbsent}.
    */
   private static void assertDumpShape(Attempt attempt) {
      String dump = attempt.describe();
      ThreadInfo child = attempt.deadlockedInfo(attempt.childSideThread)
         .orElseThrow(() -> new AssertionError("child side not deadlocked\n" + dump));
      ThreadInfo request = attempt.deadlockedInfo(attempt.requestThread)
         .orElseThrow(() -> new AssertionError("request thread not deadlocked\n" + dump));
      assertEquals(2, attempt.deadlocked.length, dump);

      // child side: waits for the parent singleton lock, owned by the request thread
      assertEquals(Thread.State.WAITING, child.getThreadState(), dump);
      assertTrue(child.getLockName().contains("ReentrantLock"), dump);
      assertEquals(request.getThreadId(), child.getLockOwnerId(), dump);
      assertFramesInOrder(child, dump,
                          REGISTRY + ".getSingleton",
                          BEAN_FACTORY + ".doGetBean",
                          ENDPOINT_BEAN + ".getBean",
                          CHM + ".computeIfAbsent",
                          DISCOVERER + ".getFilterEndpoint",
                          DISCOVERER + ".getEndpoints",
                          CHILD_MAPPING_CONFIG + ".webEndpointServletHandlerMapping|" +
                             CHILD_MAPPING_CONFIG + ".managementHealthEndpointWebMvcHandlerMapping");
      assertEquals(REGISTRY + ".getSingleton", firstSpringFrame(child), dump);
      MonitorInfo reservation = Arrays.stream(child.getLockedMonitors())
         .filter(m -> RESERVATION.equals(m.getClassName()))
         .findFirst()
         .orElseThrow(() -> new AssertionError("child side holds no ReservationNode\n" + dump));
      assertEquals(CHM + ".computeIfAbsent", frameName(reservation.getLockedStackFrame()), dump);

      // request thread: blocked on that ReservationNode, holding the parent singleton lock
      assertEquals(Thread.State.BLOCKED, request.getThreadState(), dump);
      assertEquals(RESERVATION, request.getLockInfo().getClassName(), dump);
      assertEquals(reservation.getIdentityHashCode(), request.getLockInfo().getIdentityHashCode(),
                   dump);
      assertEquals(attempt.reservationIdentity, reservation.getIdentityHashCode(), dump);
      assertEquals(child.getThreadId(), request.getLockOwnerId(), dump);
      assertEquals(CHM + ".computeIfAbsent", frameName(request.getStackTrace()[0]), dump);
      assertFramesInOrder(request, dump,
                          CHM + ".computeIfAbsent",
                          DISCOVERER + ".getFilterEndpoint",
                          DISCOVERER + ".getEndpoints",
                          HEALTH_MAPPING_CONFIG + ".healthEndpointWebMvcHandlerMapping",
                          REGISTRY + ".getSingleton",
                          DISPATCHER + ".initHandlerMappings",
                          WRAPPER + ".allocate");
      assertTrue(attempt.requestHeldParentLock, dump);
   }

   private static void assertFramesInOrder(ThreadInfo info, String dump, String... frames) {
      StackTraceElement[] stack = info.getStackTrace();
      int from = 0;

      for(String frame : frames) {
         int found = -1;

         for(int i = from; i < stack.length; i++) {
            if(Arrays.asList(frame.split("\\|")).contains(frameName(stack[i]))) {
               found = i;
               break;
            }
         }

         assertTrue(found >= 0, "\"" + info.getThreadName() + "\" lacks " + frame +
            " (in order " + Arrays.toString(frames) + ")\n" + dump);
         from = found + 1;
      }
   }

   private static String firstSpringFrame(ThreadInfo info) {
      return Arrays.stream(info.getStackTrace())
         .filter(f -> f.getClassName().startsWith("org.springframework."))
         .findFirst()
         .map(EndpointDiscoveryStartupDeadlockTest::frameName)
         .orElse("");
   }

   private static String frameName(StackTraceElement frame) {
      return frame.getClassName() + "." + frame.getMethodName();
   }
}
