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
package inetsoft.uql.util;

/*
 * Bug #77783: RunQueryUserScopeWorksheetTest stubbed the shared SecurityEngine spy bean at test
 * time. A SreeEnv.save() makes the PropertiesEngine debouncer publish an
 * ApplicationPropertiesChangedEvent 500 ms later, on its own thread, and
 * SecurityEngine.handleApplicationPropertiesChanged then calls isSecurityEnabled() on the spy. When
 * that call landed between doAnswer(..).when(spy) and the stubbed checkPermission(..) call in setUp,
 * it took the pending answer: setUp threw UnfinishedStubbingException, and isSecurityEnabled() kept
 * answering with the checkPermission lambda for the rest of the class.
 *
 * This class runs the real class's tests, with its setUp and tearDown, and forces that interleaving
 * in the first test: the debouncer thread is parked inside handleApplicationPropertiesChanged on the
 * spy's initLock, and is released as soon as setUp leaves pending answers on the spy. If setUp never
 * does, it is released before the test body, and the same is done for tearDown.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.matchers.LocalizedMatcher;
import org.mockito.internal.progress.ArgumentMatcherStorage;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.mockito.internal.util.MockUtil;
import org.mockito.stubbing.Stubbing;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockingDetails;

@ExtendWith(RunQueryUserScopeWorksheetForeignThreadTest.ForeignThread.class)
@SreeHome
@Tag("core")
class RunQueryUserScopeWorksheetForeignThreadTest extends RunQueryUserScopeWorksheetTest {
   @BeforeAll
   static void armForeignThread() {
      ForeignThread.armed = true;
      ForeignThread.events.clear();
   }

   @AfterAll
   static void noStubbingLeftByForeignThread() {
      String stubbed = mockingDetails(SecurityEngine.getSecurity()).getStubbings().stream()
         .map(Stubbing::getInvocation)
         .filter(inv -> !"checkPermission".equals(inv.getMethod().getName()))
         .map(inv -> inv.getMethod().getName() + " at " + inv.getLocation())
         .collect(Collectors.joining("; "));
      assertEquals("", stubbed, "stubbings left on the SecurityEngine spy; forced interleaving: " +
                   ForeignThread.events);
   }

   static class ForeignThread implements BeforeEachCallback, BeforeTestExecutionCallback,
      AfterTestExecutionCallback, AfterEachCallback
   {
      static volatile boolean armed;
      static final List<String> events = Collections.synchronizedList(new ArrayList<>());

      @Override
      public void beforeEach(ExtensionContext context) throws Exception {
         if(!armed) {
            return;
         }

         armed = false;
         spy = SecurityEngine.getSecurity();
         initLock = (ReentrantLock) ReflectionTestUtils.getField(spy, "initLock");
         park("setUp");
         installMockingProgress();
      }

      @Override
      public void beforeTestExecution(ExtensionContext context) {
         if(release()) {
            events.add("released before the test body, setUp left no pending answers on the spy");
            parkInTearDown = true;
         }
      }

      // setUp stubbed nothing on the spy, so do the same for tearDown
      @Override
      public void afterTestExecution(ExtensionContext context) throws Exception {
         if(parkInTearDown) {
            parkInTearDown = false;
            awaitListener();
            park("tearDown");
         }
      }

      @Override
      public void afterEach(ExtensionContext context) throws Exception {
         if(release()) {
            events.add("released after the test, tearDown left no pending answers on the spy");
         }

         restoreMockingProgress();
         awaitListener();
         parked = null;
      }

      // parks the PropertiesEngine debouncer inside handleApplicationPropertiesChanged, on the
      // spy's initLock, before the given phase of the real test class runs
      private void park(String phase) throws Exception {
         this.phase = phase;
         initLock.lock();

         // the same kind of save as SecurityTestDataBuilder.setup() in setUpAll
         SreeEnv.setProperty("bug77783.foreign.thread", Long.toString(System.nanoTime()));
         SreeEnv.save();
         parked = awaitQueuedThread();

         if(parked == null || !inChangeListener(parked)) {
            String stack = parked == null ? "no thread" : parked.getName() + " " +
               Arrays.toString(parked.getStackTrace());
            release();
            fail("the PropertiesEngine debouncer did not park in " +
                 "handleApplicationPropertiesChanged: " + stack);
         }

         events.add("parked " + parked.getName() + " in handleApplicationPropertiesChanged before " +
                    phase);
      }

      // lets the listener finish before the next phase
      private void awaitListener() throws InterruptedException {
         long end = System.currentTimeMillis() + 10_000L;

         while(inChangeListener(parked) && System.currentTimeMillis() < end) {
            Thread.sleep(10);
         }
      }

      // called by main for each argument matcher, so the first call after when(spy) is inside
      // the window between when(spy) and the stubbed call
      private void onReportMatcher() throws InterruptedException {
         if(initLock.isHeldByCurrentThread() &&
            MockUtil.getInvocationContainer(spy).hasAnswersForStubbing())
         {
            release();
            long end = System.currentTimeMillis() + 10_000L;

            while(MockUtil.getInvocationContainer(spy).hasAnswersForStubbing() &&
                  System.currentTimeMillis() < end)
            {
               Thread.sleep(1);
            }

            events.add("released inside the stubbing window in " + phase + "; pending answers " +
                       (MockUtil.getInvocationContainer(spy).hasAnswersForStubbing() ?
                        "still pending after 10 s" : "taken by " + parked.getName()));
         }
      }

      private boolean release() {
         if(initLock != null && initLock.isHeldByCurrentThread()) {
            initLock.unlock();
            return true;
         }

         return false;
      }

      private Thread awaitQueuedThread() throws Exception {
         Method queued = ReentrantLock.class.getDeclaredMethod("getQueuedThreads");
         queued.setAccessible(true);
         long end = System.currentTimeMillis() + 30_000L;

         while(System.currentTimeMillis() < end) {
            @SuppressWarnings("unchecked")
            Collection<Thread> threads = (Collection<Thread>) queued.invoke(initLock);

            if(!threads.isEmpty()) {
               return threads.iterator().next();
            }

            Thread.sleep(5);
         }

         return null;
      }

      private static boolean inChangeListener(Thread thread) {
         return thread != null && Arrays.stream(thread.getStackTrace()).anyMatch(
            f -> "handleApplicationPropertiesChanged".equals(f.getMethodName()));
      }

      // wraps the matcher storage of main's MockingProgress. The MockingProgress itself is left
      // alone, so the location that doAnswer() records stays the line in setUp
      private void installMockingProgress() throws Exception {
         storageField = ThreadSafeMockingProgress.mockingProgress().getClass()
            .getDeclaredField("argumentMatcherStorage");
         storageField.setAccessible(true);
         progress = ThreadSafeMockingProgress.mockingProgress();
         ArgumentMatcherStorage storage = (ArgumentMatcherStorage) storageField.get(progress);
         original = storage;
         storageField.set(progress, new ArgumentMatcherStorage() {
            @Override
            public void reportMatcher(ArgumentMatcher<?> matcher) {
               try {
                  onReportMatcher();
               }
               catch(InterruptedException ex) {
                  throw new IllegalStateException(ex);
               }

               storage.reportMatcher(matcher);
            }

            @Override
            public List<LocalizedMatcher> pullLocalizedMatchers() {
               return storage.pullLocalizedMatchers();
            }

            @Override
            public void reportAnd() {
               storage.reportAnd();
            }

            @Override
            public void reportNot() {
               storage.reportNot();
            }

            @Override
            public void reportOr() {
               storage.reportOr();
            }

            @Override
            public void validateState() {
               storage.validateState();
            }

            @Override
            public void reset() {
               storage.reset();
            }
         });
      }

      private void restoreMockingProgress() throws Exception {
         if(original != null) {
            storageField.set(progress, original);
            original = null;
         }
      }

      private SecurityEngine spy;
      private ReentrantLock initLock;
      private Thread parked;
      private String phase;
      private boolean parkInTearDown;
      private Field storageField;
      private MockingProgress progress;
      private ArgumentMatcherStorage original;
   }
}
