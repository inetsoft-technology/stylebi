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

package inetsoft.web.wiz.worksheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.PasteAssembliesService;
import inetsoft.web.composer.ws.PasteAssembliesServiceProxy;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSPasteAssembliesEvent;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #82348: {@code paste_assemblies} runs the Composer's worksheet paste for several assemblies
 * at once, inside a worksheet or from another worksheet the agent holds, and reports the names
 * the copies got and what it skipped.
 *
 * <p>The axes the behavior varies on, each covered below: inside one worksheet or across two;
 * copy or cut; a set whose dependents are all in it, or one that leaves a dependent or a source
 * outside it; and which source session the request names (every refusal must change neither
 * worksheet).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context the real column refresh needs
class PasteAssembliesTool82348Test {
   private static final String RT1 = "Worksheet/ws1";
   private static final String RT2 = "Worksheet/ws2";

   private final Principal agent = TestPrincipals.user("alice", "host-org");

   private Worksheet ws1;
   private Worksheet ws2;
   private SheetSessionService sessions;
   private WorksheetEditService service;

   @BeforeEach
   void setUp() throws Exception {
      ws1 = new Worksheet();
      ws2 = new Worksheet();
      table(ws1, "TA", 0, 0);
      table(ws1, "TB", 300, 0);
      sessions = mock(SheetSessionService.class);
      service = service();
      service.apply("TOK1", agent,
                    ed -> ed.addJoin("J", "TA", "k", "TB", "k", "INNER", null, null));
      assertInstanceOf(ComposedTableAssembly.class, ws1.getAssembly("J"));
   }

   // ------------------------------------------------------------------ inside one worksheet

   @Test
   void aCopiedSetRepointsTheCopiedJoinAtTheCopiedSources() throws Exception {
      PasteAssembliesOutcome out = paste(null, false, "TA", "TB", "J");

      assertEquals(3, out.pasted().size(), out.toString());
      assertTrue(out.skipped().isEmpty(), out.toString());
      String ta = to(out, "TA");
      String tb = to(out, "TB");
      String j = to(out, "J");
      assertTrue(ta.startsWith("Copy of TA"), ta);
      assertTrue(tb.startsWith("Copy of TB"), tb);
      assertEquals(Arrays.asList(ta, tb), Arrays.asList(tables(ws1, j)));
      // the originals are untouched
      assertEquals(Arrays.asList("TA", "TB"), Arrays.asList(tables(ws1, "J")));
   }

   @Test
   void aCopiedJoinWithoutItsSourcesKeepsTheOriginalSources() throws Exception {
      PasteAssembliesOutcome out = paste(null, false, "J");

      assertEquals(1, out.pasted().size(), out.toString());
      assertEquals(Arrays.asList("TA", "TB"), Arrays.asList(tables(ws1, to(out, "J"))));
   }

   @Test
   void aCutInsideOneWorksheetMovesTheAssemblyAndRenamesNothing() throws Exception {
      int before = ws1.getAssemblies().length;
      PasteAssembliesOutcome out;

      try(MockedStatic<WorksheetEventUtil> ignored = mockStatic(WorksheetEventUtil.class)) {
         out = service.pasteAssemblies("TOK1", null, true, List.of("TA"), 500, 400, agent);
      }

      assertEquals(List.of("TA"), out.moved(), out.toString());
      assertTrue(out.pasted().isEmpty(), out.toString());
      assertEquals(before, ws1.getAssemblies().length);
      assertEquals(new Point(500, 400), ((WSAssembly) ws1.getAssembly("TA")).getPixelOffset());
   }

   // ------------------------------------------------------------------ across two worksheets

   @Test
   void aCopyIntoAnotherWorksheetKeepsTheSourceAndTheName() throws Exception {
      PasteAssembliesOutcome out = paste("TOK1", false, "TA");

      assertEquals("TA", to(out, "TA"));
      assertNotNull(ws2.getAssembly("TA"));
      assertNotNull(ws1.getAssembly("TA"));
      assertTrue(out.sourceNotRemoved() == null || out.sourceNotRemoved().isEmpty());
   }

   @Test
   void aSetThatLeavesASourceBehindSkipsTheJoinAndAddsNothingForIt() throws Exception {
      PasteAssembliesOutcome out = paste("TOK1", false, "J");

      assertEquals(1, out.skipped().size(), out.toString());
      assertEquals("J", out.skipped().get(0).name());
      assertFalse(out.skipped().get(0).reason().isBlank());
      assertTrue(out.pasted().isEmpty(), out.toString());
      assertNull(ws2.getAssembly("J"));
      assertEquals(0, ws2.getAssemblies().length);
   }

