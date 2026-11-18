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

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SelectionBaseVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81733: {@code get_format} reads a Table/Crosstab's {@code data}/{@code header} cells and a
 * Selection List/Tree's {@code cell}/{@code measureText}/{@code measureBar}/
 * {@code measureBarNegative} regions -- per distinct path, the same paths {@code set_format}
 * writes -- instead of refusing them.
 */
@Tag("core")
class ViewsheetFormatServiceRegionReadTest {
   private static final TableDataPath NAME_DETAIL =
      new TableDataPath(-1, TableDataPath.DETAIL, XSchema.STRING, new String[]{ "NAME" });
   private static final TableDataPath AGE_DETAIL =
      new TableDataPath(-1, TableDataPath.DETAIL, XSchema.INTEGER, new String[]{ "AGE" });
   private static final TableDataPath NAME_HEADER =
      new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING, new String[]{ "NAME" });

   private static VSCompositeFormat userFormat(Consumer<VSFormat> fill) {
      VSCompositeFormat format = new VSCompositeFormat();
      fill.accept(format.getUserDefinedFormat());
      return format;
   }

   private static VSCompositeFormat red() {
      return userFormat(f -> f.setForegroundValue("0xFF0000", true));
   }

   /** Answers every path with the pane model every default filled in, as the real painter does. */
   private static FormatPainterService painter() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      doAnswer(invocation -> {
         CapturingCommandDispatcher dispatcher = invocation.getArgument(3);
         VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
         model.setColor("#ff0000");
         model.setBackgroundAlpha(100);
         model.setRoundCorner(0);
         dispatcher.sendCommand("x", new SetCurrentFormatCommand(model));
         return null;
      }).when(painter).getFormat(eq("rt1"), any(GetVSObjectFormatEvent.class),
                                 any(Principal.class), any());
      return painter;
   }

   private static ViewsheetFormatService service(FormatPainterService painter,
                                                 RuntimeViewsheet rvs)
   {
      return new ViewsheetFormatService(sessionsFor(rvs), FormatPainterProxies.local(painter),
                                        mock(CalcTableService.class), null);
   }

   private static Map<String, Object> read(ViewsheetFormatService service, String assembly,
                                           String target, Integer level) throws Exception
   {
      return service.getFormat("tok", principal(),
                               new ViewsheetFormatService.FormatReadRequest(assembly, target,
                                                                            null, level));
   }

   private static String refusal(ViewsheetFormatService service, String assembly, String target,
                                 Integer level)
   {
      return assertThrows(IllegalArgumentException.class,
                          () -> read(service, assembly, target, level)).getMessage();
   }

   private static VSAssemblyInfo infoWith(FormatInfo formatInfo) {
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.getFormatInfo()).thenReturn(formatInfo);
      return info;
   }

   /** A Table whose live lens renders {@code cells} (one row per cell, one column). */
   private static RuntimeViewsheet tableRvs(FormatInfo formatInfo, TableDataPath... cells)
      throws Exception
   {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      VSTableLens lens = mock(VSTableLens.class);
      TableDataDescriptor desc = mock(TableDataDescriptor.class);
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      VSAssemblyInfo info = infoWith(formatInfo);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getID()).thenReturn("rt1");
      when(viewsheet.getAssembly("Table1")).thenReturn(assembly);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(box.getVSTableLens("Table1", false)).thenReturn(lens);
      when(lens.getDescriptor()).thenReturn(desc);
      when(lens.getColCount()).thenReturn(cells.length);

      for(int row = 0; row <= 1; row++) {
         when(lens.moreRows(row)).thenReturn(row == 0);
      }

      for(int col = 0; col < cells.length; col++) {
         when(desc.getCellDataPath(0, col)).thenReturn(cells[col]);
      }

      return rvs;
   }

   private static RuntimeViewsheet treeRvs(FormatInfo formatInfo, int levels) {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SelectionTreeVSAssembly tree = mock(SelectionTreeVSAssembly.class);
      VSAssemblyInfo info = infoWith(formatInfo);
      when(tree.getVSAssemblyInfo()).thenReturn(info);
      when(tree.getDataRefs()).thenReturn(new DataRef[levels]);
      when(tree.isIDMode()).thenReturn(false);
      when(viewsheet.getAssembly("Tree1")).thenReturn(tree);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getID()).thenReturn("rt1");
      return rvs;
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> regions(Map<String, Object> out) {
      return (List<Map<String, Object>>) out.get("regions");
   }

   @Test
   void dataReadsEachColumnsStoredFormatAndLeavesTheCommonFormatOutWhenTheyDiffer()
      throws Exception
   {
      FormatInfo formatInfo = new FormatInfo();
      formatInfo.setFormat(NAME_DETAIL, red());
      FormatPainterService painter = painter();

      Map<String, Object> out =
         read(service(painter, tableRvs(formatInfo, NAME_DETAIL, AGE_DETAIL)), "Table1", "data",
              null);

      assertEquals("data", out.get("target"));
      List<Map<String, Object>> regions = regions(out);
      assertEquals(2, regions.size(), String.valueOf(out));
      assertEquals("detail", regions.get(0).get("type"));
      assertEquals(List.of("NAME"), regions.get(0).get("path"));
      @SuppressWarnings("unchecked")
      Map<String, Object> name = (Map<String, Object>) regions.get(0).get("format");
      assertEquals(Set.of("color"), name.keySet(),
                   "only what the entry itself defines is reported: " + name);
      assertNull(regions.get(1).get("format"), "AGE has nothing stored");
      assertFalse(out.containsKey("format"),
                  "regions disagree, so no single format is claimed: " + out);

      ArgumentCaptor<GetVSObjectFormatEvent> event =
         ArgumentCaptor.forClass(GetVSObjectFormatEvent.class);
      verify(painter).getFormat(eq("rt1"), event.capture(), any(Principal.class), any());
      assertEquals(NAME_DETAIL, event.getValue().getDataPath());
   }

   @Test
   void dataReportsOneCommonFormatWhenEveryRegionAgrees() throws Exception {
      FormatInfo formatInfo = new FormatInfo();
      formatInfo.setFormat(NAME_DETAIL, red());
      formatInfo.setFormat(AGE_DETAIL, red());

      Map<String, Object> out =
         read(service(painter(), tableRvs(formatInfo, NAME_DETAIL, AGE_DETAIL)), "Table1", "data",
              null);

      assertEquals(Map.of("color", "#ff0000"), out.get("format"), String.valueOf(out));
      assertEquals(2, regions(out).size());
   }

   @Test
   void dataWithNothingStoredReadsNullNotADefault() throws Exception {
      FormatPainterService painter = painter();

      Map<String, Object> out =
         read(service(painter, tableRvs(new FormatInfo(), NAME_DETAIL)), "Table1", "data", null);

      assertTrue(out.containsKey("format"), String.valueOf(out));
      assertNull(out.get("format"));
      assertNull(regions(out).get(0).get("format"));
      verify(painter, never()).getFormat(any(), any(), any(), any());
   }

   @Test
   void headerReadsTheHeaderPathNotTheBodyOne() throws Exception {
      FormatInfo formatInfo = new FormatInfo();
      formatInfo.setFormat(NAME_HEADER, red());
      formatInfo.setFormat(NAME_DETAIL, userFormat(f -> f.setForegroundValue("0x00FF00", true)));
      FormatPainterService painter = painter();

      Map<String, Object> out =
         read(service(painter, tableRvs(formatInfo, NAME_HEADER, NAME_DETAIL)), "Table1",
              "header", null);

      assertEquals("header", out.get("target"));
      assertEquals(1, regions(out).size(), "the body path is not a header region");
      assertEquals("header", regions(out).get(0).get("type"));
      ArgumentCaptor<GetVSObjectFormatEvent> event =
         ArgumentCaptor.forClass(GetVSObjectFormatEvent.class);
      verify(painter).getFormat(eq("rt1"), event.capture(), any(Principal.class), any());
      assertEquals(NAME_HEADER, event.getValue().getDataPath());
   }

   @Test
   void dataWithNoRenderedCellsIsRefusedNotAnsweredAsNoFormat() throws Exception {
      String message = refusal(service(painter(), tableRvs(new FormatInfo())), "Table1", "data",
                               null);

      assertTrue(message.startsWith("get_format: could not find any body cells on 'Table1'"),
                 message);
   }

   @Test
   void selectionRegionsReadPerLevelAtTheSamePathsSetFormatWrites() throws Exception {
      FormatInfo formatInfo = new FormatInfo();
      formatInfo.setFormat(SelectionBaseVSAssemblyInfo.getMeasureTextPath(1), red());
      FormatPainterService painter = painter();
      ViewsheetFormatService service = service(painter, treeRvs(formatInfo, 2));

      Map<String, Object> level1 = read(service, "Tree1", "measureText", 1);

      assertEquals("measureText", level1.get("target"));
      assertEquals(1, level1.get("level"));
      assertEquals(Map.of("color", "#ff0000"), level1.get("format"), String.valueOf(level1));
      ArgumentCaptor<GetVSObjectFormatEvent> event =
         ArgumentCaptor.forClass(GetVSObjectFormatEvent.class);
      verify(painter).getFormat(eq("rt1"), event.capture(), any(Principal.class), any());
      assertEquals(SelectionBaseVSAssemblyInfo.getMeasureTextPath(1),
                   event.getValue().getDataPath());

      Map<String, Object> all = read(service, "Tree1", "measureText", null);
      assertEquals(2, regions(all).size(), "no level reads every level");
      assertFalse(all.containsKey("format"), "level 0 has none, level 1 has one: " + all);

      assertNull(read(service, "Tree1", "measureBar", 0).get("format"));
      assertNull(read(service, "Tree1", "measureBarNegative", 0).get("format"));
      assertEquals("measureBarNegative",
                   read(service, "Tree1", "measureBarNegative", 0).get("target"));
   }

   @Test
   void selectionCellReadsTheItemLabelPaths() throws Exception {
      FormatInfo formatInfo = new FormatInfo();
      formatInfo.setFormat(new TableDataPath(-1, TableDataPath.DETAIL), red());

      Map<String, Object> out = read(service(painter(), treeRvs(formatInfo, 1)), "Tree1", "cell",
                                     null);

      assertEquals(Map.of("color", "#ff0000"), out.get("format"), String.valueOf(out));
   }

   @Test
   void aLevelOutOfRangeAndALevelOnATableAreRefusedAsReads() throws Exception {
      ViewsheetFormatService trees = service(painter(), treeRvs(new FormatInfo(), 2));
      String range = refusal(trees, "Tree1", "measureText", 5);
      assertTrue(range.startsWith("get_format: level 5 is out of range for 'Tree1'"), range);

      ViewsheetFormatService tables =
         service(painter(), tableRvs(new FormatInfo(), NAME_DETAIL));
      String level = refusal(tables, "Table1", "data", 0);
      assertTrue(level.startsWith("get_format: 'level' only applies to targets 'cell'"), level);
   }

   @Test
   void regionTargetsOnTheWrongAssemblyKindAreRefusedByKind() throws Exception {
      Viewsheet viewsheet = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      VSAssembly chart = mock(ChartVSAssembly.class);
      when(viewsheet.getAssembly("Chart1")).thenReturn(chart);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      ViewsheetFormatService service = service(painter(), rvs);

      for(String target : new String[]{ "data", "header" }) {
         String message = refusal(service, "Chart1", target, null);
         assertTrue(message.startsWith("get_format: target '" + target +
                                       "' only applies to a Crosstab or Table"), message);
      }

      for(String target : new String[]{ "cell", "measureText", "measureBar",
                                        "measureBarNegative" }) {
         String message = refusal(service, "Chart1", target, null);
         assertTrue(message.startsWith("get_format: target '" + target.toLowerCase() +
                                       "' only applies to a Selection List or Selection Tree"),
                    message);
      }
   }
}
