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
package inetsoft.web.admin.ai.schedule;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.ScheduleTaskMetaData;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A task given a time zone through the AI gateway shows the same value in the portal Options tab
 * model, which {@code ScheduleTaskService} builds from the same stored task field.
 */
@Tag("core")
class ScheduleTaskTimeZonePortalParityTest {
   @Test
   void aTaskZoneSavedThroughTheGatewayIsTheValueTheOptionsTabModelShows() throws Exception {
      AnalyticRepository repository =
         mock(AnalyticRepository.class, withSettings().lenient());
      ScheduleManager scheduleManager =
         mock(ScheduleManager.class, withSettings().lenient());
      SecurityEngine securityEngine =
         mock(SecurityEngine.class, withSettings().lenient());
      SecurityProvider securityProvider =
         mock(SecurityProvider.class, withSettings().lenient());
      Principal user = mock(Principal.class, withSettings().lenient());
      IdentityID owner = new IdentityID("caller", "host-org");
      when(user.getName()).thenReturn(owner.convertToKey());
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
      when(repository.getScheduleTask(anyString())).thenReturn(null);

      ScheduleTaskService portalService = new ScheduleTaskService(
         repository, scheduleManager, mock(ScheduleService.class), null, securityProvider, null,
         null);
      AdminScheduleGateway gateway = new AdminScheduleGateway(
         repository, scheduleManager, mock(ScheduleService.class), null,
         mock(ScheduleTaskService.class, withSettings().lenient()), securityEngine);

      ScheduleTaskMetaData meta = new ScheduleTaskMetaData("zoned", owner.convertToKey());
      gateway.addScheduleTask(meta, true, false, -1, -1, null, null, null,
                              Collections.emptyList(), Collections.emptyList(), null, "", null,
                              "Asia/Tokyo", user);
      ArgumentCaptor<ScheduleTask> saved = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq(meta.getTaskId()), saved.capture(), eq(user));
      when(scheduleManager.getScheduleTask(meta.getTaskId()))
         .thenReturn(ScheduleChangesetApplyService.deepCopy(saved.getValue()));

      try(MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class,
             withSettings().lenient());
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class, withSettings().lenient()))
      {
         env.when(() -> SreeEnv.getProperty("locale.available")).thenReturn("");
         sutil.when(SUtil::loadLocaleProperties).thenReturn(new Properties());
         Method getTaskOptions = ScheduleTaskService.class
            .getDeclaredMethod("getTaskOptions", String.class, Principal.class);
         getTaskOptions.setAccessible(true);

         TaskOptionsPaneModel model =
            (TaskOptionsPaneModel) getTaskOptions.invoke(portalService, meta.getTaskId(), user);

         assertEquals("Asia/Tokyo", model.timeZone());
      }
   }
}
