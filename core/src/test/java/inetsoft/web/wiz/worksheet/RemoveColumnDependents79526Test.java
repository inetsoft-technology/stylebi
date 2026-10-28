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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.WebConfig;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79526: remove_column on a SAVED worksheet is refused, naming the dependent, when a saved
 * viewsheet uses the column and the call is not confirmed. Runs against the real asset
 * repository and dependency index, as {@code MirrorResyncGuard78814Test} does; the dependent's
 * only use of the column is a condition (the reporter's case).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  RemoveColumnDependents79526Test.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class RemoveColumnDependents79526Test {
   private static int count = 0;
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final ObjectMapper mapper = new WebConfig().objectMapper();

   @Test
   void unconfirmedRemovalOfAConditionOnlyColumnIsRefusedNamingTheDependent() throws Exception {
      Fixture f = fixture(AssetRepository.GLOBAL_SCOPE);

      PairingException ex = assertThrows(PairingException.class,
         () -> f.controller().edit("TOK", request("STATE", false), agent));

      assertTrue(ex.getMessage().contains("'STATE'"), ex.getMessage());
      assertTrue(ex.getMessage().contains(f.dependent().getName()), ex.getMessage());
      assertTrue(ex.getMessage().contains("Nothing was changed"), ex.getMessage());
      assertTrue(ex.getMessage().endsWith("confirmed:true."), ex.getMessage());
      assertFalse(ex.getMessage().contains("Proceed?"), ex.getMessage());
      assertNotNull(f.table().getColumnSelection(false).getAttribute("STATE"),
                    "a refused removal must leave the column in place");
   }

   @Test
   void confirmedRemovalProceeds() throws Exception {
      Fixture f = fixture(AssetRepository.GLOBAL_SCOPE);

      f.controller().edit("TOK", request("STATE", true), agent);

      assertNull(f.table().getColumnSelection(false).getAttribute("STATE"));
   }

   @Test
   void aColumnNoDependentUsesIsNotRefused() throws Exception {
      Fixture f = fixture(AssetRepository.GLOBAL_SCOPE);

      f.controller().edit("TOK", request("CITY", false), agent);

      assertNull(f.table().getColumnSelection(false).getAttribute("CITY"));
      assertNotNull(f.table().getColumnSelection(false).getAttribute("STATE"));
   }

   @Test
   void aDependentBoundToAnotherTableWithTheSameColumnDoesNotBlockRemoval() throws Exception {
      Fixture f = fixture(AssetRepository.GLOBAL_SCOPE, "U");

      f.controller().edit("TOK", request("STATE", false), agent);

      assertNull(f.table().getColumnSelection(false).getAttribute("STATE"));
   }

   @Test
   void anUnsavedWorksheetIsNotScanned() throws Exception {
      Fixture f = fixture(AssetRepository.TEMPORARY_SCOPE);

      f.controller().edit("TOK", request("STATE", false), agent);

      assertNull(f.table().getColumnSelection(false).getAttribute("STATE"));
   }

   @Test
   void anUnknownColumnKeepsTheEditorsOwnError() throws Exception {
      Fixture f = fixture(AssetRepository.GLOBAL_SCOPE);

      PairingException ex = assertThrows(PairingException.class,
         () -> f.controller().edit("TOK", request("NOPE", false), agent));

      assertTrue(ex.getMessage().contains("Column not found"), ex.getMessage());
   }

   // ---------------------------------------------------------------------------

   private record Fixture(WorksheetAgentController controller, EmbeddedTableAssembly table,
                          AssetEntry dependent) {}

   private Fixture fixture(int scope) throws Exception {
      return fixture(scope, "T");
   }

   private Fixture fixture(int scope, String boundTable) throws Exception {
      int n = ++count;
      AssetEntry src = new AssetEntry(scope, AssetEntry.Type.WORKSHEET, "rcd79526_ws" + n, null);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "CITY", "STATE");
      ws.addAssembly(t);
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "U", "CITY", "STATE"));

      AssetEntry dep = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                      "rcd79526_vs" + n, null);

      if(scope != AssetRepository.TEMPORARY_SCOPE) {
         AssetUtil.getAssetRepository(false).setSheet(src, ws, null, true);
         Viewsheet vs = new Viewsheet(src);
         TableVSAssembly table = new TableVSAssembly(vs, "CustTable");
         table.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, boundTable));
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(new ColumnRef(new AttributeRef(null, "CITY")));
         ((TableVSAssemblyInfo) table.getInfo()).setColumnSelection(cols);
         Condition cond = new Condition();
         cond.setOperation(XCondition.EQUAL_TO);
         cond.setType(XSchema.STRING);
         cond.addValue("NY");
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "STATE")), cond, 0));
         ((TableVSAssemblyInfo) table.getInfo()).setPreConditionList(list);
         vs.addAssembly(table);
         AssetUtil.getAssetRepository(false).setSheet(dep, vs, null, true);
      }

      return new Fixture(controller(ws, src), t, dep);
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

   private EditRequest request(String column, boolean confirmed) throws Exception {
      return mapper.readValue("{\"op\":\"remove_column\",\"table\":\"T\",\"column\":\"" + column +
                              "\",\"confirmed\":" + confirmed + "}", EditRequest.class);
   }

   @Configuration
   static class Beans {
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }
   }
}
