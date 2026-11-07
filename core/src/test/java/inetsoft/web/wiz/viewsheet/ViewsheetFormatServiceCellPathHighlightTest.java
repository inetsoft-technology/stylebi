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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug 80443 (run 2), cellPath (sub-gap 5): against a REAL {@link CrossTabFilter} (not a mocked
 * lens asserting against mocked member paths, the mistake the merged run-1 fix made -- see
 * {@code docs/teams/.../00-charter.md}), {@code set_format target:"data" cellPath:["West",
 * "Y2024"]} must format exactly the one rendered cell whose real REGION/YR member values match,
 * not the whole REGION x YR band every cell in it shares one {@code TableDataPath} for.
 *
 * <p>Proves this at the mechanism level: the fix installs a synthesized {@link HighlightGroup}
 * condition (not a direct band-wide {@code TableDataPath} format write), and that condition,
 * evaluated against the real crosstab data, matches the West/Y2024 cell but not an East/Y2023
 * cell sharing the exact same {@code TableDataPath}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceCellPathHighlightTest {
   private static final String ENTITY = "SALES";

   @BeforeEach
   void setUp() throws Exception {
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

      VSAggregateRef amtAgg = new VSAggregateRef();
      amtAgg.setDataRef(new AttributeRef(ENTITY, "AMT"));
      amtAgg.setColumnValue("AMT");
      amtAgg.setFormulaValue(AggregateFormula.SUM.getFormulaName());

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[]{ regionDim });
      cinfo.setDesignColHeaders(new VSDimensionRef[]{ yrDim });
      cinfo.setDesignAggregates(new VSAggregateRef[]{ amtAgg });
      crosstab.setVSCrosstabInfo(cinfo);

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "REGION")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "YR")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "AMT")));
      table.setColumnSelection(cs, false);

      // Same body values as the VFM-044 regression fixture (docs/teams/.../vfm-044.test.js).
      Object[][] rows = new Object[][]{
         { "REGION", "YR", "AMT" },
         { "East", "Y2023", 10.0 }, { "East", "Y2024", -12.0 },
         { "West", "Y2023", 20.0 }, { "West", "Y2024", 22.0 },
         { "North", "Y2023", -5.0 }, { "North", "Y2024", 7.0 },
      };
      table.setEmbeddedData(
         new XEmbeddedTable(new String[]{ "string", "string", "double" }, rows));
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

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
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

      service = new ViewsheetFormatService(sessions, painter, null, null);
   }

   @Test
   void memberTupleCellPathHighlightsOnlyThatCellsRealDataNotTheWholeBand() throws Exception {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setColor("#FF0000");

      service.setFormat(
         "tok", () -> "admin",
         new ViewsheetFormatService.FormatRequest(
            List.of("Crosstab1"), format, false, "data", "AMT", null, null,
            List.of("West", "Y2024")), "");

      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) crosstab.getInfo();
      TableHighlightAttr attr = info.getHighlightAttr();
      assertNotNull(attr, "cellPath must install a HighlightGroup, not a direct band write");

      int[] west = findCell("West", "Y2024");
      int[] east = findCell("East", "Y2023");
      TableDataPath bandPath = crossTabFilter.getDescriptor().getCellDataPath(west[0], west[1]);

      // East/Y2023 shares the exact same TableDataPath as West/Y2024 -- the whole point of the
      // bug: a direct write to this path would have colored both.
      assertEquals(bandPath,
                   crossTabFilter.getDescriptor().getCellDataPath(east[0], east[1]));

      HighlightGroup group = attr.getHighlight(bandPath);
      assertNotNull(group, "no HighlightGroup stored at the band's TableDataPath");

      Highlight hit = group.findGroup(HighlightGroup.DEFAULT_LEVEL, crossTabFilter, west[0], west[1]);
      Highlight miss = group.findGroup(HighlightGroup.DEFAULT_LEVEL, crossTabFilter, east[0], east[1]);

      assertNotNull(hit, "West/Y2024 must match its own synthesized condition");
      assertEquals(Color.RED, hit.getForeground());
      assertNull(miss,
                 "East/Y2023 shares the same TableDataPath but must NOT match West/Y2024's " +
                 "condition -- a direct per-path write could never tell them apart");
   }

   /** Scans the rendered crosstab for the body cell whose real REGION/YR member values match. */
   private int[] findCell(String region, String yr) {
      int colCount = crossTabFilter.getColCount();

      for(int row = 0; crossTabFilter.moreRows(row); row++) {
         for(int col = 0; col < colCount; col++) {
            TableDataPath path = crossTabFilter.getDescriptor().getCellDataPath(row, col);

            if(path == null || path.getType() != TableDataPath.SUMMARY) {
               continue;
            }

            Map<Object, Object> values = crossTabFilter.getKeyValuePairs(row, col, new HashMap<>());

            if(region.equals(values.get("REGION")) && yr.equals(values.get("YR"))) {
               return new int[]{ row, col };
            }
         }
      }

      throw new AssertionError("no rendered cell for " + region + "/" + yr);
   }

   private Viewsheet viewsheet;
   private CrosstabVSAssembly crosstab;
   private CrossTabFilter crossTabFilter;
   private ViewsheetFormatService service;
}
