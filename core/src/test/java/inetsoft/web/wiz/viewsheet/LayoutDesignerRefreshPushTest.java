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
package inetsoft.web.wiz.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.uql.viewsheet.vslayout.PrintInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.wiz.pairing.SheetAgentBroadcastService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81189: every successful layout write ({@code edit_layout_objects}, {@code
 * set_layout_table_options}, {@code layout_undo}/{@code layout_redo}) pushes exactly one layout
 * designer refresh to the paired browser; a no-op undo/redo pushes nothing and a failing push
 * never fails the call.
 */
@WizAgentTestSupport
class LayoutDesignerRefreshPushTest {
   private static final Principal AGENT = TestPrincipals.user("alice", "host-org");
   private static final String PRINT_LAYOUT = "Print Layout";

   @Test
   void moveResizePushesOneRefresh() throws Exception {
      Fixture fx = new Fixture();

      fx.mutationService.editObjects("tok1", AGENT, PRINT_LAYOUT, "move_resize",
         VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 5, "y", 6, "width", 70, "height", 80)), false);

      verify(fx.broadcast, times(1)).broadcastLayoutRefresh(fx.masterRvs, PRINT_LAYOUT, AGENT);
   }

   @Test
   void removePushesOneRefresh() throws Exception {
      Fixture fx = new Fixture();

      fx.mutationService.editObjects("tok1", AGENT, PRINT_LAYOUT, "remove",
         VSLayoutService.CONTENT, List.of(Map.of("name", "Text1")), false);

      verify(fx.broadcast, times(1)).broadcastLayoutRefresh(fx.masterRvs, PRINT_LAYOUT, AGENT);
   }

   @Test
   void setTableLayoutOptionsPushesOneRefresh() throws Exception {
      Fixture fx = new Fixture();

      fx.mutationService.setTableLayoutOptions(
         "tok1", AGENT, PRINT_LAYOUT, "Table1", VSLayoutService.CONTENT, 1);

      verify(fx.broadcast, times(1)).broadcastLayoutRefresh(fx.masterRvs, PRINT_LAYOUT, AGENT);
   }

   @Test
   void undoAndRedoEachPushOnceAndNoOpsPushNothing() throws Exception {
      Fixture fx = new Fixture();

      assertEquals(false, fx.undoService.layoutUndo("tok1", AGENT, PRINT_LAYOUT).get("applied"));
      verify(fx.broadcast, never()).broadcastLayoutRefresh(any(), any(), any());

      fx.mutationService.editObjects("tok1", AGENT, PRINT_LAYOUT, "move_resize",
         VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 5, "y", 6, "width", 70, "height", 80)), false);
      clearInvocations(fx.broadcast);
      int point = fx.masterRvs.getLayoutPoint();
      int size = fx.masterRvs.getLayoutPointsSize();

      assertEquals(true, fx.undoService.layoutUndo("tok1", AGENT, PRINT_LAYOUT).get("applied"));
      verify(fx.broadcast, times(1)).broadcastLayoutRefresh(fx.masterRvs, PRINT_LAYOUT, AGENT);
      assertEquals(size, fx.masterRvs.getLayoutPointsSize());
      assertEquals(point - 1, fx.masterRvs.getLayoutPoint());

      clearInvocations(fx.broadcast);
      assertEquals(true, fx.undoService.layoutRedo("tok1", AGENT, PRINT_LAYOUT).get("applied"));
      verify(fx.broadcast, times(1)).broadcastLayoutRefresh(fx.masterRvs, PRINT_LAYOUT, AGENT);

      clearInvocations(fx.broadcast);
      assertEquals(false, fx.undoService.layoutRedo("tok1", AGENT, PRINT_LAYOUT).get("applied"));
      verify(fx.broadcast, never()).broadcastLayoutRefresh(any(), any(), any());
   }

   @Test
   void aFailingPushNeverFailsTheToolCall() throws Exception {
      Fixture fx = new Fixture();
      doThrow(new IllegalStateException("boom")).when(fx.broadcast)
         .broadcastLayoutRefresh(any(), any(), any());

      Map<String, Object> result = fx.mutationService.editObjects(
         "tok1", AGENT, PRINT_LAYOUT, "move_resize", VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 5, "y", 6, "width", 70, "height", 80)), false);

      assertEquals(false, result.get("requiresConfirmation"));
      assertEquals(true, fx.undoService.layoutUndo("tok1", AGENT, PRINT_LAYOUT).get("applied"));
   }

   private static final class Fixture {
      final ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
      final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      final VSLayoutService vsLayoutService = new VSLayoutService();
      final SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      final LayoutSessionService layoutSessions =
         new LayoutSessionService(viewsheetSessions, viewsheetService, vsLayoutService);
      final LayoutMutationService mutationService = new LayoutMutationService(
         layoutSessions, viewsheetSessions, vsLayoutService, broadcast);
      final LayoutUndoService undoService =
         new LayoutUndoService(layoutSessions, viewsheetSessions, vsLayoutService, broadcast);

      final Viewsheet masterVs = new Viewsheet();
      final RuntimeViewsheet masterRvs = spy(new RuntimeViewsheet());
      final AssetEntry masterEntry = mock(AssetEntry.class);

      private int cloneCounter;

      Fixture() throws Exception {
         TextVSAssembly text = new TextVSAssembly(masterVs, "Text1");
         text.setPixelOffset(new Point(10, 20));
         text.setPixelSize(new Dimension(15, 10));
         masterVs.addAssembly(text);
         TableVSAssembly table = new TableVSAssembly(masterVs, "Table1");
         table.setPixelOffset(new Point(50, 60));
         table.setPixelSize(new Dimension(80, 40));
         masterVs.addAssembly(table);

         LayoutInfo info = new LayoutInfo();
         PrintLayout layout = new PrintLayout();
         layout.setPrintInfo(new PrintInfo("Letter", new inetsoft.graph.internal.DimensionD(8.5, 11),
                                            0.5f, 0.5f, 0.5f, 0.5f, "inches"));
         List<VSAssemblyLayout> objects = new ArrayList<>();
         objects.add(new VSAssemblyLayout("Text1", new Point(100, 200), new Dimension(30, 40)));
         objects.add(new VSAssemblyLayout("Table1", new Point(300, 400), new Dimension(50, 60)));
         layout.setVSAssemblyLayouts(objects);
         info.setPrintLayout(layout);
         masterVs.setLayoutInfo(info);

         doReturn(masterVs).when(masterRvs).getViewsheet();
         doReturn("master-1").when(masterRvs).getID();
         doReturn(masterEntry).when(masterRvs).getEntry();
         when(masterEntry.clone()).thenReturn(masterEntry);
         when(viewsheetSessions.resolve(eq("tok1"), eq(AGENT))).thenReturn(masterRvs);

         when(viewsheetService.openTemporaryViewsheet(anyString(), any(AssetEntry.class), eq(AGENT)))
            .thenAnswer(inv -> {
               String id = "clone-" + (++cloneCounter);
               RuntimeViewsheet clone = spy(new RuntimeViewsheet());
               Viewsheet initialCloneVs = new Viewsheet();
               initialCloneVs.addAssembly(new TextVSAssembly(initialCloneVs, "Text1"));
               initialCloneVs.addAssembly(new TableVSAssembly(initialCloneVs, "Table1"));
               Viewsheet[] cloneVsHolder = { initialCloneVs };
               doAnswer(getVs -> cloneVsHolder[0]).when(clone).getViewsheet();
               doAnswer(setVs -> {
                  cloneVsHolder[0] = setVs.getArgument(0);
                  return null;
               }).when(clone).setViewsheet(any());
               doReturn(id).when(clone).getID();
               when(viewsheetService.getViewsheet(eq(id), eq(AGENT))).thenReturn(clone);
               return id;
            });
      }
   }
}
