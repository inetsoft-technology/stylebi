/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.admin.schedule;

/*
 * Test strategy
 *
 * setStatus("start") and setStatus("restart") share one stop-and-wait step: the stop request is
 * asynchronous, so SUtil.startScheduler() must not be called until scheduleClient.isRunning() is
 * false. SUtil is mocked statically; ScheduleClient is a plain mock whose isRunning() answers are
 * scripted.
 *
 * Coverage scope:
 *   [start while running]   waits for isRunning() to go false, then starts
 *   [start while stopped]   no wait, starts
 *   [start timeout]         IllegalStateException, startScheduler never called
 *   [restart]               same wait as start
 *   [stop]                  no wait, no start
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class SchedulerConfigurationServiceStatusTest {
   @Mock private ScheduleClient scheduleClient;

   private SchedulerConfigurationService service;

   @BeforeEach
   void setUp() {
      service = new SchedulerConfigurationService(scheduleClient, null, null, null);
   }

   @Test
   void startWhileRunningWaitsForStopBeforeStarting() throws Exception {
      // still running on the guard checks and on the first two polls, then gone
      when(scheduleClient.isRunning()).thenReturn(true, true, true, false, false);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         service.setStatus("start");

         sutil.verify(SUtil::stopScheduler);
         sutil.verify(SUtil::startScheduler);
         verify(scheduleClient, times(5)).isRunning();
      }
   }

   @Test
   void startDoesNotCallStartSchedulerWhileStillRunning() throws Exception {
      when(scheduleClient.isRunning()).thenReturn(true, true, false, false);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(SUtil::startScheduler).thenAnswer(inv -> {
            // by the time start is called the old process must already be gone
            assertFalse(scheduleClient.isRunning());
            return null;
         });

         service.setStatus("start");

         sutil.verify(SUtil::startScheduler);
      }
   }

   @Test
   void startWhileStoppedDoesNotWait() throws Exception {
      when(scheduleClient.isRunning()).thenReturn(false);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         long begin = System.currentTimeMillis();
         service.setStatus("start");

         assertTrue(System.currentTimeMillis() - begin < 400L);
         sutil.verify(SUtil::stopScheduler);
         sutil.verify(SUtil::startScheduler);
      }
   }

   @Test
   void startTimesOutWhenSchedulerNeverStops() throws Exception {
      when(scheduleClient.isRunning()).thenReturn(true);
      service.stopTimeoutMillis = 600L;

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         IllegalStateException e =
            assertThrows(IllegalStateException.class, () -> service.setStatus("start"));

         assertEquals("Time out waiting for scheduler to stop", e.getMessage());
         sutil.verify(SUtil::stopScheduler);
         sutil.verify(SUtil::startScheduler, never());
      }
   }

   @Test
   void restartWaitsForStopBeforeStarting() throws Exception {
      when(scheduleClient.isRunning()).thenReturn(true, true, false, false);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(SUtil::startScheduler).thenAnswer(inv -> {
            assertFalse(scheduleClient.isRunning());
            return null;
         });

         service.setStatus("restart");

         sutil.verify(SUtil::startScheduler);
      }
   }

   @Test
   void stopNeitherWaitsNorStarts() throws Exception {
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         service.setStatus("stop");

         sutil.verify(() -> SUtil.stopScheduler(true, true));
         sutil.verify(SUtil::startScheduler, never());
         verifyNoInteractions(scheduleClient);
      }
   }
}
