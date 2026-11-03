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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80092: set_worksheet_properties wrote alias/description only to the in-memory
 * WorksheetInfo, which is not persisted. The toolbar Save persists rws.getEntry() unchanged, so
 * the values were lost on reopen. The entry is the durable store, so setProperties must write it
 * too, as WorksheetPropertyDialogService does.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetPropertiesEntry80092Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @Test
   void aliasAndDescriptionAreWrittenToTheSessionEntry() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "ws80092", null);
      Worksheet ws = new Worksheet();

      controller(ws, entry).setProperties("TOK", new WorksheetAgentController.WorksheetPropertiesRequest("A1", "D1"), agent);

      assertEquals("A1", ws.getWorksheetInfo().getAlias());
      assertEquals("A1", entry.getAlias());
      assertEquals("D1", entry.getProperty("description"));
   }

   @Test
   void anOmittedFieldLeavesTheEntryValueAlone() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "ws80092b", null);
      entry.setAlias("Old");
      entry.setProperty("description", "OldD");

      controller(new Worksheet(), entry)
         .setProperties("TOK", new WorksheetAgentController.WorksheetPropertiesRequest(null, "D2"), agent);

      assertEquals("Old", entry.getAlias());
      assertEquals("D2", entry.getProperty("description"));
   }

   private WorksheetAgentController controller(Worksheet ws, AssetEntry src) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getEntry()).thenReturn(src);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      WorksheetEditService edit = new WorksheetEditService(
         sessions, runtimeAccess, mock(SheetAgentBroadcastService.class), securityEngine,
         new InnerJoinService(null, null));

      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      return new WorksheetAgentController(feature, mock(SheetJoinService.class), sessions,
         mock(WorksheetReadService.class), edit, mock(WorksheetService.class),
         mock(WorksheetPreviewService.class), mock(SheetAgentBroadcastService.class),
         mock(inetsoft.uql.XRepository.class), mock(AssetRepository.class),
         mock(inetsoft.web.wiz.service.MetadataApiService.class),
         mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
         mock(inetsoft.web.composer.ws.LayoutGraphService.class),
         mock(inetsoft.web.portal.controller.database.DataSourceService.class),
         securityEngine, mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));
   }
}
