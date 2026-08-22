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
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.uql.viewsheet.vslayout.PrintInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.uql.viewsheet.vslayout.ViewsheetLayout;
import inetsoft.web.composer.vs.controller.VSLayoutControllerServiceProxy;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
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
 * Phase 3 of the layout implementation plan (2026-08-20-layout-implementation.md), Task 5:
 * {@code layout_undo}/{@code layout_redo}.
 *
 * <p>Uses REAL {@link LayoutSessionService}/{@link LayoutMutationService}/{@link
 * LayoutReadService} instances (mocked only at the {@code ViewsheetSessionService}/{@code
 * ViewsheetService}/{@code VSLayoutControllerServiceProxy}/{@code DeviceRegistry} boundary --
 * the same fixture shape {@code LayoutMutationServiceTest} established), so the Hazard-2
 * reset-on-switch test below genuinely exercises {@code LayoutSessionService}'s real clone
 * lifecycle -- including a read-triggered switch through {@code LayoutReadService.get} -- from
 * {@code LayoutUndoService}'s own entry point, not a mocked stand-in for it.
 */
@WizAgentTestSupport
class LayoutUndoServiceTest {
   private static final Principal AGENT = TestPrincipals.user("alice", "host-org");
   private static final String PRINT_LAYOUT = "Print Layout";
   private static final String DEVICE_A = "Device A";

