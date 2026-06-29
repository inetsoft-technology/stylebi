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
package inetsoft.sree.security;

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] clear() comment says "initial value of inherited threadlocal is also cleared".
 *             Actual: THREAD_LOCAL is a plain ThreadLocal<StringWrapper>, not InheritableThreadLocal.
 *             Child threads therefore cannot inherit the parent's orgId.
 *             The setBody(null) call before remove() is a defensive measure: if a caller cached
 *             the StringWrapper reference (e.g. in a hypothetical InheritableThreadLocal scenario),
 *             the body will read null after clear(). Under the current implementation this is a
 *             no-op for child threads, but the comment suggests the design once assumed inheritance.
 */

import inetsoft.web.portal.model.database.StringWrapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class OrganizationContextHolderThreadingTest {

   @BeforeEach
   void setUp() {
      OrganizationContextHolder.clear();
   }

   @AfterEach
   void tearDown() {
      OrganizationContextHolder.clear();
   }

   // ── basic single-thread operations ────────────────────────────────────────

   @Test
   void get_beforeSet_returnsNull() {
      assertNull(OrganizationContextHolder.getCurrentOrgId());
   }

   @Test
   void setAndGet_sameThread_returnsValue() {
      OrganizationContextHolder.setCurrentOrgId("org1");
      assertEquals("org1", OrganizationContextHolder.getCurrentOrgId());
   }

   @Test
   void setNull_getCurrentOrgId_returnsNull() {
      OrganizationContextHolder.setCurrentOrgId(null);
      assertNull(OrganizationContextHolder.getCurrentOrgId());
   }

   @Test
   void setTwice_get_returnsSecondValue() {
      OrganizationContextHolder.setCurrentOrgId("first");
      OrganizationContextHolder.setCurrentOrgId("second");
      assertEquals("second", OrganizationContextHolder.getCurrentOrgId());
   }

   @Test
   void get_afterClear_returnsNull() {
      OrganizationContextHolder.setCurrentOrgId("org1");
      OrganizationContextHolder.clear();
      assertNull(OrganizationContextHolder.getCurrentOrgId());
   }

   @Test
   void clear_whenNothingSet_doesNotThrow() {
      assertDoesNotThrow(OrganizationContextHolder::clear);
   }

   // ── clear() body-nulling behavior ─────────────────────────────────────────

   // clear() sets the wrapper body to null before calling remove(); this defensive pattern
   // ensures any caller that cached a StringWrapper reference sees null after clear().
   @Test
   void clear_nullsWrapperBody_beforeRemove() throws Exception {
      OrganizationContextHolder.setCurrentOrgId("org1");

      Field field = OrganizationContextHolder.class.getDeclaredField("THREAD_LOCAL");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      ThreadLocal<StringWrapper> tl = (ThreadLocal<StringWrapper>) field.get(null);
      StringWrapper wrapper = tl.get();

      assertNotNull(wrapper, "wrapper must exist after setCurrentOrgId");
      assertEquals("org1", wrapper.getBody());

      OrganizationContextHolder.clear();

      assertNull(wrapper.getBody(), "clear() must null the wrapper body before remove()");
      assertNull(OrganizationContextHolder.getCurrentOrgId());
   }

   // ── threading: isolation and inheritance ─────────────────────────────────

   // Suspect 1: THREAD_LOCAL is a plain ThreadLocal — child threads do NOT inherit the parent's
   // orgId. A caller expecting InheritableThreadLocal semantics would see null in child threads.
   @Test
   void childThread_doesNotInheritParentOrgId() throws Exception {
      OrganizationContextHolder.setCurrentOrgId("parent-org");

      AtomicReference<String> childValue = new AtomicReference<>("sentinel");
      Thread child = new Thread(() ->
         childValue.set(OrganizationContextHolder.getCurrentOrgId()));
      child.start();
      awaitChildValue(childValue, "sentinel");

      assertNull(childValue.get(),
                 "Child thread must not inherit parent orgId: THREAD_LOCAL is plain ThreadLocal");
   }

   // Two concurrent threads each hold an independent orgId — set operations on one thread
   // must not affect the other thread's view.
   @Test
   void twoThreads_haveIsolatedState() throws Exception {
      CountDownLatch setLatch = new CountDownLatch(2);
      CountDownLatch readLatch = new CountDownLatch(1);

      AtomicReference<String> valueA = new AtomicReference<>();
      AtomicReference<String> valueB = new AtomicReference<>();

      Thread threadA = new Thread(() -> {
         OrganizationContextHolder.setCurrentOrgId("orgA");
         setLatch.countDown();
         awaitQuietly(readLatch);
         valueA.set(OrganizationContextHolder.getCurrentOrgId());
      });
      Thread threadB = new Thread(() -> {
         OrganizationContextHolder.setCurrentOrgId("orgB");
         setLatch.countDown();
         awaitQuietly(readLatch);
         valueB.set(OrganizationContextHolder.getCurrentOrgId());
      });

      threadA.start();
      threadB.start();
      assertTrue(setLatch.await(10, TimeUnit.SECONDS));
      readLatch.countDown();
      awaitThreadValues(valueA, valueB);

      assertEquals("orgA", valueA.get(), "Thread A must retain its own orgId");
      assertEquals("orgB", valueB.get(), "Thread B must retain its own orgId");
   }

   // Thread-pool reuse: after clear() in task 1, the same thread running task 2 must see null.
   @Test
   void threadReuse_afterClear_seesNullNotStaleValue() throws Exception {
      AtomicReference<String> task2Value = new AtomicReference<>("not-set");

      Thread reusedThread = new Thread(() -> {
         // task 1
         OrganizationContextHolder.setCurrentOrgId("task1-org");
         OrganizationContextHolder.clear();

         // task 2 on same thread
         task2Value.set(OrganizationContextHolder.getCurrentOrgId());
      });

      reusedThread.start();
      awaitChildValue(task2Value, "not-set");

      assertNull(task2Value.get(),
                 "Reused thread must see null after clear(), not stale orgId from previous task");
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static void awaitQuietly(CountDownLatch latch) {
      try {
         latch.await(10, TimeUnit.SECONDS);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static void awaitChildValue(AtomicReference<String> value, String sentinel) {
      Awaitility.await()
         .atMost(Duration.ofSeconds(10))
         .pollInterval(Duration.ofMillis(10))
         .until(() -> !sentinel.equals(value.get()));
   }

   private static void awaitThreadValues(AtomicReference<String> valueA,
                                         AtomicReference<String> valueB) {
      Awaitility.await()
         .atMost(Duration.ofSeconds(10))
         .pollInterval(Duration.ofMillis(10))
         .until(() -> valueA.get() != null && valueB.get() != null);
   }
}
