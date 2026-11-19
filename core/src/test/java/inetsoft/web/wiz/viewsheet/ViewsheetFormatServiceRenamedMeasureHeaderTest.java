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
import inetsoft.report.TableDataPath;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.CrosstabVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.handler.SetTableHeaderAliasHandler;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug 81582: a Crosstab measure header renamed through set_column_labels (a MESSAGE_FORMAT
 * override in the assembly's FormatInfo, invisible to lens.getObject) must be accepted as
 * set_format's 'field' for the data and header targets, exactly like its internal name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceRenamedMeasureHeaderTest {
   private static final String ENTITY = "SALES";

   @BeforeEach
   void setUp() throws Exception {
      build(1, false, false);
   }

   /**
    * Renders a crosstab with one row dimension (REGION), the same {@code Sum(AMT)} bound
    * {@code copies} times, an optional YR column dimension, and the given side-by-side setting.
    */
   private void build(int copies, boolean sideBySide, boolean colDim) throws Exception {
      build(copies, sideBySide, colDim, false);
   }

   /** As above; {@code withQty} replaces all but the first aggregate with Sum(QTY). */
   private void build(int copies, boolean sideBySide, boolean colDim, boolean withQty)
      throws Exception
   {
      viewsheet = new Viewsheet();
      crosstab = new CrosstabVSAssembly(viewsheet, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      viewsheet.addAssembly(crosstab);

      VSDimensionRef regionDim = new VSDimensionRef();
      regionDim.setDataRef(new AttributeRef(ENTITY, "REGION"));
      regionDim.setGroupColumnValue("REGION");

      VSDimensionRef yrDim = new VSDimensionRef();
      yrDim.setDataRef(new AttributeRef(ENTITY, "YR"));
      yrDim.setGroupColumnValue("YR");

      VSAggregateRef[] aggs = new VSAggregateRef[copies];

      for(int i = 0; i < copies; i++) {
         aggs[i] = new VSAggregateRef();
         aggs[i].setDataRef(new AttributeRef(ENTITY, "AMT"));
         aggs[i].setColumnValue("AMT");
         aggs[i].setFormulaValue(AggregateFormula.SUM.getFormulaName());
      }

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[]{ regionDim });
      cinfo.setDesignColHeaders(colDim ? new VSDimensionRef[]{ yrDim } : new VSDimensionRef[0]);
      cinfo.setSummarySideBySide(sideBySide);

      if(withQty) {
         VSAggregateRef qtyAgg = new VSAggregateRef();
         qtyAgg.setDataRef(new AttributeRef(ENTITY, "QTY"));
         qtyAgg.setColumnValue("QTY");
         qtyAgg.setFormulaValue(AggregateFormula.SUM.getFormulaName());
         aggs = new VSAggregateRef[]{ aggs[0], qtyAgg };
      }

      cinfo.setDesignAggregates(aggs);
      crosstab.setVSCrosstabInfo(cinfo);

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "REGION")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "YR")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "AMT")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "QTY")));
      table.setColumnSelection(cs, false);

      // Same body values as the VFM-044 regression fixture (docs/teams/.../vfm-044.test.js).
      Object[][] rows = new Object[][]{
         { "REGION", "YR", "AMT", "QTY" },
         { "East", "Y2023", 10.0, 1.0 }, { "East", "Y2024", -12.0, 1.0 },
         { "West", "Y2023", 20.0, 1.0 }, { "West", "Y2024", 22.0, 1.0 },
         { "North", "Y2023", -5.0, 1.0 }, { "North", "Y2024", 7.0, 1.0 },
      };
      table.setEmbeddedData(
         new XEmbeddedTable(new String[]{ "string", "string", "double", "double" }, rows));
      ws.addAssembly(table);

      Method setBaseWorksheet =
         Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(viewsheet, ws);

      // A throwaway sandbox, used only to actually execute the crosstab query and get a real
      // CrossTabFilter back -- the same technique as CrosstabHighlightSummaryHeaderColumnShiftTest.
      ViewsheetSandbox queryBox =
         new ViewsheetSandbox(viewsheet, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      java.lang.reflect.Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(queryBox, wbox);

      cinfo.update(viewsheet, cs, null, false, "Query1", null);

      TableLens rawLens = new CrosstabVSAQuery(queryBox, "Crosstab1", false).getTableLens(false);
      assertTrue(rawLens instanceof CrossTabFilter,
                 "expected a raw CrossTabFilter with no sort/max-row wrapping");
      crossTabFilter = (CrossTabFilter) rawLens;
      crossTabFilter.moreRows(TableLens.EOT);

      // The sandbox set_format itself resolves through -- stubbed to hand back the real
      // CrossTabFilter above, the same way ViewsheetFormatServiceDateRenderTest stubs a real
      // DefaultTableLens into its own mocked write-side sandbox.
      ViewsheetSandbox writeBox = mock(ViewsheetSandbox.class);
      when(writeBox.getVSTableLens(eq("Crosstab1"), anyBoolean()))
         .thenReturn(new VSTableLens(crossTabFilter));

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(writeBox));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      FormatPainterService painter = new FormatPainterService(
         mock(CoreLifecycleService.class), new ChartRegionHandler(), viewsheetService,
         mock(VSObjectModelFactoryService.class), mock(VSBindingService.class),
         mock(VSLayoutService.class));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      CapturingCommandDispatcher dispatcher = mock(CapturingCommandDispatcher.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", dispatcher);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      service = new ViewsheetFormatService(sessions, FormatPainterProxies.local(painter), null,
                                           null);
   }

   @Test
   void renamedLabelResolvesLikeTheInternalNameForDataAndHeader() throws Exception {
      renameHeaders("Revenue");

      for(String target : List.of("data", "header")) {
         TableDataPath[] byLabel = resolve(target, "Revenue");
         TableDataPath[] byName = resolve(target, "AMT");
         // A single-measure crosstab renders its measure header as GROUP_HEADER; the header
         // target collects it like a HEADER-typed one.
         assertTrue(byLabel.length > 0, target + ": no paths resolved for the renamed label");
         assertEquals(List.of(byName), List.of(byLabel), target);
      }
   }

   /**
    * Bug 81582 follow-up: side-by-side measures with no column dimension render each measure's
    * header as GROUP_HEADER; header + field must still return exactly that measure's header
    * cell, renamed or not, instead of an empty array.
    */
   @Test
   void headerTargetScopesToOneMeasureOnSideBySideWithoutColumnDimension() throws Exception {
      build(2, true, false, true);
      renameHeaders("Revenue");

      TableDataPath[] amt = resolve("header", "AMT");
      TableDataPath[] qty = resolve("header", "QTY");
      assertEquals(1, amt.length, "AMT header cells");
      assertEquals(1, qty.length, "QTY header cells");
      assertEquals(List.of(amt), List.of(resolve("header", "Revenue")));
      assertTrue(!List.of(amt).equals(List.of(qty)), "measures must not share a header path");
   }

   @Test
   void headerTargetUnknownMeasureNamesTheMeasureNotRenderedRows() {
      Exception e = assertThrows(Exception.class, () -> resolve("header", "Nope"));
      assertTrue(e.getMessage().contains("is not an aggregate"), e.getMessage());
   }

   @Test
   void unrenamedAndRenamedRefusalListsTheRenamedLabel() throws Exception {
      renameHeaders("Revenue");
      Exception e = assertThrows(Exception.class, () -> resolve("data", "Nope"));
      assertTrue(e.getMessage().contains("Revenue (AMT)"), e.getMessage());
   }

   @Test
   void unknownLabelStillRefusedWithoutRename() {
      Exception e = assertThrows(Exception.class, () -> resolve("data", "Revenue"));
      assertTrue(e.getMessage().contains("is not an aggregate"), e.getMessage());
   }

   /** Bug #81584: with no separate summary-header row the measure header is a GROUP_HEADER. */
   @Test
   void fieldScopedHeaderFindsTheMeasureHeaderWithNoSummaryHeaderRow() throws Exception {
      for(boolean sideBySide : new boolean[]{ false, true }) {
         build(1, sideBySide, false);
         assertMeasureHeaderPaths("1 copy, sideBySide=" + sideBySide);
      }

      build(2, true, false);
      assertMeasureHeaderPaths("2 copies, side by side, no column dimension");
   }

   /** Controls: layouts with a separate summary-header row still resolve. */
   @Test
   void fieldScopedHeaderStillWorksWithAColumnDimensionAndOneCopyStacked() throws Exception {
      build(2, true, true);
      assertMeasureHeaderPaths("2 copies, side by side, column dimension");
      build(1, false, false);
      assertMeasureHeaderPaths("1 copy, stacked");
   }

   @Test
   void fieldScopedHeaderDoesNotSweepInDimensionHeaders() throws Exception {
      build(2, true, false);

      for(TableDataPath path : resolve("header", "AMT")) {
         String[] segs = path.getPath();
         assertEquals("AMT", segs[segs.length - 1]);
      }
   }

   @Test
   void anUnmatchedHeaderFieldIsRefusedWithAFieldNamedMessage() throws Exception {
      build(2, true, false);
      Exception e = assertThrows(Exception.class, () -> resolve("header", "Nope"));
      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
   }

   private void assertMeasureHeaderPaths(String layout) throws Exception {
      TableDataPath[] paths = resolve("header", "AMT");
      assertTrue(paths.length > 0, layout + ": no header paths");

      for(TableDataPath path : paths) {
         String[] segs = path.getPath();
         assertEquals("AMT", segs[segs.length - 1], layout);
         assertTrue(path.getType() == TableDataPath.HEADER ||
                       path.getType() == TableDataPath.GROUP_HEADER, layout);
      }
   }

   private TableDataPath[] resolve(String target, String field) throws Exception {
      boolean data = "data".equals(target);
      Method m = data ?
         ViewsheetFormatService.class.getDeclaredMethod(
            "computeDataRegionPaths", RuntimeViewsheet.class, String.class, String.class,
            List.class) :
         ViewsheetFormatService.class.getDeclaredMethod(
            "computeHeaderRegionPaths", RuntimeViewsheet.class, String.class, String.class);
      m.setAccessible(true);
      try {
         return (TableDataPath[]) ("data".equals(target) ?
            m.invoke(null, rvs, "Crosstab1", field, null) :
            m.invoke(null, rvs, "Crosstab1", field));
      }
      catch(java.lang.reflect.InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   /** Writes the same MESSAGE_FORMAT alias set_column_labels writes, at every header path. */
   private void renameHeaders(String label) {
      TableDataDescriptor desc = crossTabFilter.getDescriptor();
      int colCount = crossTabFilter.getColCount();

      for(int row = 0; crossTabFilter.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            TableDataPath path = desc.getCellDataPath(row, col);

            if(path != null && (path.getType() == TableDataPath.HEADER ||
               path.getType() == TableDataPath.GROUP_HEADER) &&
               path.getPath() != null && path.getPath().length > 0 &&
               "AMT".equals(path.getPath()[path.getPath().length - 1]))
            {
               SetTableHeaderAliasHandler.setAlias(path, crosstab.getFormatInfo(), label);
            }
         }
      }
   }

   private Viewsheet viewsheet;
   private CrosstabVSAssembly crosstab;
   private CrossTabFilter crossTabFilter;
   private ViewsheetFormatService service;
   private RuntimeViewsheet rvs;
}
