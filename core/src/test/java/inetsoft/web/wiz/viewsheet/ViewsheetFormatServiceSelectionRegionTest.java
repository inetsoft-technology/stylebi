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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug 79227: {@code set_format} targets {@code cell}, {@code measureText} and {@code measureBar}
 * for a Selection List/Tree's item labels, measure text and measure bar. The axis covered is
 * level index x region kind x list / tree / ID-hierarchy tree.
 */
@Tag("core")
class ViewsheetFormatServiceSelectionRegionTest {
   // ── item label cells ────────────────────────────────────────────────────────────────────

   @Test
   void twoLevelTreeLabelsUseFolderPathForTheTopLevelAndDetailForTheLast() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(2, false));

      assertArrayEquals(new TableDataPath[]{ folder(0) },
                        paths(rvs, "Tree1", "cell", 0));
      assertArrayEquals(new TableDataPath[]{ detail() },
                        paths(rvs, "Tree1", "cell", 1));
   }

   @Test
   void treeLabelsWithoutALevelCoverEveryLevel() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(3, false));

      assertArrayEquals(new TableDataPath[]{ folder(0), folder(1), detail() },
                        paths(rvs, "Tree1", "cell", null));
   }

   @Test
   void singleLevelTreeLabelIsTheDetailPath() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(1, false));

      assertArrayEquals(new TableDataPath[]{ detail() }, paths(rvs, "Tree1", "cell", null));
      assertArrayEquals(new TableDataPath[]{ detail() }, paths(rvs, "Tree1", "cell", 0));
   }

   /** The renderer reads the two-argument constructor's paths; the six-argument form is not it. */
   @Test
   void labelPathsAreTheOnesTheRendererReads() {
      TableDataPath path = paths(rvs("Tree1", tree(2, false)), "Tree1", "cell", 0)[0];

      assertEquals(new TableDataPath(0, TableDataPath.GROUP_HEADER), path);
      assertNotEquals(new TableDataPath(0, TableDataPath.GROUP_HEADER, "string",
                                        new String[0], false, false), path);
   }

   @Test
   void listLabelIsTheDetailPath() {
      RuntimeViewsheet rvs = rvs("List1", mock(SelectionListVSAssembly.class));

      assertArrayEquals(new TableDataPath[]{ detail() }, paths(rvs, "List1", "cell", null));
      assertArrayEquals(new TableDataPath[]{ detail() }, paths(rvs, "List1", "cell", 0));
   }

   @Test
   void idModeTreeHasOneSharedLabelPathAndRefusesAHigherLevel() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(3, true));

      assertArrayEquals(new TableDataPath[]{ detail() }, paths(rvs, "Tree1", "cell", null));
      assertArrayEquals(new TableDataPath[]{ detail() }, paths(rvs, "Tree1", "cell", 0));

      String message = refusal(rvs, "Tree1", "cell", 1);
      assertTrue(message.contains("ID-hierarchy") && message.contains("Tree1"), message);
      assertFalse(message.contains("\n"), message);
   }

   // ── measure text and bar ────────────────────────────────────────────────────────────────

   @Test
   void measurePathsKeepTheirOwnLevelIncludingTheLastTreeLevel() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(2, false));

      assertArrayEquals(new TableDataPath[]{ SelectionBaseVSAssemblyInfo.getMeasureTextPath(1) },
                        paths(rvs, "Tree1", "measuretext", 1));
      assertArrayEquals(new TableDataPath[]{ SelectionBaseVSAssemblyInfo.getMeasureBarPath(0) },
                        paths(rvs, "Tree1", "measurebar", 0));
      assertArrayEquals(
         new TableDataPath[]{ SelectionBaseVSAssemblyInfo.getMeasureTextPath(0),
                              SelectionBaseVSAssemblyInfo.getMeasureTextPath(1) },
         paths(rvs, "Tree1", "measuretext", null));
   }

   @Test
   void measureBarWritesThePositiveBarOnly() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(2, false));

      for(TableDataPath path : paths(rvs, "Tree1", "measurebar", null)) {
         assertArrayEquals(new String[]{ "Measure Bar" }, path.getPath());
      }
   }

   @Test
   void listMeasureIsLevelZero() {
      RuntimeViewsheet rvs = rvs("List1", mock(SelectionListVSAssembly.class));

      assertArrayEquals(new TableDataPath[]{ SelectionBaseVSAssemblyInfo.getMeasureBarPath(0) },
                        paths(rvs, "List1", "measurebar", null));
   }

   @Test
   void idModeTreeMeasuresSpanTenLevels() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(3, true));

      assertEquals(10, paths(rvs, "Tree1", "measuretext", null).length);
      assertArrayEquals(new TableDataPath[]{ SelectionBaseVSAssemblyInfo.getMeasureTextPath(7) },
                        paths(rvs, "Tree1", "measuretext", 7));
      assertTrue(refusal(rvs, "Tree1", "measuretext", 10).contains("out of range"));
   }

   // ── refusals ────────────────────────────────────────────────────────────────────────────

   @Test
   void outOfRangeLevelIsRefusedNamingTheAssemblyAndTheValidRange() {
      RuntimeViewsheet rvs = rvs("Tree1", tree(2, false));

      for(String target : List.of("cell", "measuretext", "measurebar")) {
         String message = refusal(rvs, "Tree1", target, 2);
         assertTrue(message.contains("level 2") && message.contains("'Tree1'") &&
                    message.contains("2 levels: 0-1"), message);
         assertFalse(message.contains("\n"), message);
      }

      assertTrue(refusal(rvs, "Tree1", "cell", -1).contains("out of range"));
   }

   @Test
   void listWithALevelAboveZeroIsRefused() {
      String message = refusal(rvs("List1", mock(SelectionListVSAssembly.class)), "List1",
                               "measurebar", 1);

      assertTrue(message.contains("Selection List") && message.contains("'List1'"), message);
   }

   @Test
   void nonSelectionAssembliesAreRefusedNamingThem() {
      for(VSAssembly other : List.of(mock(TableVSAssembly.class), mock(CrosstabVSAssembly.class))) {
         String message = refusal(rvs("Other1", other), "Other1", "cell", null);

         assertTrue(message.contains("Selection List or Selection Tree") &&
                    message.contains("'Other1'") && message.contains("'data' or 'header'"),
                    message);
         assertFalse(message.contains("\n"), message);
      }

      assertTrue(refusal(rvs("Tree1", tree(2, false)), "Missing", "measurebar", null)
                    .contains("not found"));
   }

   @Test
   void treeWithoutLevelsIsRefusedInsteadOfWritingNothing() {
      String message = refusal(rvs("Tree1", tree(0, false)), "Tree1", "cell", null);

      assertTrue(message.contains("no tree levels") && message.contains("'Tree1'"), message);
   }

   @Test
   void dataAndHeaderOnASelectionPointToTheNewTargets() {
      for(String target : List.of("data", "header")) {
         RuntimeViewsheet rvs = rvs("Tree1", tree(2, false));
         ViewsheetFormatService service = service(mock(FormatPainterService.class), rvs);
         String message = assertThrows(IllegalArgumentException.class, () -> service.setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Tree1"), format(), false, target),
            "")).getMessage();

         assertTrue(message.contains("'cell'") && message.contains("'measureBar'"), message);
      }
   }

   @Test
   void levelOnAnotherTargetIsRefusedNotDropped() {
      ViewsheetFormatService service =
         service(mock(FormatPainterService.class), rvs("Tree1", tree(2, false)));

      for(String target : new String[]{ null, "object", "title", "data", "header" }) {
         String message = assertThrows(IllegalArgumentException.class, () -> service.setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Tree1"), format(), false, target,
                                                     null, null, 0), "")).getMessage();

         assertTrue(message.contains("'level'") && message.contains("'cell'"), message);
      }
   }

   @Test
   void unknownTargetListsTheNewNames() {
      ViewsheetFormatService service = service(mock(FormatPainterService.class), null);
      String message = assertThrows(IllegalArgumentException.class, () -> service.setFormat(
         "tok", principal(),
         new ViewsheetFormatService.FormatRequest(List.of("Tree1"), format(), false, "levels"),
         "")).getMessage();

      // The list also names the Calendar targets (Bug #79228), so check each name separately.
      assertTrue(message.contains("'cell'") && message.contains("'measureText'") &&
                 message.contains("'measureBar'"), message);
   }

   // ── request wiring ──────────────────────────────────────────────────────────────────────

   @Test
   void levelIsReadFromTheJsonBodyAndOlderBodiesStillParse() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      ViewsheetFormatService.FormatRequest withLevel = mapper.readValue(
         "{\"assemblies\":[\"Tree1\"],\"format\":{\"color\":\"#00AA00\"},\"reset\":false," +
         "\"target\":\"cell\",\"level\":1}", ViewsheetFormatService.FormatRequest.class);
      ViewsheetFormatService.FormatRequest without = mapper.readValue(
         "{\"assemblies\":[\"Tree1\"],\"format\":{\"color\":\"#00AA00\"},\"reset\":false," +
         "\"target\":\"cell\"}", ViewsheetFormatService.FormatRequest.class);

      assertEquals(1, withLevel.level());
      assertNull(without.level());
   }

   @Test
   void setFormatHandsTheComputedPathsToThePainterPerAssembly() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      RuntimeViewsheet rvs = rvs("Tree1", tree(2, false));

      service(painter, rvs).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Tree1"), format(), false,
                                                     "measureText", null, null, 1), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertArrayEquals(new String[]{ "Tree1" }, captor.getValue().getObjects());
      assertEquals(1, captor.getValue().getData().size());
      assertArrayEquals(new TableDataPath[]{ SelectionBaseVSAssemblyInfo.getMeasureTextPath(1) },
                        captor.getValue().getData().get(0));
   }

   @Test
   void resetOnCellWithoutALevelClearsEveryLevelPathAndNotTheObject() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      RuntimeViewsheet rvs = rvs("Tree1", tree(2, false));

      service(painter, rvs).setFormat(
            "tok", principal(),
            new ViewsheetFormatService.FormatRequest(List.of("Tree1"), null, true, "cell"), "");

      ArgumentCaptor<FormatVSObjectEvent> captor =
         ArgumentCaptor.forClass(FormatVSObjectEvent.class);
      verify(painter).setFormat(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                anyString());
      assertTrue(captor.getValue().isReset());
      assertArrayEquals(new TableDataPath[]{ folder(0), detail() },
                        captor.getValue().getData().get(0));
   }

   // ── fixtures ────────────────────────────────────────────────────────────────────────────

   private static TableDataPath folder(int level) {
      return new TableDataPath(level, TableDataPath.GROUP_HEADER);
   }

   private static TableDataPath detail() {
      return new TableDataPath(-1, TableDataPath.DETAIL);
   }

   private static TableDataPath[] paths(RuntimeViewsheet rvs, String name, String target,
                                        Integer level)
   {
      return ViewsheetFormatService.computeSelectionRegionPaths(rvs, name, target, level);
   }

   private static String refusal(RuntimeViewsheet rvs, String name, String target, Integer level) {
      return assertThrows(IllegalArgumentException.class,
                          () -> paths(rvs, name, target, level)).getMessage();
   }

   private static SelectionTreeVSAssembly tree(int levels, boolean idMode) {
      SelectionTreeVSAssembly tree = mock(SelectionTreeVSAssembly.class);
      when(tree.getDataRefs()).thenReturn(new DataRef[levels]);
      when(tree.isIDMode()).thenReturn(idMode);
      return tree;
   }

   private static RuntimeViewsheet rvs(String name, VSAssembly assembly) {
      Viewsheet viewsheet = mock(Viewsheet.class);
      when(viewsheet.getAssembly(name)).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      return rvs;
   }

   private static VSObjectFormatInfoModel format() {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#00AA00");
      return format;
   }

   private static ViewsheetFormatService service(FormatPainterService painter,
                                                 RuntimeViewsheet rvs)
   {
      return ViewsheetFormatServiceTest.service(ViewsheetFormatServiceTest.sessionsFor(rvs), painter,
                                                mock(CalcTableService.class));
   }

   private static Principal principal() {
      return ViewsheetFormatServiceTest.principal();
   }
}
