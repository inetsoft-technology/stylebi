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
package inetsoft.web.portal.controller;

/*
 * Test strategy
 *
 * The four viewsheet metadata endpoints (hasPrintLayout, viewsheet/highlights,
 * viewsheet/parameters, viewsheet/tableDataAssemblies) open a runtime viewsheet from a
 * client-supplied asset id, read metadata through the cluster proxy and close it again.
 *
 * Coverage scope (Bug #77058):
 *   [open as caller]     openViewsheet() receives the calling principal, never null, so the
 *                        asset READ and multi-tenant org checks are applied
 *   [close by runtime]   the proxy close is keyed by the returned runtime id, not the asset
 *                        identifier, so the runtime sheet is actually released
 *   [close on failure]   the runtime sheet is closed even when the metadata call throws
 *   [open failure]       nothing is closed when the open itself fails
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.admin.schedule.ScheduleTaskActionService;
import inetsoft.web.admin.schedule.ScheduleTaskActionServiceProxy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleTaskActionControllerTest {
   @Mock private ScheduleTaskActionService actionService;
   @Mock private ScheduleTaskActionServiceProxy actionServiceProxy;
   @Mock private ViewsheetService viewsheetService;
   @Mock private Principal principal;

   private ScheduleTaskActionController controller;

   private static final String IDENTIFIER = "1^128^__NULL__^Secret^orgb_id";
   private static final String RUNTIME_ID = "Secret-7";

   @BeforeEach
   void setUp() throws Exception {
      controller = new ScheduleTaskActionController(
         actionService, actionServiceProxy, viewsheetService);
   }

   private void stubOpen() throws Exception {
      when(viewsheetService.openViewsheet(any(AssetEntry.class), any(), anyBoolean()))
         .thenReturn(RUNTIME_ID);
   }

   private void verifyOpenAndClose() throws Exception {
      verify(viewsheetService).openViewsheet(
         argThat(e -> "orgb_id".equals(e.getOrgID()) && "Secret".equals(e.getPath())),
         same(principal), eq(false));
      verify(actionServiceProxy).closeViewsheet(eq(RUNTIME_ID), same(principal));
      verify(actionServiceProxy, never()).closeViewsheet(eq(IDENTIFIER), any());
   }

   @Test
   void hasPrintLayout_opensAsCallerAndClosesByRuntimeId() throws Exception {
      stubOpen();
      when(actionServiceProxy.hasPrintLayout(RUNTIME_ID, principal)).thenReturn(true);

      assertTrue(controller.hasPrintLayout(IDENTIFIER, principal));
      verifyOpenAndClose();
   }

   @Test
   void getViewsheetHighlights_opensAsCallerAndClosesByRuntimeId() throws Exception {
      stubOpen();
      when(actionServiceProxy.getViewsheetHighlights(RUNTIME_ID, principal)).thenReturn(List.of());

      assertEquals(List.of(), controller.getViewsheetHighlights(IDENTIFIER, principal));
      verifyOpenAndClose();
   }

   @Test
   void getViewsheetParameters_opensAsCallerAndClosesByRuntimeId() throws Exception {
      stubOpen();
      when(actionServiceProxy.getViewsheetParameters(RUNTIME_ID, principal))
         .thenReturn(List.of("p1"));

      assertEquals(List.of("p1"), controller.getViewsheetParameters(IDENTIFIER, principal));
      verifyOpenAndClose();
   }

   @Test
   void getViewsheetTableDataAssemblies_opensAsCallerAndClosesByRuntimeId() throws Exception {
      stubOpen();
      when(actionServiceProxy.getViewsheetTableDataAssemblies(RUNTIME_ID, principal))
         .thenReturn(List.of("Table1"));

      assertEquals(List.of("Table1"),
                   controller.getViewsheetTableDataAssemblies(IDENTIFIER, principal));
      verifyOpenAndClose();
   }

   @Test
   void getViewsheetParameters_metadataFails_stillClosesByRuntimeId() throws Exception {
      stubOpen();
      when(actionServiceProxy.getViewsheetParameters(RUNTIME_ID, principal))
         .thenThrow(new IllegalStateException("boom"));

      assertThrows(IllegalStateException.class,
                   () -> controller.getViewsheetParameters(IDENTIFIER, principal));
      verify(actionServiceProxy).closeViewsheet(eq(RUNTIME_ID), same(principal));
   }

   @Test
   void getViewsheetHighlights_openFails_closesNothing() throws Exception {
      when(viewsheetService.openViewsheet(any(AssetEntry.class), any(), anyBoolean()))
         .thenThrow(new SecurityException("denied"));

      assertThrows(SecurityException.class,
                   () -> controller.getViewsheetHighlights(IDENTIFIER, principal));
      verify(actionServiceProxy, never()).closeViewsheet(any(), any());
   }
}
