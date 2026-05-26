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
 *
 * ScheduleManager.hasTaskPermission() is a static method intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

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
}