   @Test
   void undoRevertsTheMostRecentEditAndRedoReappliesIt() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      fx.mutationService.editObjects("tok1", AGENT, PRINT_LAYOUT, "move_resize",
         VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 555, "y", 666, "width", 70, "height", 80)), false);
      assertPosition(fx.printLayoutObject("Table1"), 555, 666, 70, 80);

      Map<String, Object> undone = fx.undoService.layoutUndo("tok1", AGENT, PRINT_LAYOUT);

      assertEquals(true, undone.get("applied"));
      assertPosition(fx.printLayoutObject("Table1"), 300, 400, 50, 60);

      Map<String, Object> redone = fx.undoService.layoutRedo("tok1", AGENT, PRINT_LAYOUT);

      assertEquals(true, redone.get("applied"));
      assertPosition(fx.printLayoutObject("Table1"), 555, 666, 70, 80);
   }

   @Test
   void undoPastAvailableHistoryIsANoOpNotAnError() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      // No edit at all yet -- this call itself establishes focus (mints the clone, seeds one
      // baseline), but there is nothing earlier than the baseline to revert to.
      Map<String, Object> result = fx.undoService.layoutUndo("tok1", AGENT, PRINT_LAYOUT);

      assertEquals(false, result.get("applied"));
      assertPosition(fx.printLayoutObject("Table1"), 300, 400, 50, 60);
   }

   @Test
   void redoPastAvailableHistoryIsANoOpNotAnError() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      fx.mutationService.editObjects("tok1", AGENT, PRINT_LAYOUT, "move_resize",
         VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 555, "y", 666, "width", 70, "height", 80)), false);

      // Nothing has been undone yet, so there is nothing to redo.
      Map<String, Object> result = fx.undoService.layoutRedo("tok1", AGENT, PRINT_LAYOUT);

      assertEquals(false, result.get("applied"));
      assertPosition(fx.printLayoutObject("Table1"), 555, 666, 70, 80);

      // Undo once, redo once (consuming the only redo available), then redo again -- the second
      // redo must be a no-op too.
      fx.undoService.layoutUndo("tok1", AGENT, PRINT_LAYOUT);
      fx.undoService.layoutRedo("tok1", AGENT, PRINT_LAYOUT);
      Map<String, Object> secondRedo = fx.undoService.layoutRedo("tok1", AGENT, PRINT_LAYOUT);

      assertEquals(false, secondRedo.get("applied"));
      assertPosition(fx.printLayoutObject("Table1"), 555, 666, 70, 80);
   }

   /**
    * The Hazard-2 reset-on-switch test, exercised end to end through this task's own entry point
    * (not only at {@code LayoutSessionServiceTest}'s unit level): edit Print Layout, switch focus
    * to a device layout ("Device A") via a plain read ({@code LayoutReadService.get} -- the same
    * call {@code get_layout} makes), assert {@code layout_undo} on Device A has no history from
    * Print Layout's edit, then switch back to Print Layout and assert its own undo history was
    * not resurrected either.
    */
   @Test
   void undoOnADifferentlyFocusedLayoutHasNoHistoryFromTheOneJustLeft() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      fx.installDeviceLayout(DEVICE_A, 10, 10, 20, 20);

      fx.mutationService.editObjects("tok1", AGENT, PRINT_LAYOUT, "move_resize",
         VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 999, "y", 999, "width", 9, "height", 9)), false);
      assertPosition(fx.printLayoutObject("Table1"), 999, 999, 9, 9);

      // A plain read on a different layout -- LayoutReadService.get's own call chain
      // (LayoutSessionService.resolveForRead) -- switches focus away from Print Layout.
      fx.readService.get("tok1", AGENT, DEVICE_A);

      Map<String, Object> undoOnDeviceA = fx.undoService.layoutUndo("tok1", AGENT, DEVICE_A);

      assertEquals(false, undoOnDeviceA.get("applied"),
                   "Device A has no history of its own yet -- Print Layout's edit must not leak in");
      assertPosition(fx.namedLayoutObject(DEVICE_A, "Table1"), 10, 10, 20, 20);

      // Switch back to Print Layout via layout_undo itself (its own entry point re-focuses
      // before consulting the stack) -- its earlier edit was persisted directly to the master's
      // LayoutInfo by LayoutMutationService, independent of the undo-stack reset, so it is still
      // there; what must NOT reappear is the now-discarded UNDO HISTORY for that edit.
      Map<String, Object> undoOnPrintAfterSwitchBack =
         fx.undoService.layoutUndo("tok1", AGENT, PRINT_LAYOUT);

      assertEquals(false, undoOnPrintAfterSwitchBack.get("applied"),
                   "switching back to Print Layout must not resurrect its old undo history");
      assertPosition(fx.printLayoutObject("Table1"), 999, 999, 9, 9);
   }

   private static void assertPosition(VSAssemblyLayout layout, int x, int y, int width,
                                       int height)
   {
      assertEquals(new Point(x, y), layout.getPosition());
      assertEquals(new Dimension(width, height), layout.getSize());
   }

   // ── fixture ───────────────────────────────────────────────────────────────

   /**
    * Wires one {@code LayoutUndoService} against REAL {@code LayoutSessionService}/{@code
    * LayoutMutationService}/{@code LayoutReadService}/{@code VSLayoutService} instances (mocked
    * only at the {@code ViewsheetSessionService}/{@code ViewsheetService}/{@code
    * VSLayoutControllerServiceProxy}/{@code DeviceRegistry} boundary), the same fixture shape
    * {@code LayoutMutationServiceTest} uses.
    */
   private static final class Fixture {
      final ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
      final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      final VSLayoutService vsLayoutService = new VSLayoutService();
      final VSLayoutControllerServiceProxy vsLayoutControllerService =
         mock(VSLayoutControllerServiceProxy.class);
      final DeviceRegistry deviceRegistry = mock(DeviceRegistry.class);
      final ViewsheetPropertyDialogService dialogService =
         mock(ViewsheetPropertyDialogService.class);
      final LayoutSessionService layoutSessions =
         new LayoutSessionService(viewsheetSessions, viewsheetService, vsLayoutService);
      final LayoutMutationService mutationService = new LayoutMutationService(
         layoutSessions, viewsheetSessions, vsLayoutService, vsLayoutControllerService);
      final LayoutReadService readService = new LayoutReadService(
         viewsheetSessions, layoutSessions, vsLayoutService, deviceRegistry, dialogService);
      final LayoutUndoService undoService =
         new LayoutUndoService(layoutSessions, viewsheetSessions, vsLayoutService);

      final Viewsheet masterVs = new Viewsheet();
      final TextVSAssembly masterText = new TextVSAssembly(masterVs, "Text1");
      final TableVSAssembly masterTable = new TableVSAssembly(masterVs, "Table1");
      final RuntimeViewsheet masterRvs = spy(new RuntimeViewsheet());
      final AssetEntry masterEntry = mock(AssetEntry.class);

      private int cloneCounter;

      Fixture() throws Exception {
         masterText.setPixelOffset(new Point(10, 20));
         masterText.setPixelSize(new Dimension(15, 10));
         masterVs.addAssembly(masterText);

         masterTable.setPixelOffset(new Point(50, 60));
         masterTable.setPixelSize(new Dimension(80, 40));
         masterVs.addAssembly(masterTable);

         doReturn(masterVs).when(masterRvs).getViewsheet();
         doReturn("master-1").when(masterRvs).getID();
         doReturn(masterEntry).when(masterRvs).getEntry();
         when(masterEntry.clone()).thenReturn(masterEntry);
         when(viewsheetSessions.resolve(eq("tok1"), eq(AGENT))).thenReturn(masterRvs);

         when(viewsheetService.openTemporaryViewsheet(anyString(), any(AssetEntry.class), eq(AGENT)))
            .thenAnswer(inv -> {
               String id = "clone-" + (++cloneCounter);
               RuntimeViewsheet clone = spy(new RuntimeViewsheet());

               // A genuinely separate object graph from master's -- see LayoutSessionServiceTest's
               // fixture doc for why this matters. Neither LayoutMutationService's nor
               // LayoutUndoService's own mutations read/write the clone's content by name (they
               // operate on master's LayoutInfo directly, redirecting only the apply() call onto
               // the clone), so a minimal same-shape stand-in is enough here.
               Viewsheet initialCloneVs = new Viewsheet();
               TextVSAssembly cloneText = new TextVSAssembly(initialCloneVs, "Text1");
               cloneText.setPixelOffset(new Point(10, 20));
               initialCloneVs.addAssembly(cloneText);
               TableVSAssembly cloneTable = new TableVSAssembly(initialCloneVs, "Table1");
               cloneTable.setPixelOffset(new Point(50, 60));
               initialCloneVs.addAssembly(cloneTable);

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

         doAnswer(inv -> {
            String flushedId = inv.getArgument(0);
            when(viewsheetService.getViewsheet(eq(flushedId), any()))
               .thenThrow(new IllegalStateException("runtime " + flushedId + " has been flushed"));
            return null;
         }).when(viewsheetService).flushRuntimeSheet(anyString());
      }

      /**
       * A real print layout with a Text object (does not support table layout) and a Table
       * object (does), each placed at a distinct layout position from its own master/viewsheet
       * position -- mirrors {@code LayoutMutationServiceTest}'s fixture.
       */
      void installPrintLayout() {
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
      }

      /** Adds a real device (viewsheet) layout named {@code name} with one Table1 placement. */
      void installDeviceLayout(String name, int x, int y, int width, int height) {
         LayoutInfo info = masterVs.getLayoutInfo();
         ViewsheetLayout layout = new ViewsheetLayout();
         layout.setName(name);
         List<VSAssemblyLayout> objects = new ArrayList<>();
         objects.add(new VSAssemblyLayout("Table1", new Point(x, y), new Dimension(width, height)));
         layout.setVSAssemblyLayouts(objects);
         List<ViewsheetLayout> layouts = new ArrayList<>(info.getViewsheetLayouts());
         layouts.add(layout);
         info.setViewsheetLayouts(layouts);
      }

      VSAssemblyLayout printLayoutObject(String name) {
         return masterVs.getLayoutInfo().getPrintLayout().getVSAssemblyLayouts().stream()
            .filter(l -> l.getName().equals(name))
            .findFirst()
            .orElseThrow();
      }

      VSAssemblyLayout namedLayoutObject(String layoutName, String name) {
         return masterVs.getLayoutInfo().getViewsheetLayouts().stream()
            .filter(l -> l.getName().equals(layoutName))
            .findFirst()
            .orElseThrow()
            .getVSAssemblyLayouts().stream()
            .filter(l -> l.getName().equals(name))
            .findFirst()
            .orElseThrow();
      }
   }
}
