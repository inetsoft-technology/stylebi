/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.viewsheet;

/*
 * Test strategy
 *
 * ViewsheetMonitorController has:
 *
 *   Two STOMP subscribers (subscribeToExecuting, subscribeToOpen) — each with an inline
 *     permission check. Permission denied → SecurityException; granted → addSubscriber called.
 *
 *   REST POST /api/em/monitoring/viewsheets/remove/** (closeViewsheets) — delegates to
 *     viewsheetService.destroyClusterNodeViewsheets(address, ids). The controller explicitly
 *     swallows IllegalArgumentException and ExpiredSheetException (viewsheet already gone);
 *     other exceptions propagate.
 *
 * Behavioral guarantees covered:
 *
 * [G1] STOMP subscribeToExecuting: permission denied → SecurityException.
 * [G2] STOMP subscribeToOpen: permission denied → SecurityException.
 * [G3] closeViewsheets delegates ids and resolved address to destroyClusterNodeViewsheets.
 * [G4] closeViewsheets swallows IllegalArgumentException (viewsheet already removed).
 * [G5] closeViewsheets swallows ExpiredSheetException (viewsheet already expired).
 */

import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ViewsheetMonitorControllerTest {

   @Mock private ViewsheetService viewsheetService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;

   private ViewsheetMonitorController controller;

   @BeforeEach
   void setUp() {
      controller = new ViewsheetMonitorController(
         viewsheetService, monitoringDataService, securityEngine);
   }

   private void stubPermission(String resource, boolean granted) {
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq(resource), eq(ResourceAction.ACCESS)))
         .thenReturn(granted);
   }

   // [G1] executing-viewsheets subscribe: permission denied → SecurityException
   @Test
   void subscribeToExecuting_permissionDenied_throwsSecurityException() {
      stubPermission("monitoring/viewsheets/executing", false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeToExecuting(stompHeaderAccessor, Optional.empty(), principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G2] open-viewsheets subscribe: permission denied → SecurityException
   @Test
   void subscribeToOpen_permissionDenied_throwsSecurityException() {
      stubPermission("monitoring/viewsheets/open", false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeToOpen(stompHeaderAccessor, Optional.empty(), principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G3] closeViewsheets with blank server → null address; delegates to service
   @Test
   void closeViewsheets_blankServer_passesNullAddressToService() throws Exception {
      String[] ids = { "vs1", "vs2" };

      controller.closeViewsheets("/", ids);

      verify(viewsheetService).destroyClusterNodeViewsheets(isNull(), eq(ids));
   }

   // [G4] IllegalArgumentException (viewsheet already gone) is silently swallowed
   @Test
   void closeViewsheets_illegalArgument_swallowed() throws Exception {
      doThrow(new IllegalArgumentException("not found"))
         .when(viewsheetService).destroyClusterNodeViewsheets(any(), any());

      assertDoesNotThrow(() -> controller.closeViewsheets("/", new String[]{ "vs1" }));
   }

   // [G5] ExpiredSheetException (viewsheet already expired) is silently swallowed
   @Test
   void closeViewsheets_expiredSheetException_swallowed() throws Exception {
      doThrow(new ExpiredSheetException("vs1", null))
         .when(viewsheetService).destroyClusterNodeViewsheets(any(), any());

      assertDoesNotThrow(() -> controller.closeViewsheets("/", new String[]{ "vs1" }));
   }
}