   @Test
   void aSkippedAssemblyDoesNotFailTheCallAfterTheRestWasPasted() throws Exception {
      PasteAssembliesOutcome out = paste("TOK1", false, "TA", "J");

      assertEquals(List.of("TA"), out.pasted().stream().map(p -> p.from()).toList());
      assertEquals(List.of("J"), out.skipped().stream().map(s -> s.name()).toList());
      assertNotNull(ws2.getAssembly("TA"));
   }

   @Test
   void aCutIntoAnotherWorksheetRemovesTheWholeSetFromTheSource() throws Exception {
      PasteAssembliesOutcome out = paste("TOK1", true, "TA", "TB", "J");

      assertEquals(3, out.pasted().size(), out.toString());
      assertTrue(out.sourceNotRemoved() == null || out.sourceNotRemoved().isEmpty(),
                 out.toString());
      assertEquals(0, ws1.getAssemblies().length, "the source keeps " + names(ws1));
      assertEquals(Arrays.asList("TA", "TB"), Arrays.asList(tables(ws2, "J")));
   }

   @Test
   void aCutOfASourceSomethingOutsideTheSetDependsOnIsSkippedAndNotDeleted() throws Exception {
      PasteAssembliesOutcome out = paste("TOK1", true, "TA");

      assertEquals(List.of("TA"), out.skipped().stream().map(s -> s.name()).toList());
      assertTrue(out.pasted().isEmpty(), out.toString());
      assertNotNull(ws1.getAssembly("TA"), "a skipped assembly must stay in the source");
      assertNull(ws2.getAssembly("TA"));
   }

   // ------------------------------------------------------------------ refusals change nothing

   @Test
   void aSourceSessionThatDoesNotResolveIsRefused() throws Exception {
      refused("nobody-s-token", "sourceSession", "TA");
   }

