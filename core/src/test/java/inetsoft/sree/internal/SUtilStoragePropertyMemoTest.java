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
package inetsoft.sree.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.PermissionRequestScope;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80317: the uncached storage property read is made once per property per request scope.
 */
@org.junit.jupiter.api.Tag("core")
class SUtilStoragePropertyMemoTest {
   private static String read(String name) throws Exception {
      Method m = SUtil.class.getDeclaredMethod("getPropertyBypassingCache", String.class);
      m.setAccessible(true);
      return (String) m.invoke(null, name);
   }

   @Test
   void oneStorageReadPerKeyInsideScope() throws Exception {
      AtomicInteger reads = new AtomicInteger();

      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
          PermissionRequestScope.Handle scope = PermissionRequestScope.open())
      {
         env.when(() -> SreeEnv.getPropertyFromStorage("a")).thenAnswer(inv -> {
            reads.incrementAndGet();
            return "true";
         });

         for(int i = 0; i < 10; i++) {
            assertEquals("true", read("a"));
         }

         assertEquals(1, reads.get());
      }
   }

   @Test
   void unchangedOutsideScope() throws Exception {
      AtomicInteger reads = new AtomicInteger();

      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class)) {
         env.when(() -> SreeEnv.getPropertyFromStorage("a")).thenAnswer(inv -> {
            reads.incrementAndGet();
            return "true";
         });

         for(int i = 0; i < 5; i++) {
            read("a");
         }

         assertEquals(5, reads.get());
      }
   }

   @Test
   void rereadAfterScopeCloses() throws Exception {
      AtomicInteger reads = new AtomicInteger();

      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class)) {
         env.when(() -> SreeEnv.getPropertyFromStorage("a")).thenAnswer(inv -> {
            return String.valueOf(reads.incrementAndGet());
         });

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            assertEquals("1", read("a"));
            assertEquals("1", read("a"));
         }

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            assertEquals("2", read("a"));
         }
      }
   }

   @Test
   void storageExceptionIsNotMemoized() throws Exception {
      AtomicInteger reads = new AtomicInteger();

      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
          PermissionRequestScope.Handle scope = PermissionRequestScope.open())
      {
         env.when(() -> SreeEnv.getPropertyFromStorage("a")).thenAnswer(inv -> {
            if(reads.incrementAndGet() == 1) {
               throw new IllegalStateException("blip");
            }

            return "fresh";
         });
         env.when(() -> SreeEnv.getProperty("a", "false")).thenReturn("stale");

         assertEquals("stale", read("a"));
         assertEquals("fresh", read("a"));
         assertEquals("fresh", read("a"));
         assertEquals(2, reads.get());
      }
   }

   @Test
   void orgQualifiedKeysAreDistinct() throws Exception {
      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
          PermissionRequestScope.Handle scope = PermissionRequestScope.open())
      {
         env.when(() -> SreeEnv.getPropertyFromStorage("security.o1.exposeDefaultOrgToAll"))
            .thenReturn("true");
         env.when(() -> SreeEnv.getPropertyFromStorage("security.o2.exposeDefaultOrgToAll"))
            .thenReturn("false");

         assertEquals("true", read("security.o1.exposeDefaultOrgToAll"));
         assertEquals("false", read("security.o2.exposeDefaultOrgToAll"));
         assertEquals("true", read("security.o1.exposeDefaultOrgToAll"));
      }
   }
}
