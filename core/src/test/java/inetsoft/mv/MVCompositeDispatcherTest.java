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
import inetsoft.util.CancelledException;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

   private MVCompositeDispatcher newDispatcher() {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");
      return spy(new MVCompositeDispatcher(def, new VariableTable(), mock(Principal.class)));
   }

   /**
    * Runs the dispatcher the way the build does (a worker thread that swallows whatever
    * escapes run(), like ThreadPool does) and waits like MVDispatcher.processDispatch.
    */
   private Exception runAndWait(MVCompositeDispatcher dispatcher) {
      Thread worker = new Thread(() -> {
         try {
            dispatcher.run();
         }
         catch(Throwable ignore) {
            // the thread pool only logs it
         }
      });
      worker.start();
      assertTrue(dispatcher.isCompleted());
      return dispatcher.getException();
   }

   @Test
   void errorFromDispatchIsReportedAsException() throws Exception {
      MVCompositeDispatcher dispatcher = newDispatcher();
      OutOfMemoryError oom = new OutOfMemoryError("boom");
      doThrow(oom).when(dispatcher).dispatch0();

      Exception ex = runAndWait(dispatcher);

      assertNotNull(ex);
      assertSame(oom, ex.getCause());
   }

   @Test
   void runRethrowsErrorAfterRecordingIt() throws Exception {
      MVCompositeDispatcher dispatcher = newDispatcher();
      doThrow(new OutOfMemoryError("boom")).when(dispatcher).dispatch0();

      assertThrows(OutOfMemoryError.class, dispatcher::run);
      assertNotNull(dispatcher.getException());
      assertTrue(dispatcher.isCompleted());
   }

   @Test
   void exceptionFromDispatchIsReportedOnceCompleted() throws Exception {
      MVCompositeDispatcher dispatcher = newDispatcher();
      Exception failure = new Exception("boom");
      doThrow(failure).when(dispatcher).dispatch0();

      assertSame(failure, runAndWait(dispatcher));
   }

   @Test
   void completedImpliesExceptionVisibleUnderRace() throws Exception {
      int iterations = 5000;
      int missed = 0;

      for(int i = 0; i < iterations; i++) {
         MVCompositeDispatcher dispatcher = newDispatcher();
         doThrow(new Exception("boom")).when(dispatcher).dispatch0();

         if(runAndWait(dispatcher) == null) {
            missed++;
         }
      }

      assertEquals(0, missed, "isCompleted() returned true before the failure was recorded");
   }

   @Test
   void cancellationIsStillRecordedAsTheCancelledException() throws Exception {
      MVCompositeDispatcher dispatcher = newDispatcher();
      CancelledException cancel = new CancelledException("cancelled");
      doThrow(cancel).when(dispatcher).dispatch0();

      assertSame(cancel, runAndWait(dispatcher));
   }

   @Test
   void successLeavesNoExceptionAndCompletes() throws Exception {
      MVCompositeDispatcher dispatcher = newDispatcher();
      doNothing().when(dispatcher).dispatch0();

      assertNull(runAndWait(dispatcher));
   }

   @Test
   void interruptWhileWaitingDoesNotEndTheWait() throws Exception {
      MVCompositeDispatcher dispatcher = newDispatcher();
      doNothing().when(dispatcher).dispatch0();
      AtomicReference<Boolean> result = new AtomicReference<>();
      Thread waiter = new Thread(() -> result.set(dispatcher.isCompleted()));
      waiter.start();
      waiter.interrupt();
      Thread.sleep(100);
      assertNull(result.get(), "an interrupt must not make isCompleted() return early");

      dispatcher.run();
      waiter.join(5000);
      assertEquals(Boolean.TRUE, result.get());
   }
}
