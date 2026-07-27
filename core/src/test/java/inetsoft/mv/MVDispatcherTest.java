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

import inetsoft.sree.SreeEnv;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link MVDispatcher}'s multi-threaded dispatch path passes the calling thread's
 * current {@link ThreadContext} principal into each {@link MVCompositeDispatcher} it constructs,
 * rather than defaulting to {@code null} -- otherwise MV blocks built on the parallel path would
 * run with no tenant/user identity attached.
 */
@Tag("core")
class MVDispatcherTest {
   @Test
   void passesTheCurrentContextPrincipalToEachCompositeDispatcherItCreates() throws Throwable {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");

      MVDispatcher dispatcher = new MVDispatcher(def);
      Principal currentPrincipal = mock(Principal.class);
      List<List<Object>> constructedWith = new ArrayList<>();

      try(MockedStatic<SreeEnv> sreeEnvStatic = mockStatic(SreeEnv.class);
          MockedStatic<ThreadContext> threadContextStatic = mockStatic(ThreadContext.class);
          MockedConstruction<MVCompositeDispatcher> construction = mockConstruction(
             MVCompositeDispatcher.class,
             (mock, context) -> {
                constructedWith.add(new ArrayList<>(context.arguments()));
                when(mock.isCompleted()).thenReturn(true);
                when(mock.getException()).thenReturn(null);
             }))
      {
         // force exactly one composite dispatcher to be created, so there's exactly one
         // constructor call to inspect.
         sreeEnvStatic.when(() -> SreeEnv.getProperty("mv.dispatcher.count")).thenReturn("1");
         threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(currentPrincipal);

         Method processDispatch =
            MVDispatcher.class.getDeclaredMethod("processDispatch", boolean.class);
         processDispatch.setAccessible(true);

         try {
            processDispatch.invoke(dispatcher, true);
         }
         catch(java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
         }
      }

      assertEquals(1, constructedWith.size());
      // constructor signature is (MVDef def, VariableTable vars, Principal principal)
      assertSame(currentPrincipal, constructedWith.get(0).get(2));
   }
}
