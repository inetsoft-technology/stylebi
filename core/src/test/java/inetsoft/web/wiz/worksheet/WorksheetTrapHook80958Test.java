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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.web.WebConfig;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80958: the warn-only trap check is wired into exactly the four worksheet ops Composer's
 * dialogs guard, its result reaches {@code EditResponse.note}, the write is applied either way,
 * and the script-pane entry point ({@code editOp} without holders) never runs it.
 */
@WizAgentTestSupport
class WorksheetTrapHook80958Test {
   private static final String WARNING = "Data-model trap: Fan Trap is on Orders, Customer. "
      + "The write was applied; use undo to revert it.";
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final ObjectMapper mapper = new WebConfig().objectMapper();

   // ------------------------------------------------------------------- the four hooked ops

   @Test
   void addFilterWarnsAndStillApplies() throws Exception {
      assertWarns("{\"op\":\"add_filter\",\"table\":\"T\",\"field\":\"a\",\"operation\":\"=\","
                  + "\"values\":[\"x\"]}", false);
   }

   @Test
   void editConditionWarnsAndStillApplies() throws Exception {
      assertWarns("{\"op\":\"edit_condition\",\"table\":\"T\",\"field\":\"a\",\"operation\":\"=\","
                  + "\"values\":[\"x\"]}", false);
   }

   @Test
   void setConditionsWarnsAndStillApplies() throws Exception {
      assertWarns("{\"op\":\"set_conditions\",\"table\":\"T\",\"conditions\":[{\"level\":0,"
                  + "\"condition\":{\"field\":\"a\",\"operation\":\"=\",\"values\":[\"x\"]}}]}",
                  false);
   }

   @Test
   void setGroupAggregateWarnsAndStillAppliesWithTheAggregateContext() throws Exception {
      assertWarns("{\"op\":\"set_group_aggregate\",\"table\":\"T\",\"groups\":[{\"field\":\"a\"}],"
                  + "\"aggregates\":[{\"field\":\"b\",\"formula\":\"Sum\"}]}", true);
   }

   @Test
   void noTrapKeepsTheNoContentResponse() throws Exception {
      Fixture f = fixture();

      try(MockedStatic<WorksheetTrapCheck> check = Mockito.mockStatic(WorksheetTrapCheck.class)) {
         check.when(() -> WorksheetTrapCheck.newTrap(any(), any(), anyBoolean(), any()))
            .thenReturn(Optional.empty());

         ResponseEntity<EditResponse> response = f.controller.edit(
            "TOK", request("{\"op\":\"add_filter\",\"table\":\"T\",\"field\":\"a\","
                           + "\"operation\":\"=\",\"values\":[\"x\"]}"), agent);

         assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
         assertTrue(f.table.getPreConditionList().getConditionSize() > 0, "the write applied");
      }
   }

   @Test
   void theCheckSeesTheTableBeforeAndAfterTheWrite() throws Exception {
      Fixture f = fixture();

      try(MockedStatic<WorksheetTrapCheck> check = Mockito.mockStatic(WorksheetTrapCheck.class)) {
         check.when(() -> WorksheetTrapCheck.newTrap(any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               TableAssembly before = inv.getArgument(0);
               TableAssembly after = inv.getArgument(1);
               assertNotSame(f.table, before, "the before state is a snapshot, not the live table");
               assertEquals(0, before.getPreConditionList() == null ? 0
                               : before.getPreConditionList().getConditionSize());
               assertTrue(after.getPreConditionList().getConditionSize() > 0);
               return Optional.of(WARNING);
            });

         ResponseEntity<EditResponse> response = f.controller.edit(
            "TOK", request("{\"op\":\"add_filter\",\"table\":\"T\",\"field\":\"a\","
                           + "\"operation\":\"=\",\"values\":[\"x\"]}"), agent);

         assertEquals(WARNING, response.getBody().note());
      }
   }

   // ------------------------------------------------------------------- everything else stays silent

   @Test
   void otherOpsNeverRunTheCheck() throws Exception {
      for(String json : List.of(
         "{\"op\":\"remove_filter\",\"table\":\"T\",\"field\":\"a\"}",
         "{\"op\":\"set_post_conditions\",\"table\":\"T\",\"conditions\":[]}",
         "{\"op\":\"set_sort\",\"table\":\"T\",\"field\":\"a\",\"direction\":\"ASC\"}",
         "{\"op\":\"remove_column\",\"table\":\"T\",\"column\":\"b\",\"confirmed\":true}"))
      {
         Fixture f = fixture();

         try(MockedStatic<WorksheetTrapCheck> check =
                Mockito.mockStatic(WorksheetTrapCheck.class))
         {
            f.controller.edit("TOK", request(json), agent);

            check.verifyNoInteractions();
         }
      }
   }

