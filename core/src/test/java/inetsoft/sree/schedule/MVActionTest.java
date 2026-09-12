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
package inetsoft.sree.schedule;

import inetsoft.mv.MVDef;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@code MVAction}'s cluster-scheduled MV creation honors {@code schedule.task.timeout}:
 * falls back to 10 minutes when the property is missing/invalid, actually enforces the timeout
 * (cancelling the future and wrapping the {@link TimeoutException}) when it elapses, and falls
 * back to an unbounded wait when the property is zero/negative -- all without ever leaking a
 * {@link NumberFormatException} or blocking the test for a real timeout duration.
 */
@Tag("core")
class MVActionTest {
   @Test
   void defaultsToTenMinutesWhenTimeoutPropertyIsMissing() throws Throwable {
      Future<String> future = mockFuture();
      when(future.get(600000L, TimeUnit.MILLISECONDS)).thenReturn(null);

      invokeCreateMV(null, future);

      verify(future).get(600000L, TimeUnit.MILLISECONDS);
   }

   @ParameterizedTest(name = "invalid timeout property [{0}] defaults to 600000ms")
   @ValueSource(strings = { "not-a-number", "" })
   void defaultsToTenMinutesWhenTimeoutPropertyIsInvalid(String propertyValue) throws Throwable {
      Future<String> future = mockFuture();
      when(future.get(600000L, TimeUnit.MILLISECONDS)).thenReturn(null);

      invokeCreateMV(propertyValue, future);

      verify(future).get(600000L, TimeUnit.MILLISECONDS);
   }

   @Test
   void cancelsTheFutureAndWrapsTimeoutExceptionWhenTimeoutElapses() throws Throwable {
      Future<String> future = mockFuture();
      TimeoutException timeoutException = new TimeoutException("simulated timeout");
      when(future.get(100L, TimeUnit.MILLISECONDS)).thenThrow(timeoutException);

      Throwable thrown = assertThrows(
         RuntimeException.class, () -> invokeCreateMV("100", future));

      assertSame(timeoutException, thrown.getCause());
      verify(future).cancel(true);
   }

   @ParameterizedTest(name = "timeout property [{0}] falls back to unbounded wait")
   @ValueSource(strings = { "0", "-5" })
   void waitsWithoutATimeoutWhenTimeoutPropertyIsNotPositive(String propertyValue)
      throws Throwable
   {
      Future<String> future = mockFuture();
      when(future.get()).thenReturn(null);

      invokeCreateMV(propertyValue, future);

      verify(future).get();
      verify(future, never()).get(anyLong(), any());
   }

   @SuppressWarnings("unchecked")
   private static Future<String> mockFuture() {
      return mock(Future.class);
   }

   private static void invokeCreateMV(String timeoutProperty, Future<String> future)
      throws Throwable
   {
      MVDef mv = mock(MVDef.class);
      when(mv.getName()).thenReturn("mv1");
      when(mv.clone()).thenReturn(mv);
      when(mv.hasData()).thenReturn(false);

      MVAction action = new MVAction(mv);

      try(MockedStatic<SreeEnv> sreeEnvStatic = mockStatic(SreeEnv.class);
          MockedStatic<Cluster> clusterStatic = mockStatic(Cluster.class))
      {
         sreeEnvStatic.when(() -> SreeEnv.getProperty("schedule.task.timeout"))
            .thenReturn(timeoutProperty);

         Cluster cluster = mock(Cluster.class);
         when(cluster.isSchedulerRunning()).thenReturn(true);
         when(cluster.<String>submit(any(), eq(true))).thenReturn(future);
         clusterStatic.when(Cluster::getInstance).thenReturn(cluster);

         Method method =
            MVAction.class.getDeclaredMethod("createMV", Principal.class, MVDef.class);
         method.setAccessible(true);

         try {
            method.invoke(action, (Principal) null, mv);
         }
         catch(InvocationTargetException e) {
            throw e.getCause();
         }
      }
   }
}
