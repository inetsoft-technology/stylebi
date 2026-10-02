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
package inetsoft.web.admin.schedule;

/*
 * Test strategy
 *
 * EMScheduleTaskActionService.getViewsheetTableDataAssemblies opens a runtime viewsheet from
 * an asset id, reads the table assemblies through the cluster proxy and closes it again.
 *
 * Coverage scope (Bug #77058):
 *   [open as caller]     openViewsheet() receives the calling principal, never null
 *   [close by runtime]   the close goes through the cluster proxy keyed by the runtime id,
 *                        not the local ViewsheetService (this method is routed by the asset
 *                        identifier, so a local close would run on the wrong node)
 *   [close on failure]   the runtime sheet is closed even when the metadata call throws
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.AssetEntry;
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
class EMScheduleTaskActionServiceTest {
   @Mock private ScheduleTaskActionService actionService;
   @Mock private ScheduleTaskActionServiceProxy actionServiceProxy;
   @Mock private ViewsheetService viewsheetService;
   @Mock private Principal principal;

   private EMScheduleTaskActionService service;

   private static final String VS_ID = "1^128^__NULL__^Secret^orgb_id";
   private static final String RUNTIME_ID = "Secret-7";

   @BeforeEach
   void setUp() throws Exception {
      service = new EMScheduleTaskActionService(actionService, actionServiceProxy, viewsheetService);
      when(viewsheetService.openViewsheet(any(AssetEntry.class), any(), anyBoolean()))
         .thenReturn(RUNTIME_ID);
   }

   @Test
   void getViewsheetTableDataAssemblies_opensAsCallerAndClosesByRuntimeIdThroughProxy()
      throws Exception
   {
      when(actionServiceProxy.getViewsheetTableDataAssemblies(RUNTIME_ID, principal))
         .thenReturn(List.of("Table1"));

      assertEquals(List.of("Table1"), service.getViewsheetTableDataAssemblies(VS_ID, principal));

      verify(viewsheetService).openViewsheet(any(AssetEntry.class), same(principal), eq(false));
      verify(actionServiceProxy).closeViewsheet(eq(RUNTIME_ID), same(principal));
      verify(viewsheetService, never()).closeViewsheet(any(), any());
   }

   @Test
   void getViewsheetTableDataAssemblies_metadataFails_stillClosesByRuntimeId() throws Exception {
      when(actionServiceProxy.getViewsheetTableDataAssemblies(RUNTIME_ID, principal))
         .thenThrow(new IllegalStateException("boom"));

      assertThrows(IllegalStateException.class,
                   () -> service.getViewsheetTableDataAssemblies(VS_ID, principal));
      verify(actionServiceProxy).closeViewsheet(eq(RUNTIME_ID), same(principal));
   }
}
