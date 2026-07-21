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
package inetsoft.mv.mr.internal;

import inetsoft.mv.fs.XBlockSystem;
import inetsoft.mv.mr.XJobPool;
import inetsoft.mv.mr.XMapFailure;
import inetsoft.mv.mr.XMapResult;
import inetsoft.mv.mr.XMapTask;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code XMapTaskPool.Handler} attributes a task's result/failure to that task's
 * own organization id (computed per task via {@link XMapTask#getOrgID()}), rather than an org id
 * cached once for the shared worker thread that runs it -- the bug fixed by computing the org id
 * per task instead of once per thread.
 */
@Tag("core")
class XMapTaskPoolTest {
   @Test
   void routesSuccessfulResultToTheTasksOwnOrganization() throws Exception {
      XBlockSystem sys = mock(XBlockSystem.class);
      XMapResult result = mock(XMapResult.class);
      XMapTask task = mock(XMapTask.class);
      when(task.getOrgID()).thenReturn("orgA");
      when(task.run(same(sys))).thenReturn(result);

      try(MockedStatic<XJobPool> jobPoolStatic = mockStatic(XJobPool.class)) {
         newHandler(task, sys).run();

         jobPoolStatic.verify(() -> XJobPool.addResult(same(result), eq("orgA")));
      }
   }

   @Test
   void routesFailureToTheTasksOwnOrganization() throws Exception {
      XBlockSystem sys = mock(XBlockSystem.class);
      XMapTask task = mock(XMapTask.class);
      when(task.getOrgID()).thenReturn("orgB");
      when(task.run(same(sys))).thenThrow(new RuntimeException("boom"));

      try(MockedStatic<XJobPool> jobPoolStatic = mockStatic(XJobPool.class)) {
         newHandler(task, sys).run();

         jobPoolStatic.verify(() -> XJobPool.addFailure(
            org.mockito.ArgumentMatchers.argThat(f -> f.getReason().equals("boom")), eq("orgB")));
      }
   }

   /**
    * Runs two tasks belonging to different organizations through independent {@code Handler}
    * instances (as {@code XMapTaskPool.add0()} creates one per task) and confirms neither task's
    * result is misattributed to the other task's organization, regardless of execution order.
    */
   @Test
   void doesNotCrossAttributeResultsBetweenTasksFromDifferentOrganizations() throws Exception {
      XBlockSystem sys = mock(XBlockSystem.class);
      XMapResult resultA = mock(XMapResult.class);
      XMapResult resultB = mock(XMapResult.class);
      XMapTask taskA = mock(XMapTask.class);
      XMapTask taskB = mock(XMapTask.class);
      when(taskA.getOrgID()).thenReturn("orgA");
      when(taskB.getOrgID()).thenReturn("orgB");
      when(taskA.run(same(sys))).thenReturn(resultA);
      when(taskB.run(same(sys))).thenReturn(resultB);

      try(MockedStatic<XJobPool> jobPoolStatic = mockStatic(XJobPool.class)) {
         // task B (submitted later) finishes first, as would happen if a shared worker thread
         // picked up a second organization's task before the first one completed.
         newHandler(taskB, sys).run();
         newHandler(taskA, sys).run();

         jobPoolStatic.verify(() -> XJobPool.addResult(same(resultB), eq("orgB")));
         jobPoolStatic.verify(() -> XJobPool.addResult(same(resultA), eq("orgA")));
      }
   }

   private static Runnable newHandler(XMapTask task, XBlockSystem sys) throws Exception {
      Class<?> handlerClass = Class.forName(XMapTaskPool.class.getName() + "$Handler");
      Constructor<?> constructor =
         handlerClass.getDeclaredConstructor(XMapTask.class, XBlockSystem.class);
      constructor.setAccessible(true);
      return (Runnable) constructor.newInstance(task, sys);
   }
}
