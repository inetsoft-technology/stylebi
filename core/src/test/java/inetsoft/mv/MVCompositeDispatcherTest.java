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
package inetsoft.mv;

import inetsoft.uql.VariableTable;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link MVCompositeDispatcher#run()} always installs the principal it was
 * constructed with as the thread's context principal for the duration of {@code dispatch0()},
 * and always clears it afterward -- even when {@code dispatch0()} throws -- so a shared MV
 * build thread never leaks one tenant's identity into the next task it picks up.
 */
@Tag("core")
class MVCompositeDispatcherTest {
   @Test
   void installsAndClearsThePrincipalWhenDispatchSucceeds() throws Exception {
      Principal principal = mock(Principal.class);
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");

      MVCompositeDispatcher dispatcher =
         spy(new MVCompositeDispatcher(def, new VariableTable(), principal));
      doNothing().when(dispatcher).dispatch0();

      List<Principal> installedPrincipals = new ArrayList<>();

      try(MockedStatic<ThreadContext> threadContextStatic = mockStatic(ThreadContext.class)) {
         threadContextStatic.when(() -> ThreadContext.setContextPrincipal(any()))
            .thenAnswer(inv -> {
               installedPrincipals.add(inv.getArgument(0));
               return null;
            });

         dispatcher.run();
      }

      assertEquals(Arrays.asList(principal, null), installedPrincipals);
      assertNull(dispatcher.getException());
   }

   @Test
   void stillClearsThePrincipalWhenDispatchThrows() throws Exception {
      Principal principal = mock(Principal.class);
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");

      MVCompositeDispatcher dispatcher =
         spy(new MVCompositeDispatcher(def, new VariableTable(), principal));
      Exception dispatchFailure = new Exception("boom");
      doThrow(dispatchFailure).when(dispatcher).dispatch0();

      List<Principal> installedPrincipals = new ArrayList<>();

      try(MockedStatic<ThreadContext> threadContextStatic = mockStatic(ThreadContext.class)) {
         threadContextStatic.when(() -> ThreadContext.setContextPrincipal(any()))
            .thenAnswer(inv -> {
               installedPrincipals.add(inv.getArgument(0));
               return null;
            });

         dispatcher.run();
      }

      // the principal must still be cleared even though dispatch0() failed, so the next task
      // run on a reused worker thread doesn't inherit this tenant's identity.
      assertEquals(Arrays.asList(principal, null), installedPrincipals);
      assertSame(dispatchFailure, dispatcher.getException());
   }
}
