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
package inetsoft.sree.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionRequestScope: opt-in per-thread memo.
 *  - no scope open: every call reaches the supplier (behavior identical to before the scope existed)
 *  - scope open: one supplier call per (owner identity, operation, argument), null included
 *  - nesting: only the outermost close ends the scope
 *  - cleanup after an exception, no sharing between threads, invalidation per owner
 */
@Tag("core")
class PermissionRequestScopeTest {
   @AfterEach
   void clear() {
      PermissionRequestScope.clear();
   }

   @Test
   void noScope_everyCallReachesSupplier() {
      AtomicInteger calls = new AtomicInteger();
      Object owner = new Object();

      for(int i = 0; i < 5; i++) {
         PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);
      }

      assertEquals(5, calls.get());
      assertFalse(PermissionRequestScope.isOpen());
   }

   @Test
   void scope_supplierRunsOncePerKey() {
      AtomicInteger calls = new AtomicInteger();
      Object owner = new Object();

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         for(int i = 0; i < 50; i++) {
            assertEquals(1, PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet));
         }

         assertEquals(1, calls.get());
         assertEquals(2, PermissionRequestScope.memo(owner, "op", "b", calls::incrementAndGet));
         assertEquals(3, PermissionRequestScope.memo(owner, "other", "a", calls::incrementAndGet));
      }

      assertFalse(PermissionRequestScope.isOpen());
   }

   @Test
   void scope_nullResultIsMemoized() {
      AtomicInteger calls = new AtomicInteger();
      Object owner = new Object();

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         for(int i = 0; i < 3; i++) {
            assertNull(PermissionRequestScope.memo(owner, "op", "a", () -> {
               calls.incrementAndGet();
               return null;
            }));
         }
      }

      assertEquals(1, calls.get());
   }

   @Test
   void scope_exceptionIsNotMemoized() {
      Object owner = new Object();
      AtomicInteger calls = new AtomicInteger();

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         assertThrows(IllegalStateException.class, () ->
            PermissionRequestScope.memo(owner, "op", "a", () -> {
               calls.incrementAndGet();
               throw new IllegalStateException();
            }));
         assertEquals("ok", PermissionRequestScope.memo(owner, "op", "a", () -> "ok"));
      }

      assertEquals(1, calls.get());
   }

   @Test
   void scope_ownersAreSeparatedByIdentityNotEquality() {
      // two equal-by-equals owners must not share an answer
      String ownerA = new String("same");
      String ownerB = new String("same");
      assertEquals(ownerA, ownerB);

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         assertEquals("a", PermissionRequestScope.memo(ownerA, "op", "x", () -> "a"));
         assertEquals("b", PermissionRequestScope.memo(ownerB, "op", "x", () -> "b"));
         assertEquals("a", PermissionRequestScope.memo(ownerA, "op", "x", () -> "other"));
      }
   }

   @Test
   void scope_nestedCloseKeepsOuterScope() {
      AtomicInteger calls = new AtomicInteger();
      Object owner = new Object();

      try(PermissionRequestScope.Handle outer = PermissionRequestScope.open()) {
         PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);

         try(PermissionRequestScope.Handle inner = PermissionRequestScope.open()) {
            PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);
         }

         assertTrue(PermissionRequestScope.isOpen());
         PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);
      }

      assertEquals(1, calls.get());
      assertFalse(PermissionRequestScope.isOpen());
   }

   @Test
   void scope_closeTwiceDoesNotEndOuterScope() {
      PermissionRequestScope.Handle outer = PermissionRequestScope.open();
      PermissionRequestScope.Handle inner = PermissionRequestScope.open();
      inner.close();
      inner.close();

      assertTrue(PermissionRequestScope.isOpen());
      outer.close();
      assertFalse(PermissionRequestScope.isOpen());
   }

   @Test
   void scope_clearedAfterException() {
      assertThrows(IllegalStateException.class, () -> {
         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            throw new IllegalStateException();
         }
      });

      assertFalse(PermissionRequestScope.isOpen());
   }

   @Test
   void scope_valuesDoNotSurviveAcrossScopes() {
      AtomicInteger calls = new AtomicInteger();
      Object owner = new Object();

      for(int i = 0; i < 2; i++) {
         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);
         }
      }

      assertEquals(2, calls.get());
   }

   @Test
   void scope_invalidateDropsOnlyThatOwner() {
      AtomicInteger callsA = new AtomicInteger();
      AtomicInteger callsB = new AtomicInteger();
      Object ownerA = new Object();
      Object ownerB = new Object();

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         PermissionRequestScope.memo(ownerA, "op", "a", callsA::incrementAndGet);
         PermissionRequestScope.memo(ownerB, "op", "a", callsB::incrementAndGet);
         PermissionRequestScope.invalidate(ownerA);
         PermissionRequestScope.memo(ownerA, "op", "a", callsA::incrementAndGet);
         PermissionRequestScope.memo(ownerB, "op", "a", callsB::incrementAndGet);
      }

      assertEquals(2, callsA.get());
      assertEquals(1, callsB.get());
   }

   @Test
   void invalidateWithoutScopeIsNoop() {
      assertDoesNotThrow(() -> PermissionRequestScope.invalidate(new Object()));
   }

   @Test
   void scope_isNotSharedAcrossThreads() throws Exception {
      AtomicInteger calls = new AtomicInteger();
      Object owner = new Object();
      boolean[] openInOther = new boolean[1];

      try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
         PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);

         Thread t = new Thread(() -> {
            openInOther[0] = PermissionRequestScope.isOpen();
            PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);
            PermissionRequestScope.memo(owner, "op", "a", calls::incrementAndGet);
         });
         t.start();
         t.join();
      }

      assertFalse(openInOther[0]);
      assertEquals(3, calls.get());
   }
}
