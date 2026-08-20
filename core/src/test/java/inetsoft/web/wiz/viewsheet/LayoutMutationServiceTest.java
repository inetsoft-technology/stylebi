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
import inetsoft.web.composer.vs.controller.VSLayoutControllerServiceProxy;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.DataTipInLayoutCheckResult;
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
 * Phase 3 of the layout implementation plan (2026-08-20-layout-implementation.md), Task 4:
 * {@code edit_layout_objects}/{@code set_layout_table_options}.
 *
 * <p>Uses a REAL {@link LayoutSessionService} (mocked only at the {@code ViewsheetSessionService}/
 * {@code ViewsheetService} boundary, exactly like {@code LayoutSessionServiceTest}'s own fixture)
 * rather than a mock, so the Hazard-1 re-assertion below actually exercises {@code mutateLayout}'s
 * real clone-mint-and-checkpoint mechanism from this task's own entry point -- proving this task
 * calls it correctly, not just that the mechanism works in isolation (Task 1 already proved that).
 */
@WizAgentTestSupport
class LayoutMutationServiceTest {
   private static final Principal AGENT = TestPrincipals.user("alice", "host-org");
   private static final String PRINT_LAYOUT = "Print Layout";

   /**
    * The coordinate-space test: moving/resizing "Table1" through {@code edit_layout_objects}
    * updates the layout-space reading ({@link LayoutReadService#get}, off the master's
    * {@code LayoutInfo}) but leaves that same object's viewsheet-space reading -- its real
    * {@code pixelOffset}/{@code pixelSize} on the master -- exactly as it was.
    */
   @Test
   void moveResizeUpdatesLayoutSpaceButNotViewsheetSpace() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      Point viewsheetPosBefore = fx.masterTable.getPixelOffset();
      Dimension viewsheetSizeBefore = fx.masterTable.getPixelSize();

      Map<String, Object> result = fx.service.editObjects("tok1", AGENT, PRINT_LAYOUT, "move_resize",
         VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 555, "y", 666, "width", 70, "height", 80)), false);

      assertEquals(false, result.get("requiresConfirmation"));

      LayoutObjectModelHolder table = fx.readTableObject();
      assertEquals(555, table.layoutX());
      assertEquals(666, table.layoutY());
      assertEquals(70, table.layoutWidth());
      assertEquals(80, table.layoutHeight());

      assertEquals(viewsheetPosBefore, fx.masterTable.getPixelOffset(),
                   "the master's own assembly pixel position must be untouched by a layout edit");
      assertEquals(viewsheetSizeBefore, fx.masterTable.getPixelSize(),
                   "the master's own assembly pixel size must be untouched by a layout edit");
   }

   /**
    * The Hazard-1 exit criterion again, from this task's own entry point (not just
    * {@code LayoutSessionServiceTest}'s unit test in isolation) -- a full {@code
    * edit_layout_objects} call through {@code LayoutMutationService} leaves the master runtime's
    * {@code Viewsheet} untouched: same layout-position bookkeeping, same per-format
    * {@code RScaleFont}, on an assembly the edit did not even target.
    */
   @Test
   void editObjectsNeverTouchesTheMasterViewsheetsRuntimeState() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      Point masterLayoutPositionBefore =
         fx.masterText.getVSAssemblyInfo().getLayoutPosition();
      Point masterPixelOffsetBefore = fx.masterText.getPixelOffset();
      float masterFormatScaleBefore =
         fx.masterText.getVSAssemblyInfo().getFormat().getRScaleFont();
      float masterTopLevelScaleBefore = fx.masterVs.getRScaleFont();

      fx.service.editObjects("tok1", AGENT, PRINT_LAYOUT, "move_resize", VSLayoutService.CONTENT,
         List.of(Map.of("name", "Table1", "x", 1, "y", 2, "width", 3, "height", 4)), false);

      // Re-resolve the master INDEPENDENTLY -- a fresh call into the mocked
      // ViewsheetSessionService, not anything the call above handed back.
      RuntimeViewsheet masterAfter = fx.viewsheetSessions.resolve("tok1", AGENT);
      TextVSAssembly textAfter = (TextVSAssembly) masterAfter.getViewsheet().getAssembly("Text1");

      assertEquals(masterLayoutPositionBefore, textAfter.getVSAssemblyInfo().getLayoutPosition(),
                   "an assembly the edit did not target must keep its layout-position bookkeeping");
      assertEquals(masterPixelOffsetBefore, textAfter.getPixelOffset(),
                   "the master's own assembly pixel position must be untouched");
      assertEquals(masterFormatScaleBefore, textAfter.getVSAssemblyInfo().getFormat().getRScaleFont(),
                   "the master's own per-assembly RScaleFont must be untouched");
      assertEquals(masterTopLevelScaleBefore, masterAfter.getViewsheet().getRScaleFont(),
                   "the master's own top-level RScaleFont must be untouched");
   }

   @Test
   void removeWithADataTipDependencyRequiresConfirmationAndDoesNotRemove() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      when(fx.vsLayoutControllerService.checkAssemblyInLayout(eq("master-1"), eq(PRINT_LAYOUT),
                                                              eq("Table1"), eq(AGENT)))
         .thenReturn(DataTipInLayoutCheckResult.builder().isAssemblyInLayout(true).build());

      Map<String, Object> result = fx.service.editObjects("tok1", AGENT, PRINT_LAYOUT, "remove",
         VSLayoutService.CONTENT, List.of(Map.of("name", "Table1")), false);

      assertEquals(true, result.get("requiresConfirmation"));
      assertNotNull(result.get("reason"));
      assertEquals(List.of("Table1"), result.get("objectNames"));

      // The underlying remove was never reached: no clone was even minted for the attempt.
      verify(fx.viewsheetService, never())
         .openTemporaryViewsheet(anyString(), any(AssetEntry.class), eq(AGENT));
      assertTrue(fx.printLayoutObjectNames().contains("Table1"),
                 "a blocked remove must leave the layout's object list untouched");
   }

   @Test
   void removeWithConfirmedTrueProceedsPastTheDataTipGuard() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      when(fx.vsLayoutControllerService.checkAssemblyInLayout(anyString(), anyString(), anyString(),
                                                              eq(AGENT)))
         .thenReturn(DataTipInLayoutCheckResult.builder().isAssemblyInLayout(true).build());

      Map<String, Object> result = fx.service.editObjects("tok1", AGENT, PRINT_LAYOUT, "remove",
         VSLayoutService.CONTENT, List.of(Map.of("name", "Table1")), true);

      assertEquals(false, result.get("requiresConfirmation"));
      assertFalse(fx.printLayoutObjectNames().contains("Table1"),
                  "confirmed: true must proceed with the removal");
      // The guard itself must not even be consulted once already confirmed.
      verify(fx.vsLayoutControllerService, never())
         .checkAssemblyInLayout(anyString(), anyString(), anyString(), any());
   }

   @Test
   void removeWithNoDependencySucceedsCleanlyWithoutConfirmation() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      when(fx.vsLayoutControllerService.checkAssemblyInLayout(anyString(), anyString(), anyString(),
                                                              eq(AGENT)))
         .thenReturn(DataTipInLayoutCheckResult.builder().isAssemblyInLayout(false).build());

      Map<String, Object> result = fx.service.editObjects("tok1", AGENT, PRINT_LAYOUT, "remove",
         VSLayoutService.CONTENT, List.of(Map.of("name", "Table1")), false);

      assertEquals(false, result.get("requiresConfirmation"));
      assertFalse(fx.printLayoutObjectNames().contains("Table1"));
   }

   @Test
   void setLayoutTableOptionsRefusedForAnObjectThatDoesNotSupportIt() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> fx.service.setTableLayoutOptions("tok1", AGENT, PRINT_LAYOUT, "Text1",
                                                 VSLayoutService.CONTENT, 1));

      assertTrue(thrown.getMessage().contains("Text1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("supportsTableLayout"), thrown.getMessage());
      // Refused before any service call: no clone minted, no checkpoint attempted.
      verify(fx.viewsheetService, never())
         .openTemporaryViewsheet(anyString(), any(AssetEntry.class), eq(AGENT));
   }

   @Test
   void setLayoutTableOptionsSucceedsForATableObject() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      fx.service.setTableLayoutOptions("tok1", AGENT, PRINT_LAYOUT, "Table1",
                                        VSLayoutService.CONTENT, 2);

      VSAssemblyLayout tableLayout = fx.printLayoutObject("Table1");
      assertEquals(2, tableLayout.getTableLayout());
   }

   // ── fixture ───────────────────────────────────────────────────────────────

   /**
    * Wires one {@code LayoutMutationService} against a REAL {@code LayoutSessionService}/
    * {@code VSLayoutService} (mocked only at the {@code ViewsheetSessionService}/
    * {@code ViewsheetService} boundary -- the same fixture shape {@code LayoutSessionServiceTest}
    * uses) and a mocked {@code VSLayoutControllerServiceProxy} (the data-tip guard's only
    * dependency, a pure read this class calls directly against the master).
    */
   private static final class Fixture {
      final ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
      final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      final VSLayoutService vsLayoutService = new VSLayoutService();
      final VSLayoutControllerServiceProxy vsLayoutControllerService =
         mock(VSLayoutControllerServiceProxy.class);
      final LayoutSessionService layoutSessions =
         new LayoutSessionService(viewsheetSessions, viewsheetService, vsLayoutService);
      final LayoutMutationService service = new LayoutMutationService(
         layoutSessions, viewsheetSessions, vsLayoutService, vsLayoutControllerService);

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
               // fixture doc for why this matters. LayoutMutationService's own mutations never
               // read/write the clone's content (they operate on master's LayoutInfo directly, per
               // this class's own class doc), so a minimal same-shape stand-in is enough here.
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
       * position -- mirrors {@code LayoutReadServiceTest}'s fixture.
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

      List<String> printLayoutObjectNames() {
         List<String> names = new ArrayList<>();

         for(VSAssemblyLayout layout : masterVs.getLayoutInfo().getPrintLayout()
            .getVSAssemblyLayouts())
         {
            names.add(layout.getName());
         }

         return names;
      }

      VSAssemblyLayout printLayoutObject(String name) {
         return masterVs.getLayoutInfo().getPrintLayout().getVSAssemblyLayouts().stream()
            .filter(l -> l.getName().equals(name))
            .findFirst()
            .orElseThrow();
      }

      /** Reads "Table1"'s layout-space geometry straight off the master's LayoutInfo. */
      LayoutObjectModelHolder readTableObject() {
         VSAssemblyLayout layout = printLayoutObject("Table1");
         return new LayoutObjectModelHolder(layout.getPosition().x, layout.getPosition().y,
                                             layout.getSize().width, layout.getSize().height);
      }
   }

   private record LayoutObjectModelHolder(int layoutX, int layoutY, int layoutWidth,
                                           int layoutHeight) {
   }
}
