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

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphFormatUtil;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.wiz.binding.CalcTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77597 (#3, #2c): {@code set_format} with {@code target: "field"} must change the tick
 * labels the chart actually renders, and a reset must bring the default labels back. Drives the
 * real service and the real {@link VSWizardBindingHandler}, then builds the real VGraph
 * ({@code GraphGenerator} + {@code Plotter.plotAndLayout}) and reads each axis's label text.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceFieldTargetRenderTest {
   private static final List<String> K_TICKS =
      List.of("0K", "1K", "2K", "3K", "4K", "5K", "6K", "7K");
   private static final List<String> DOLLAR_TICKS =
      List.of("$0", "$1,000", "$2,000", "$3,000", "$4,000", "$5,000", "$6,000", "$7,000");

   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      chart = new ChartVSAssembly(viewsheet, "Chart1");
      viewsheet.addAssembly(chart);
      cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      chart.setVSChartInfo(cinfo);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      sessions = sessionsFor(rvs);
   }

   /** #3: the measure axis renders the set number format, and the reset brings 0K…7K back. */
   @Test
   void aFieldNumberFormatRendersOnTheMeasureAxisAndAResetRestoresTheDefault() throws Exception {
      cinfo.addXField(dimension("Region", XSchema.STRING, -1));
      cinfo.addYField(aggregate("Revenue"));
      cinfo.addYField(aggregate("Cost"));
      DataSet data = new DefaultDataSet(new Object[][] {
         { "Region", "Sum(Revenue)", "Sum(Cost)" }, { "East", 1200.0, 900.0 },
         { "West", 6800.0, 4100.0 }, { "North", 3500.0, 2000.0 }
      });
      bind();
      assertEquals(K_TICKS, ticks(data, "Sum(Revenue)"));
      List<String> costTicks = ticks(data, "Sum(Cost)");

      service().setFormat("tok", principal(), field("Sum(Revenue)", dollars()), "");

      assertEquals(DOLLAR_TICKS, ticks(data, "Sum(Revenue)"));
      assertEquals(costTicks, ticks(data, "Sum(Cost)"), "the other measure axis is unchanged");
      assertEquals(List.of("East", "West", "North"), ticks(data, "Region"));

      service().setFormat("tok", principal(), reset("Sum(Revenue)"), "");

      assertEquals(K_TICKS, ticks(data, "Sum(Revenue)"));
      assertEquals(costTicks, ticks(data, "Sum(Cost)"));
   }

   /** Not separated: one shared value axis takes the set, and a reset of the other measure clears it. */
   @Test
   void onANonSeparatedChartTheSharedValueAxisRendersTheSetAndTheReset() throws Exception {
      cinfo.addXField(dimension("Region", XSchema.STRING, -1));
      cinfo.addYField(aggregate("Revenue"));
      cinfo.addYField(aggregate("Cost"));
      cinfo.setSeparatedGraph(false);
      DataSet data = new DefaultDataSet(new Object[][] {
         { "Region", "Sum(Revenue)", "Sum(Cost)" }, { "East", 1200.0, 900.0 },
         { "West", 6800.0, 4100.0 }, { "North", 3500.0, 2000.0 }
      });
      bind();
      assertEquals(K_TICKS, ticks(data, "Sum(Revenue)"));

      ViewsheetFormatService.FormatResult set =
         service().setFormat("tok", principal(), field("Sum(Revenue)", dollars()), "");
      assertEquals(DOLLAR_TICKS, ticks(data, "Sum(Revenue)"));
      assertEquals(1, set.warnings().size(), set.warnings().toString());

      service().setFormat("tok", principal(), reset("Sum(Cost)"), "");
      assertEquals(K_TICKS, ticks(data, "Sum(Revenue)"));
   }

   /** #2c: a custom date pattern on a full-level date dimension renders on the X axis. */
   @Test
   void aFieldDateFormatRendersOnAFullLevelDateAxisAndAResetRestoresTheDefault()
      throws Exception
   {
      VSChartDimensionRef month =
         dimension("Order Date", XSchema.DATE, DateRangeRef.MONTH_INTERVAL);
      cinfo.addXField(month);
      cinfo.addYField(aggregate("Revenue"));
      String name = month.getFullName();
      DataSet data = new DefaultDataSet(new Object[][] {
         { name, "Sum(Revenue)" },
         { date(2026, Calendar.JANUARY), 1200.0 }, { date(2026, Calendar.FEBRUARY), 6800.0 },
         { date(2026, Calendar.MARCH), 3500.0 }
      });
      bind();
      List<String> defaults = ticks(data, name);

      service().setFormat("tok", principal(), field(name, monthYear()), "");
      assertEquals(List.of("Jan 2026", "Feb 2026", "Mar 2026"), ticks(data, name));

      service().setFormat("tok", principal(), reset(name), "");
      assertEquals(defaults, ticks(data, name));
   }

   // ── helpers ─────────────────────────────────────────────────────────────────────────────

   // the binding-time default number format (the abbreviated 0K…7K)
   private void bind() {
      GraphFormatUtil.fixDefaultNumberFormat(chartInfo().getChartDescriptor(), cinfo);
   }

   private ChartVSAssemblyInfo chartInfo() {
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   // the label text of every axis over field, in the real generated and laid-out VGraph
   private List<String> ticks(DataSet data, String field) throws Exception {
      Method fix = VGraphPair.class.getDeclaredMethod("fixChartFormat", ChartVSAssemblyInfo.class);
      fix.setAccessible(true);
      fix.invoke(new VGraphPair(), chartInfo());
      VSChartInfo info = chart.getVSChartInfo();
      info.setRTXFields(info.getXFields());
      info.setRTYFields(info.getYFields());
      GraphGenerator gen = GraphGenerator.getGenerator(chartInfo(), null, data,
                                                       new VariableTable(), null, 0,
                                                       new Dimension(400, 300));
      EGraph egraph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(gen.getData(), 0, 0, 400, 300);
      List<String> labels = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(true)) {
         if(Arrays.asList(axis.getScale().getFields()).contains(field)) {
            Arrays.stream(axis.getLabels()).filter(Objects::nonNull)
               .forEach(label -> labels.add(((VLabel) label).getText()));
         }
      }

      assertFalse(labels.isEmpty(), "the " + field + " axis should render labels");
      return labels;
   }

   private ViewsheetFormatService service() throws Exception {
      return new ViewsheetFormatService(sessions, mock(FormatPainterService.class),
                                        mock(CalcTableService.class), realBindingHandler());
   }

   private static VSWizardBindingHandler realBindingHandler() throws Exception {
      Constructor<?> ctor = VSWizardBindingHandler.class.getConstructors()[0];
      Object[] args = new Object[ctor.getParameterCount()];

      for(int i = 0; i < args.length; i++) {
         args[i] = mock(ctor.getParameterTypes()[i]);
      }

      return (VSWizardBindingHandler) ctor.newInstance(args);
   }

   private static ViewsheetFormatService.FormatRequest field(String field,
                                                              VSObjectFormatInfoModel format)
   {
      return new ViewsheetFormatService.FormatRequest(List.of("Chart1"), format, false, "field",
                                                      field);
   }

   private static ViewsheetFormatService.FormatRequest reset(String field) {
      return new ViewsheetFormatService.FormatRequest(List.of("Chart1"), null, true, "field",
                                                      field);
   }

   private static VSObjectFormatInfoModel dollars() {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setFormat("DecimalFormat");
      format.setFormatSpec("'$'#,##0");
      return format;
   }

   private static VSObjectFormatInfoModel monthYear() {
      VSObjectFormatInfoModel format = new VSObjectFormatInfoModel();
      format.setFormat("DateFormat");
      format.setDateSpec("Custom");
      format.setFormatSpec("MMM yyyy");
      return format;
   }

   private static Date date(int year, int month) {
      Calendar calendar = Calendar.getInstance();
      calendar.clear();
      calendar.set(year, month, 1);
      return calendar.getTime();
   }

   private static VSChartAggregateRef aggregate(String column) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, column));
      ref.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef aggregate = new VSChartAggregateRef();
      aggregate.setDataRef(ref);
      aggregate.setColumnValue(column);
      aggregate.setFormulaValue("Sum");
      return aggregate;
   }

   private static VSChartDimensionRef dimension(String column, String type, int level) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, column));
      ref.setDataType(type);
      VSChartDimensionRef dimension = new VSChartDimensionRef();
      dimension.setDataRef(ref);
      dimension.setGroupColumnValue(column);

      if(level >= 0) {
         dimension.setDateLevelValue(String.valueOf(level));
         dimension.setDateLevel(level);
      }

      return dimension;
   }

   private Viewsheet viewsheet;
   private ChartVSAssembly chart;
   private VSChartInfo cinfo;
   private ViewsheetSessionService sessions;
}