   @Test
   void aViewsheetSessionIsRefusedAsSource() throws Exception {
      JoinSession vs = new JoinSession("VSTOK", "Viewsheet/vs1", "alice~;~host-org",
                                       SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                       JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("VSTOK"), any())).thenReturn(vs);
      refused("VSTOK", "sourceSession", "TA");
   }

   @Test
   void aPaneScopedSessionIsRefusedAsSource() throws Exception {
      EditorContext scoped = new EditorContext("worksheetExpression", "TA", "x", null, false);
      JoinSession pane = new JoinSession("PANETOK", RT1, "alice~;~host-org", SheetType.WORKSHEET,
                                         0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
                                         null, null, scoped);
      when(sessions.resolve(eq("PANETOK"), any())).thenReturn(pane);
      refused("PANETOK", "sourceSession", "TA");
   }

   @Test
   void aViewerSessionIsRefusedAsSource() throws Exception {
      JoinSession viewer = new JoinSession("VIEWTOK", RT1, "alice~;~host-org",
                                           SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                           JoinSession.ConnectionMode.VIEWER, null, null, null);
      when(sessions.resolve(eq("VIEWTOK"), any())).thenReturn(viewer);
      refused("VIEWTOK", "sourceSession", "TA");
   }

   @Test
   void theDestinationsOwnSessionIsRefusedAsSource() throws Exception {
      String before = state(ws1) + "|" + state(ws2);

      PairingException e = assertThrows(PairingException.class, () ->
         service.pasteAssemblies("TOK2", "TOK2", false, List.of("TA"), null, null, agent));
      assertTrue(e.getMessage().contains("sourceSession"), e.getMessage());
      assertEquals(before, state(ws1) + "|" + state(ws2));
   }

   @Test
   void anEmptyOrUnknownOrHiddenNameIsRefusedByField() throws Exception {
      String before = state(ws1) + "|" + state(ws2);

      for(List<String> names : List.<List<String>>of(List.of(), List.of(" "), List.of("NOPE"))) {
         PairingException e = assertThrows(PairingException.class, () ->
            service.pasteAssemblies("TOK2", "TOK1", false, names, null, null, agent), "" + names);
         assertTrue(e.getMessage().startsWith("tables:"), e.getMessage());
      }

      ((AbstractWSAssembly) ws1.getAssembly("TB")).setOuter(true);
      PairingException e = assertThrows(PairingException.class, () ->
         service.pasteAssemblies("TOK2", "TOK1", false, List.of("TB"), null, null, agent));
      assertTrue(e.getMessage().startsWith("tables:"), e.getMessage());
      ((AbstractWSAssembly) ws1.getAssembly("TB")).setOuter(false);
      assertEquals(before, state(ws1) + "|" + state(ws2));
   }

   // ------------------------------------------------------------------ helpers

   private void refused(String sourceToken, String field, String... names) throws Exception {
      String before = state(ws1) + "|" + state(ws2);
      PairingException e = assertThrows(PairingException.class, () -> service.pasteAssemblies(
         "TOK2", sourceToken, false, List.of(names), null, null, agent));
      assertTrue(e.getMessage().contains(field), e.getMessage());
      assertEquals(before, state(ws1) + "|" + state(ws2), "a refusal must change nothing");
   }

   /** Pastes {@code names}: into WS1 itself ({@code sourceToken} null) or from WS1 into WS2. */
   private PasteAssembliesOutcome paste(String sourceToken, boolean cut, String... names)
      throws Exception
   {
      String target = sourceToken == null ? "TOK1" : "TOK2";

      try(MockedStatic<WorksheetEventUtil> ignored = mockStatic(WorksheetEventUtil.class)) {
         return service.pasteAssemblies(target, sourceToken, cut, List.of(names), null, null,
                                        agent);
      }
   }

   private static String to(PasteAssembliesOutcome out, String from) {
      return out.pasted().stream().filter(p -> p.from().equals(from)).findFirst()
         .orElseThrow(() -> new AssertionError(from + " not pasted: " + out)).to();
   }

   private static String[] tables(Worksheet ws, String join) {
      return ((ComposedTableAssembly) ws.getAssembly(join)).getTableNames();
   }

   private static List<String> names(Worksheet ws) {
      return Arrays.stream(ws.getAssemblies()).map(Assembly::getName).toList();
   }

   private static String state(Worksheet ws) {
      List<String> state = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         String line = assembly.getName() + "@" + ((WSAssembly) assembly).getPixelOffset();

         if(assembly instanceof ComposedTableAssembly composed) {
            line += " " + Arrays.toString(composed.getTableNames());
         }

         state.add(line);
      }

      Collections.sort(state);
      return String.join("\n", state);
   }

   private static void table(Worksheet ws, String name, int x, int y) {
      EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, name, "k", "v");
      ColumnSelection selection = table.getColumnSelection();
      String[] types = { "integer", "integer" };

      for(int i = 0; i < types.length; i++) {
         ((ColumnRef) selection.getAttribute(i)).setDataType(types[i]);
      }

      table.setEmbeddedData(new XEmbeddedTable(types, new Object[][] {
         { "k", "v" }, { 1, 10 }, { 2, 20 } }));
      table.setPixelOffset(new Point(x, y));
      ws.addAssembly(table);
   }

   private WorksheetEditService service() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      RuntimeWorksheet rws1 = runtime(ws1, RT1);
      RuntimeWorksheet rws2 = runtime(ws2, RT2);
      when(sessions.resolve(eq("TOK1"), any())).thenReturn(session("TOK1", RT1));
      when(sessions.resolve(eq("TOK2"), any())).thenReturn(session("TOK2", RT2));
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq(RT1), any()))
         .thenReturn(rws1);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq(RT2), any()))
         .thenReturn(rws2);

      // the real paste behind the (cluster) proxy, over the same two runtime worksheets
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getWorksheet(eq(RT1), any())).thenReturn(rws1);
      when(engine.getWorksheet(eq(RT2), any())).thenReturn(rws2);
      PasteAssembliesService paste = new PasteAssembliesService(engine, null);
      PasteAssembliesServiceProxy proxy = mock(PasteAssembliesServiceProxy.class);
      when(proxy.pasteAssemblies(any(), any(WSPasteAssembliesEvent.class), any(), any()))
         .thenAnswer(inv -> paste.pasteAssemblies(
            inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine,
                                      new InnerJoinService(null, null), null, null, null, proxy);
   }

   private static RuntimeWorksheet runtime(Worksheet ws, String id) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getID()).thenReturn(id);
      when(rws.getEntry()).thenReturn(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, id.replace('/', '_'), null));
      return rws;
   }

   private static JoinSession session(String token, String runtimeId) {
      return new JoinSession(token, runtimeId, "alice~;~host-org", SheetType.WORKSHEET, 0L,
                             Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, null);
   }
}
