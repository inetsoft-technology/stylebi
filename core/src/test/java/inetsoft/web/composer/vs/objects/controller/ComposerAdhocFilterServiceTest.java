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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.composer.vs.objects.event.FilterChartEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ad hoc filter on chart legends (Bug #78465): a relation chart's node color / node size
 * legend bound to a dimension must build the same selection as a Color legend.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ComposerAdhocFilterServiceTest {
   private static final String SOURCE = "Query1";

   static Stream<Arguments> nodeLegends() {
      List<Arguments> args = new ArrayList<>();

      for(int type : new int[] { GraphTypes.CHART_NETWORK, GraphTypes.CHART_TREE,
                                 GraphTypes.CHART_CIRCULAR })
      {
         for(String slot : new String[] { "nodeColor", "nodeSize" }) {
            args.add(Arguments.of(type, slot, false));
            args.add(Arguments.of(type, slot, true));
         }
      }

      return args.stream();
   }

   @ParameterizedTest(name = "type={0} slot={1} date={2}")
   @MethodSource("nodeLegends")
   void nodeDimensionLegendCreatesSelection(int chartType, String slot, boolean date)
      throws Exception
   {
      Captured cap = runFilter(chartType, slot, dimension(date), null);
      assertCreated(cap, date);
   }

   @ParameterizedTest(name = "type={0} date={1}")
   @MethodSource("colorLegends")
   void colorDimensionLegendCreatesSelection(int chartType, boolean date) throws Exception {
      Captured cap = runFilter(chartType, "color", dimension(date), null);
      assertCreated(cap, date);
   }

   static Stream<Arguments> colorLegends() {
      return Stream.of(Arguments.of(GraphTypes.CHART_NETWORK, false),
                       Arguments.of(GraphTypes.CHART_NETWORK, true),
                       Arguments.of(GraphTypes.CHART_TREE, false),
                       Arguments.of(GraphTypes.CHART_CIRCULAR, true));
   }

   @Test
   void aggregateNodeLegendMatchesColorLegend() throws Exception {
      Captured color = runFilter(GraphTypes.CHART_NETWORK, "color", aggregate(), null);
      Captured nodeColor = runFilter(GraphTypes.CHART_NETWORK, "nodeColor", aggregate(), null);
      Captured nodeSize = runFilter(GraphTypes.CHART_NETWORK, "nodeSize", aggregate(), null);

      assertNotNull(color.type, "the Color aggregate legend should reach createSelectionVSAssembly");
      assertEquals(color.describe(), nodeColor.describe());
      assertEquals(color.describe(), nodeSize.describe());
   }

   @Test
   void unknownLegendColumnDoesNothing() throws Exception {
      Captured cap = runFilter(GraphTypes.CHART_NETWORK, "nodeColor", dimension(false), "NoSuchField");

      assertEquals(0, cap.calls, "a legend column that matches no binding must not create a filter");
      verify(cap.tableService, never()).createSelectionVSAssembly(
         any(), anyInt(), any(), any(), any(), any(), any());
   }

   private static void assertCreated(Captured cap, boolean date) {
      assertEquals(1, cap.calls);
      assertEquals(date ? AbstractSheet.TIME_SLIDER_ASSET : AbstractSheet.SELECTION_LIST_ASSET,
                   cap.type);
      assertEquals(date ? XSchema.DATE : XSchema.STRING, cap.dtype);
      assertEquals(List.of(SOURCE), cap.tableNames);
      assertEquals(1, cap.columns.getAttributeCount());

      DataRef col = cap.columns.getAttribute(0);
      assertEquals(date ? "Date" : "State", col.getAttribute());
      assertEquals(date ? XSchema.DATE : XSchema.STRING, col.getDataType());

      if(date) {
         TimeSliderVSAssembly slider =
            assertInstanceOf(TimeSliderVSAssembly.class, cap.created);
         assertEquals(SOURCE, slider.getTableName());
      }
      else {
         SelectionListVSAssembly list =
            assertInstanceOf(SelectionListVSAssembly.class, cap.created);
         assertEquals(SOURCE, list.getTableName());
      }
   }

   private static VSDataRef dimension(boolean date) {
      VSChartDimensionRef dim = new VSChartDimensionRef();

      if(date) {
         dim.setGroupColumnValue("Date");
         dim.setDataType(XSchema.DATE);
         dim.setDateLevelValue(String.valueOf(DateRangeRef.YEAR_INTERVAL));
      }
      else {
         dim.setGroupColumnValue("State");
         dim.setDataType(XSchema.STRING);
      }

      return dim;
   }

   private static VSDataRef aggregate() {
      VSChartAggregateRef aggr = new VSChartAggregateRef();
      aggr.setColumnValue("Sales");
      aggr.setFormulaValue("Sum");
      return aggr;
   }

   private static Captured runFilter(int chartType, String slot, VSDataRef ref, String columnName)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      cinfo.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, SOURCE));

      RelationVSChartInfo rinfo = new RelationVSChartInfo();
      rinfo.setChartType(chartType);
      VSChartDimensionRef src = new VSChartDimensionRef();
      src.setGroupColumnValue("Source");
      rinfo.setSourceField(src);
      VSChartDimensionRef tgt = new VSChartDimensionRef();
      tgt.setGroupColumnValue("Target");
      rinfo.setTargetField(tgt);

      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(ref);

      switch(slot) {
      case "nodeColor":
         rinfo.setNodeColorField(aref);
         break;
      case "nodeSize":
         rinfo.setNodeSizeField(aref);
         break;
      case "color":
         rinfo.setColorField(aref);
         break;
      default:
         throw new IllegalArgumentException(slot);
      }

      cinfo.setVSChartInfo(rinfo);
      vs.addAssembly(chart);
      vs.addAssembly(new CurrentSelectionVSAssembly(vs, "CurrentSelection1"));

      ColumnSelection cols = new ColumnSelection();
      addColumn(cols, "Source", XSchema.STRING);
      addColumn(cols, "Target", XSchema.STRING);
      addColumn(cols, "State", XSchema.STRING);
      addColumn(cols, "Date", XSchema.DATE);
      addColumn(cols, "Sales", XSchema.DOUBLE);
      rinfo.update(vs, cols, false, null, null, null);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet("rid", principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSTableService tableService = spy(new VSTableService(null));
      Captured cap = new Captured(tableService);

      // record the arguments, build the real selection assembly, then stop the flow before the
      // layout code by returning null
      doAnswer(inv -> {
         cap.calls++;
         cap.type = inv.getArgument(1);
         cap.dtype = inv.getArgument(2);
         cap.tableNames = inv.getArgument(4);
         cap.columns = inv.getArgument(5);
         cap.created = (VSAssembly) inv.callRealMethod();
         return null;
      }).when(tableService).createSelectionVSAssembly(
         any(), anyInt(), any(), any(), any(), any(), any());

      ComposerAdhocFilterService service = new ComposerAdhocFilterService(
         viewsheetService, mock(CoreLifecycleService.class), tableService,
         mock(GroupingService.class));

      FilterChartEvent event = new FilterChartEvent();
      event.setName("Chart1");
      event.setColumnName(columnName != null ? columnName : ref.getFullName());
      event.setLegend(true);
      event.setDimension(ref instanceof VSDimensionRef);

      service.addChartFilter("rid", event, principal, "", mock(CommandDispatcher.class));
      return cap;
   }

   @Test
   void anAdhocSliderIsSquareUntilItReturns() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "Slider1");
      vs.addAssembly(slider);
      VSAssemblyInfo info = slider.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.initDefaultFormat();
      VSCompositeFormat objFormat = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      assertEquals(6, objFormat.getDefaultFormat().getRoundCornerValue());

      ComposerAdhocFilterService service = new ComposerAdhocFilterService(
         mock(ViewsheetService.class), mock(CoreLifecycleService.class),
         new VSTableService(null), mock(GroupingService.class));

      invoke(service, "saveFormats", slider);
      objFormat = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      assertEquals(0, objFormat.getDefaultFormat().getRoundCornerValue());

      invoke(service, "restoreFormats", slider);
      objFormat = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      assertEquals(6, objFormat.getDefaultFormat().getRoundCornerValue());
   }

   private static void invoke(ComposerAdhocFilterService service, String name, VSAssembly arg)
      throws Exception
   {
      java.lang.reflect.Method method =
         ComposerAdhocFilterService.class.getDeclaredMethod(name, VSAssembly.class);
      method.setAccessible(true);
      method.invoke(service, arg);
   }

   private static void addColumn(ColumnSelection cols, String name, String dtype) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));
      col.setDataType(dtype);
      cols.addAttribute(col);
   }

   private static final class Captured {
      Captured(VSTableService tableService) {
         this.tableService = tableService;
      }

      String describe() {
         DataRef col = columns == null || columns.getAttributeCount() == 0 ?
            null : columns.getAttribute(0);
         return "calls=" + calls + " type=" + type + " dtype=" + dtype + " tables=" + tableNames +
            " attr=" + (col == null ? null : col.getAttribute()) +
            " colType=" + (col == null ? null : col.getDataType()) +
            " created=" + (created == null ? null : created.getClass().getSimpleName());
      }

      final VSTableService tableService;
      int calls;
      Integer type;
      String dtype;
      List<String> tableNames;
      ColumnSelection columns;
      VSAssembly created;
   }
}
