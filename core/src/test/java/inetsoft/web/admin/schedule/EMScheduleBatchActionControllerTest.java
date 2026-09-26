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
 * EMScheduleBatchActionController has real in-controller logic in one method:
 *   getParameters — null-task guard + static permission check before delegation
 *
 * The remaining methods (getScheduledTasks, getQueryTree, getQueryColumns) either delegate
 * entirely to services or call AssetRepository which requires a live context; those are
 * covered by E2E tests.
 *
 * Coverage scope:
 *   [getParameters: task not found]    scheduleManager returns null → RuntimeException
 *   [getParameters: permission denied] ScheduleManager.hasTaskPermission() false → SecurityException
 *   [getParameters: open/close]        each viewsheet is opened as the caller and closed by
 *                                      runtime id (Bug #77058)
 *   [getParameters: unreadable vs]     one viewsheet the caller cannot open is skipped; the
 *                                      others and the action's own variables still returned
 *
 * ScheduleManager.hasTaskPermission() is a static method intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.admin.schedule.model.BatchParameterListModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleBatchActionControllerTest {

   @Mock private AssetRepository assetRepository;
   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleManager scheduleManager;
   @Mock private ContentRepositoryTreeService contentRepositoryTreeService;
   @Mock private SecurityEngine securityEngine;
   @Mock private ViewsheetService viewsheetService;
   @Mock private ScheduleTaskActionServiceProxy actionServiceProxy;
   @Mock private EMScheduleTaskActionServiceProxy emActionServiceProxy;
   @Mock private ScheduleTask scheduleTask;
   @Mock private Principal principal;

   private EMScheduleBatchActionController controller;

   private MockedStatic<ScheduleManager> scheduleManagerStatic;

   @BeforeEach
   void setUp() {
      controller = new EMScheduleBatchActionController(
         assetRepository, scheduleService, scheduleManager, contentRepositoryTreeService,
         securityEngine, viewsheetService, actionServiceProxy, emActionServiceProxy);

      scheduleManagerStatic = mockStatic(ScheduleManager.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      scheduleManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // getParameters()
   // -------------------------------------------------------------------------

   // [task not found] scheduleManager.getScheduleTask() returns null → RuntimeException
   @Test
   void getParameters_taskNotFound_throwsRuntimeException() {
      when(scheduleManager.getScheduleTask("missingTask")).thenReturn(null);

      assertThrows(RuntimeException.class,
         () -> controller.getParameters("missingTask", principal));
   }

   // [permission denied] ScheduleManager.hasTaskPermission() false → SecurityException
   @Test
   void getParameters_permissionDenied_throwsSecurityException() {
      IdentityID owner = new IdentityID("owner", "host-org");
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(scheduleTask);
      when(scheduleTask.getOwner()).thenReturn(owner);
      scheduleManagerStatic.when(
         () -> ScheduleManager.hasTaskPermission(eq(owner), eq(principal), eq(ResourceAction.READ)))
         .thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> controller.getParameters("myTask", principal));
   }

   private ViewsheetAction viewsheetAction(String identifier, String subject) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(identifier);
      action.setSubject(subject);
      return action;
   }

   private void stubPermittedTask(ScheduleAction... actions) {
      IdentityID owner = new IdentityID("owner", "host-org");
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(scheduleTask);
      when(scheduleTask.getOwner()).thenReturn(owner);
      when(scheduleTask.getActionCount()).thenReturn(actions.length);

      for(int i = 0; i < actions.length; i++) {
         when(scheduleTask.getAction(i)).thenReturn(actions[i]);
      }

      scheduleManagerStatic.when(
         () -> ScheduleManager.hasTaskPermission(eq(owner), eq(principal), eq(ResourceAction.READ)))
         .thenReturn(true);
   }

   // [open/close] Bug #77058: open as the caller and close by runtime id
   @Test
   void getParameters_opensAsCallerAndClosesByRuntimeId() throws Exception {
      String vsId = "1^128^__NULL__^Sales^host-org";
      stubPermittedTask(viewsheetAction(vsId, "Report $(region)"));
      when(viewsheetService.openViewsheet(any(AssetEntry.class), any(), anyBoolean()))
         .thenReturn("Sales-1");
      when(actionServiceProxy.getViewsheetParameters("Sales-1", principal))
         .thenReturn(List.of("year"));

      BatchParameterListModel result = controller.getParameters("myTask", principal);

      assertEquals(List.of("year", "region"), List.copyOf(result.parameterNames()));
      verify(viewsheetService).openViewsheet(any(AssetEntry.class), same(principal), eq(false));
      verify(emActionServiceProxy).closeViewsheet(eq("Sales-1"), same(principal));
      verify(emActionServiceProxy, never()).closeViewsheet(eq(vsId), any());
   }

   // [unreadable vs] one viewsheet the caller cannot open must not fail the whole request
   @Test
   void getParameters_unreadableViewsheet_isSkipped() throws Exception {
      String deniedId = "1^128^__NULL__^Secret^host-org";
      String okId = "1^128^__NULL__^Sales^host-org";
      stubPermittedTask(viewsheetAction(deniedId, "Denied $(deniedVar)"),
                        viewsheetAction(okId, null));
      when(viewsheetService.openViewsheet(
         argThat(e -> e != null && "Secret".equals(e.getPath())), any(), anyBoolean()))
         .thenThrow(new inetsoft.util.MessageException("denied"));
      when(viewsheetService.openViewsheet(
         argThat(e -> e != null && "Sales".equals(e.getPath())), any(), anyBoolean()))
         .thenReturn("Sales-1");
      when(actionServiceProxy.getViewsheetParameters("Sales-1", principal))
         .thenReturn(List.of("year"));

      BatchParameterListModel result = controller.getParameters("myTask", principal);

      assertEquals(List.of("deniedVar", "year"), List.copyOf(result.parameterNames()));
      verify(emActionServiceProxy).closeViewsheet(eq("Sales-1"), same(principal));
      verify(emActionServiceProxy, times(1)).closeViewsheet(any(), any());
   }
}
