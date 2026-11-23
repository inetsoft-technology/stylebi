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
package inetsoft.web.binding.handler;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.sree.AnalyticRepository;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.binding.dnd.BindingDropTarget;
import inetsoft.web.binding.dnd.ChartTransfer;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.viewsheet.handler.VSDrillHandler;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A date measure dropped on a Gantt slot takes its colour from the chart's own palette. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSChartBindingHandlerGanttColorTest {
   private static final Color LEGACY_1 = CategoricalColorFrame.COLOR_PALETTE.get(0);
   private static final Color LEGACY_3 = CategoricalColorFrame.COLOR_PALETTE.get(2);
   private static final Color MODERN_1 = new Color(0x0490FF);
   private static final Color DARK_1 = new Color(0x4FA5FF);
   private static final Color AUTHOR = new Color(0x123456);

   @BeforeEach
   void setup() {
      chartRefService = mock(ChartRefModelFactoryService.class);
      handler = new VSChartBindingHandler(new ChartDndHandler(chartRefService),
                                          mock(VSDrillHandler.class),
                                          mock(VSChartDataHandler.class),
                                          mock(AnalyticRepository.class));
      viewsheet = new Viewsheet();
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(rvs.getViewsheet()).thenReturn(viewsheet);
   }

   // the binding tree's Start, End and Milestone drops
   @ParameterizedTest
   @ValueSource(ints = { ChartConstants.DROP_REGION_START, ChartConstants.DROP_REGION_END,
                         ChartConstants.DROP_REGION_MILESTONE })
   void aDateFromTheTreeOnAMarkedGanttTakesTheModernSlotOne(int region) throws Exception {
      ChartVSAssembly chart = chart(gantt(), VizMark.MODERN_LIGHT);

      drop(chart, date("Start"), region);

      assertEquals(MODERN_1, defaultColor(slot(chart, region)));
   }

   @Test
   void aDarkGanttTakesTheDarkSlotOne() throws Exception {
      ChartVSAssembly chart = chart(gantt(), VizMark.MODERN_DARK);

      drop(chart, date("Start"), ChartConstants.DROP_REGION_START);

      assertEquals(DARK_1, defaultColor(slot(chart, ChartConstants.DROP_REGION_START)));
   }

   @Test
   void aStartReplacedFromTheTreeTakesTheModernSlotOne() throws Exception {
      GanttVSChartInfo info = gantt();
      info.setStartField(measure("Begin", LEGACY_1));
      ChartVSAssembly chart = chart(info, VizMark.MODERN_LIGHT);

      drop(chart, date("Start"), ChartConstants.DROP_REGION_START);

      ChartAggregateRef start = slot(chart, ChartConstants.DROP_REGION_START);
      assertEquals("Start", start.getName());
      assertEquals(MODERN_1, defaultColor(start));
   }

   // the binding pane's move from one Gantt slot to another
   @Test
   void aStartMovedToMilestoneOnAMarkedGanttTakesTheModernSlotOne() throws Exception {
      GanttVSChartInfo info = gantt();
      VSChartAggregateRef start = measure("Start", LEGACY_1);
      info.setStartField(start);
      ChartVSAssembly chart = chart(info, VizMark.MODERN_LIGHT);

      move(chart, start, ChartConstants.DROP_REGION_START, ChartConstants.DROP_REGION_MILESTONE);

      assertNull(info.getStartField());
      assertSame(start, info.getMilestoneField());
      assertEquals(MODERN_1, defaultColor(start));
   }

   @Test
   void anAuthorColourSurvivesAMarkedMove() throws Exception {
      GanttVSChartInfo info = gantt();
      VSChartAggregateRef start = measure("Start", LEGACY_1);
      frame(start).setUserColor(AUTHOR);
      info.setStartField(start);
      ChartVSAssembly chart = chart(info, VizMark.MODERN_LIGHT);

      move(chart, start, ChartConstants.DROP_REGION_START, ChartConstants.DROP_REGION_MILESTONE);

      assertEquals(AUTHOR, frame(start).getColor());
   }

   @Test
   void aClassicDropKeepsTheLegacyColourAndItsFrameBytes() throws Exception {
      ChartVSAssembly chart = chart(gantt(), null);

      drop(chart, date("Start"), ChartConstants.DROP_REGION_START);

      ChartAggregateRef start = slot(chart, ChartConstants.DROP_REGION_START);
      assertEquals(LEGACY_1, defaultColor(start));
      assertEquals(xml(new VSChartAggregateRef().getColorFrameWrapper()),
                   xml(start.getColorFrameWrapper()));
   }

   // a classic Gantt drop never re-picked a colour
   @Test
   void aClassicMoveKeepsTheMovedFieldsColour() throws Exception {
      GanttVSChartInfo info = gantt();
      VSChartAggregateRef end = measure("End", LEGACY_3);
      info.setEndField(end);
      ChartVSAssembly chart = chart(info, null);

      move(chart, end, ChartConstants.DROP_REGION_END, ChartConstants.DROP_REGION_MILESTONE);

      assertSame(end, info.getMilestoneField());
      assertEquals(LEGACY_3, defaultColor(end));
   }

   private void drop(ChartVSAssembly chart, AssetEntry entry, int region) throws Exception {
      handler.addColumns(rvs, chart, new AssetEntry[] { entry }, target(region),
                         mock(CommandDispatcher.class), "/link");
   }

   private void move(ChartVSAssembly chart, ChartRef ref, int from, int to) throws Exception {
      // the binding pane reuses the dragged field, as pasteChartRef does
      when(chartRefService.pasteChartRef(any(), any())).thenReturn(ref);
      handler.addRemoveColumns(rvs, chart, transfer(from), target(to));
   }

   private ChartVSAssembly chart(VSChartInfo info, VizMark mark) {
      ChartVSAssembly chart = new ChartVSAssembly(viewsheet, "Chart1");
      chart.setVSChartInfo(info);
      chart.setChartDescriptor(new ChartDescriptor());
      chart.getVSAssemblyInfo().setVizMark(mark);
      return chart;
   }

   private static GanttVSChartInfo gantt() {
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setChartType(GraphTypes.CHART_GANTT);
      VSChartDimensionRef task = new VSChartDimensionRef(new AttributeRef(null, "Task"));
      task.setDataType(XSchema.STRING);
      task.setGroupColumnValue("Task");
      info.addYField(task);
      return info;
   }

   // a date column of the Tasks table, dropped as a measure
   private static AssetEntry date(String name) {
      AssetEntry entry = new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.COLUMN,
                                        "Tasks/" + name, null);
      entry.setProperty("assembly", "Tasks");
      entry.setProperty("dtype", XSchema.DATE);
      entry.setProperty(AssetEntry.CUBE_COL_TYPE, String.valueOf(AssetEntry.MEASURES));
      return entry;
   }

   private static VSChartAggregateRef measure(String name, Color color) {
      AttributeRef column = new AttributeRef(null, name);
      column.setDataType(XSchema.DATE);
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(column);
      ref.setColumnValue(name);
      frame(ref).setDefaultColor(color);
      return ref;
   }

   private static BindingDropTarget target(int region) {
      BindingDropTarget target = new BindingDropTarget();
      target.setDropType(String.valueOf(region));
      target.setDropIndex(0);
      return target;
   }

   private static ChartTransfer transfer(int region) {
      ChartRefModel model = mock(ChartRefModel.class);
      when(model.getOriginal()).thenReturn(new OriginalDescriptor(source(region)));
      ChartTransfer transfer = new ChartTransfer();
      transfer.setDragType(String.valueOf(region));
      transfer.setRefs(Collections.singletonList(model));
      return transfer;
   }

   private static String source(int region) {
      return switch(region) {
         case ChartConstants.DROP_REGION_START -> OriginalDescriptor.START;
         case ChartConstants.DROP_REGION_END -> OriginalDescriptor.END;
         default -> OriginalDescriptor.MILESTONE;
      };
   }

   private static ChartAggregateRef slot(ChartVSAssembly chart, int region) {
      GanttChartInfo info = (GanttChartInfo) chart.getVSChartInfo();
      ChartRef ref = switch(region) {
         case ChartConstants.DROP_REGION_START -> info.getStartField();
         case ChartConstants.DROP_REGION_END -> info.getEndField();
         default -> info.getMilestoneField();
      };

      assertInstanceOf(ChartAggregateRef.class, ref, "the slot is empty");
      return (ChartAggregateRef) ref;
   }

   private static StaticColorFrameWrapper frame(ChartAggregateRef ref) {
      return (StaticColorFrameWrapper) ref.getColorFrameWrapper();
   }

   private static Color defaultColor(ChartAggregateRef ref) {
      return frame(ref).getDefaultColor();
   }

   private static String xml(inetsoft.uql.viewsheet.graph.aesthetic.ColorFrameWrapper wrapper) {
      StringWriter out = new StringWriter();
      wrapper.writeXML(new PrintWriter(out, true));
      return out.toString();
   }

   private ChartRefModelFactoryService chartRefService;
   private VSChartBindingHandler handler;
   private Viewsheet viewsheet;
   private RuntimeViewsheet rvs;
}
