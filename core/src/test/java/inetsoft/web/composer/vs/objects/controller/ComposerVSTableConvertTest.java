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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabTree;
import inetsoft.util.ThreadContext;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.event.ConvertToFreehandTableEvent;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs {@code ComposerVSTableService.convertToFreehandTable} end to end on an opened runtime
 * viewsheet (EmbeddedVS1: the Hurricane worksheet table with the columns datetime, day,
 * latitude, longitude, name, type, wind, year). The crosstab is bound to the worksheet table,
 * converted to a freehand table, and the freehand data is compared with the data the crosstab
 * had before the conversion. Only the UI side (lifecycle service, object tree, drill and info
 * handlers) is mocked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("slow")
@Tag("integration")
class ComposerVSTableConvertTest {
   @Test
   void convertedFreehandTableHasTheCrosstabData() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "type" }, new String[0], "wind", "Sum");
      String before = cells(box().getData(CROSSTAB));

      convert(CROSSTAB);

      assertInstanceOf(CalcTableVSAssembly.class, vs.getAssembly(CROSSTAB));
      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void convertedFreehandTableKeepsTheGroupOrderOfTwoRowDimensions() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "year", "type" }, new String[0], "wind", "Count");
      String before = cells(box().getData(CROSSTAB));

      convert(CROSSTAB);

      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void convertedFreehandTableHasTheTopNRowsOfTheCrosstabWithoutOthers() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "name", "year" }, new String[0],
                                             "wind", "Sum");
      VSDimensionRef name = (VSDimensionRef) crosstab.getVSCrosstabInfo().getDesignRowHeaders()[0];
      name.setRankingOptionValue(XCondition.TOP_N + "");
      name.setRankingNValue("3");
      name.setRankingColValue("Sum(wind)");
      String before = cells(box().getData(CROSSTAB));

      // the Top 3 setting limits the first column of the crosstab to exactly 3 names; no Others
      // row because the crosstab supports Others only on the innermost dimension, and name is the
      // outer one
      Set<String> names = new HashSet<>();
      String[] lines = before.split("\n");

      for(int i = 1; i < lines.length; i++) {
         String first = lines[i].split("\\|", -1)[0];

         if(!"null".equals(first)) {
            names.add(first);
         }
      }

      assertEquals(3, names.size(), "Top 3 not applied on the crosstab: " + names);

      convert(CROSSTAB);

      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void freehandTableCopiesFillBlankWithZeroAndSortOthersLast() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "type" }, new String[] { "year" },
                                             "wind", "Sum");
      crosstab.getVSCrosstabInfo().setFillBlankWithZeroValue(true);
      crosstab.getVSCrosstabInfo().setSortOthersLastValue(false);
      box().getData(CROSSTAB);

      convert(CROSSTAB);

      CalcTableVSAssemblyInfo info =
         (CalcTableVSAssemblyInfo) vs.getAssembly(CROSSTAB).getInfo();
      assertTrue(info.isFillBlankWithZero());
      assertFalse(info.isSortOthersLast());
   }

   @Test
   void freehandGroupCellsDoNotMergeWhenTheCrosstabDoesNotMergeSpans() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "year", "type" }, new String[0],
                                             "wind", "Sum");
      crosstab.getVSCrosstabInfo().setMergeSpanValue(false);
      box().getData(CROSSTAB);

      convert(CROSSTAB);

      TableLayout layout = ((CalcTableVSAssembly) vs.getAssembly(CROSSTAB)).getTableLayout();
      int groups = 0;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && b.getBType() == TableCellBinding.GROUP) {
               groups++;
               assertFalse(b.isMergeCells());
            }
         }
      }

      assertTrue(groups > 0, "no group cell in the freehand layout");
   }

   @Test
   void drilledCrosstabIsConvertedLikeTheNonDrilledCrosstab() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "year", "type" }, new String[0],
                                             "wind", "Sum");
      String before = cells(box().getData(CROSSTAB));
      CrosstabTree tree = crosstab.getCrosstabTree();
      tree.getExpandedPaths().put("year", new HashSet<>(Set.of("1851")));
      assertTrue(tree.isDrilled());

      convert(CROSSTAB);

      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void assemblyThatIsNotATableIsLeftUnchanged() throws Exception {
      Viewsheet vs = box().getViewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      vs.addAssembly(gauge);
      Assembly[] before = vs.getAssemblies();

      convert("Gauge1");

      assertArrayEquals(before, vs.getAssemblies());
      assertSame(gauge, vs.getAssembly("Gauge1"));
   }

   @Test
   void freehandTableKeepsTheNameAndTheCrosstabIsGone() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "type" }, new String[0], "wind", "Sum");
      int count = vs.getAssemblies().length;

      convert(CROSSTAB);

      assertEquals(count, vs.getAssemblies().length);
      assertInstanceOf(CalcTableVSAssembly.class, vs.getAssembly(CROSSTAB));
      assertFalse(vs.getAssembly(CROSSTAB) instanceof CrosstabVSAssembly);

      for(Assembly assembly : vs.getAssemblies()) {
         assertFalse(assembly instanceof CrosstabVSAssembly, "crosstab left: " + assembly);
      }
   }

   private ViewsheetSandbox box() {
      return vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
   }

   private void convert(String name) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ComposerVSTableService service = new ComposerVSTableService(
         Mockito.mock(CoreLifecycleService.class), Mockito.mock(VSObjectTreeService.class),
         Mockito.mock(VSObjectModelFactoryService.class), Mockito.mock(VSBindingService.class),
         assetRepository, viewsheetService, Mockito.mock(CrosstabDrillHandler.class),
         Mockito.mock(VSAssemblyInfoHandler.class));
      ConvertToFreehandTableEvent event = new ConvertToFreehandTableEvent();
      event.setName(name);
      Principal principal = ThreadContext.getPrincipal();
      service.convertToFreehandTable(rvs.getID(), event, principal, "http://localhost:8080/sree",
                                     Mockito.mock(CommandDispatcher.class));
   }

   private CrosstabVSAssembly crosstab(Viewsheet vs, String[] rows, String[] cols,
                                       String column, String formula)
   {
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, CROSSTAB);
      crosstab.setSourceInfo(source);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      cinfo.setDesignRowHeaders(dimensions(rows));
      cinfo.setDesignColHeaders(dimensions(cols));
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
      return crosstab;
   }

   private static DataRef[] dimensions(String[] names) {
      DataRef[] refs = new DataRef[names.length];

      for(int i = 0; i < names.length; i++) {
         VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, names[i])));
         dim.setGroupColumnValue(names[i]);
         refs[i] = dim;
      }

      return refs;
   }

   private static String cells(Object data) {
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      assertNotNull(lens, "no data");
      StringBuilder sb = new StringBuilder();
      lens.moreRows(Integer.MAX_VALUE);

      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append(lens.getObject(r, c)).append(c < lens.getColCount() - 1 ? "|" : "\n");
         }
      }

      return sb.toString();
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());
   @Autowired
   private ViewsheetService viewsheetService;
   @Autowired
   private AssetRepository assetRepository;

   private static final String TABLE = "TableView1";
   private static final String CROSSTAB = "Crosstab1";
}