   @Test
   void aRefusedWriteThrowsBeforeTheCheckRuns() throws Exception {
      Fixture f = fixture();

      try(MockedStatic<WorksheetTrapCheck> check = Mockito.mockStatic(WorksheetTrapCheck.class)) {
         assertThrows(PairingException.class, () -> f.controller.edit(
            "TOK", request("{\"op\":\"add_filter\",\"table\":\"NOPE\",\"field\":\"a\","
                           + "\"operation\":\"=\",\"values\":[\"x\"]}"), agent));

         check.verifyNoInteractions();
      }
   }

   @Test
   void theScriptPaneEntryPointNeverRunsTheCheck() throws Exception {
      Fixture f = fixture();

      try(MockedStatic<WorksheetTrapCheck> check = Mockito.mockStatic(WorksheetTrapCheck.class)) {
         f.controller.editOp("TOK", request(
            "{\"op\":\"add_filter\",\"table\":\"T\",\"field\":\"a\",\"operation\":\"=\","
            + "\"values\":[\"x\"]}"), agent);

         check.verifyNoInteractions();
         assertTrue(f.table.getPreConditionList().getConditionSize() > 0, "the write applied");
      }
   }

   /** Only the four hooked op names appear in the controller's trap-op sets. */
   @Test
   void theHookedOpSetIsExactlyTheFourOps() throws Exception {
      Path classes = Path.of(WorksheetAgentController.class.getProtectionDomain().getCodeSource()
                                .getLocation().toURI());
      Path source = classes.getParent().getParent().resolve(
         "src/main/java/inetsoft/web/wiz/worksheet/WorksheetAgentController.java");

      String text = Files.readString(source);
      Matcher aggregate = Pattern.compile("AGGREGATE_TRAP_OP = \"([a-z_]+)\"").matcher(text);
      Matcher conditions = Pattern.compile(
         "CONDITION_TRAP_OPS =\\s*Set\\.of\\(([^)]*)\\)").matcher(text);

      assertTrue(aggregate.find());
      assertEquals("set_group_aggregate", aggregate.group(1));
      assertTrue(conditions.find());
      assertEquals("\"add_filter\", \"edit_condition\", \"set_conditions\"",
                   conditions.group(1).trim());
      assertEquals(1, text.split("WorksheetTrapCheck\\.newTrap\\(", -1).length - 1,
                   "exactly one call site");
   }

   // ------------------------------------------------------------------- fixtures

   private void assertWarns(String json, boolean aggregate) throws Exception {
      Fixture f = fixture();

      try(MockedStatic<WorksheetTrapCheck> check = Mockito.mockStatic(WorksheetTrapCheck.class)) {
         check.when(() -> WorksheetTrapCheck.newTrap(any(), any(), anyBoolean(), any()))
            .thenReturn(Optional.of(WARNING));

         ResponseEntity<EditResponse> response = f.controller.edit("TOK", request(json), agent);

         assertEquals(HttpStatus.OK, response.getStatusCode());
         assertEquals(WARNING, response.getBody().note());
         check.verify(() -> WorksheetTrapCheck.newTrap(any(), any(), eq(aggregate), any()));

         if(aggregate) {
            assertTrue(f.table.isAggregate(), "the write applied");
         }
         else {
            assertTrue(f.table.getPreConditionList().getConditionSize() > 0, "the write applied");
         }
      }
   }

   private EditRequest request(String json) throws Exception {
      return mapper.readValue(json, EditRequest.class);
   }

   private record Fixture(WorksheetAgentController controller, BoundTableAssembly table) {
   }

   private Fixture fixture() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = new BoundTableAssembly(ws, "T");
      t.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds", "tbl"));
      ColumnSelection cs = new ColumnSelection();
      ColumnRef a = new ColumnRef(new AttributeRef(null, "a"));
      a.setDataType("string");
      ColumnRef b = new ColumnRef(new AttributeRef(null, "b"));
      b.setDataType("double");
      cs.addAttribute(a);
      cs.addAttribute(b);
      t.setColumnSelection(cs);
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(
         new JoinSession("TOK", "Worksheet/ws-1", "alice~;~host-org", SheetType.WORKSHEET, 0L,
                         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, null));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);
      WorksheetEditService edit = new WorksheetEditService(
         sessions, runtimeAccess, mock(SheetAgentBroadcastService.class),
         mock(SecurityEngine.class), mock(InnerJoinService.class));
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      WorksheetAgentController controller = new WorksheetAgentController(
         feature, mock(SheetJoinService.class), sessions, mock(WorksheetReadService.class), edit,
         mock(WorksheetService.class), mock(WorksheetPreviewService.class),
         mock(SheetAgentBroadcastService.class), mock(inetsoft.uql.XRepository.class),
         mock(inetsoft.uql.asset.AssetRepository.class),
         mock(inetsoft.web.wiz.service.MetadataApiService.class),
         mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
         mock(inetsoft.web.composer.ws.LayoutGraphService.class),
         mock(inetsoft.web.portal.controller.database.DataSourceService.class),
         mock(SecurityEngine.class), mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));
      return new Fixture(controller, t);
   }
}
