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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
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

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.principal;
import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.sessionsFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77597, part C: {@code set_format} with {@code target: "field"} on a real chart binding,
 * through the real {@link VSWizardBindingHandler#applyFieldFormats}. Real chart refs need the
 * SREE context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceFieldTargetTest {
   @BeforeEach
   void setUp() throws Exception {
      viewsheet = new Viewsheet();
      chart = new ChartVSAssembly(viewsheet, "Chart1");
      viewsheet.addAssembly(chart);
      info = new DefaultVSChartInfo();
      chart.setVSChartInfo(info);

      box = mock(ViewsheetSandbox.class);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      handler = realBindingHandler();
   }

   /**
    * #3 and its reset: set Sum(Revenue)'s value format, then reset it. Both the axis-wide label
    * format and the per-column label format carry it, and both are cleared again. Each write also
    * drops the cached chart descriptor and graph, or the next render would serve the old axis.
    */
   @Test
   void setThenResetAFieldFormatOnASeparatedChart() throws Exception {
      VSChartAggregateRef revenue = aggregate("Revenue");
      info.addXField(dimension("Region", XSchema.STRING, -1));
      info.addYField(revenue);
      info.setSeparatedGraph(true);
      revenue.getAxisDescriptor().setColumnLabelTextFormat(revenue.getFullName(),
                                                           new CompositeTextFormat());
      ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      chartInfo.setRTChartDescriptor(new ChartDescriptor());

      ViewsheetFormatService.FormatResult set = service().setFormat(
         "tok", principal(), fieldRequest(revenue.getFullName(), dollars(), false), "");

      assertEquals(List.of(), set.warnings());
      assertEquals("DecimalFormat", axisUserFormat(revenue).getFormat());
      assertEquals("'$'#,##0", axisUserFormat(revenue).getFormatSpec());
      assertEquals("DecimalFormat", columnUserFormat(revenue).getFormat());
      assertNull(chartInfo.getRTChartDescriptor(), "the cached chart descriptor is dropped");
      verify(box, times(1)).clearGraph("Chart1");

      chartInfo.setRTChartDescriptor(new ChartDescriptor());
      service().setFormat("tok", principal(),
                          fieldRequest(revenue.getFullName(), null, true), "");

      assertNull(axisUserFormat(revenue).getFormat());
      assertNull(axisUserFormat(revenue).getFormatSpec());
      assertNull(columnUserFormat(revenue).getFormat());
      assertNull(chartInfo.getRTChartDescriptor());
      verify(box, times(2)).clearGraph("Chart1");
   }

   /** Separated chart: resetting a field that has no format of its own changes nothing else. */
   @Test
   void resettingAnUnformattedFieldIsAHarmlessNoOpOnASeparatedChart() throws Exception {
      VSChartAggregateRef revenue = aggregate("Revenue");
      VSChartAggregateRef cost = aggregate("Cost");
      info.addYField(revenue);
      info.addYField(cost);
      info.setSeparatedGraph(true);

      service().setFormat("tok", principal(),
                          fieldRequest(revenue.getFullName(), dollars(), false), "");
      ViewsheetFormatService.FormatResult reset = service().setFormat(
         "tok", principal(), fieldRequest(cost.getFullName(), null, true), "");

      assertEquals(List.of(), reset.warnings());
      assertNull(axisUserFormat(cost).getFormat());
      assertEquals("DecimalFormat", axisUserFormat(revenue).getFormat(),
                   "Revenue keeps its own format");
   }

   /**
    * Not separated: the measures share the chart's value-axis descriptor, so resetting Sum(Cost)
    * clears the shared axis format Sum(Revenue) set -- the actual (wizard) behaviour -- and the
    * result says so.
    */
   @Test
   void onANonSeparatedChartAFieldResetClearsTheSharedAxisAndWarns() throws Exception {
      VSChartAggregateRef revenue = aggregate("Revenue");
      VSChartAggregateRef cost = aggregate("Cost");
      info.addXField(dimension("Region", XSchema.STRING, -1));
      info.addYField(revenue);
      info.addYField(cost);
      info.setSeparatedGraph(false);

      ViewsheetFormatService.FormatResult set = service().setFormat(
         "tok", principal(), fieldRequest(revenue.getFullName(), dollars(), false), "");
      assertEquals("DecimalFormat",
                   info.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat()
                      .getFormat().getFormat());
      assertEquals(1, set.warnings().size(), set.warnings().toString());

      ViewsheetFormatService.FormatResult reset = service().setFormat(
         "tok", principal(), fieldRequest(cost.getFullName(), null, true), "");

      assertTrue(info.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat()
                    .getFormat().isEmpty(), "the shared axis format is cleared");
      assertEquals(1, reset.warnings().size(), reset.warnings().toString());
      String warning = reset.warnings().get(0);
      assertTrue(warning.contains(cost.getFullName()) && warning.contains(revenue.getFullName()) &&
                 warning.contains("reset"), warning);
   }

   /** #2c: MonthOfYear renders integers, so a date format is refused; Month renders dates. */
   @Test
   void aDateFormatIsRefusedOnAPartLevelDateDimensionAndAllowedOnAFullLevel() throws Exception {
      VSChartDimensionRef monthOfYear =
         dimension("Order Date", XSchema.DATE, DateRangeRef.MONTH_OF_YEAR_PART);
      info.addXField(monthOfYear);
      info.addYField(aggregate("Revenue"));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service().setFormat("tok", principal(),
                                   fieldRequest(monthOfYear.getFullName(), monthYear(), false),
                                   ""));
      assertTrue(thrown.getMessage().contains(XSchema.INTEGER), thrown.getMessage());

      VSChartDimensionRef month = dimension("Order Date", XSchema.DATE, DateRangeRef.MONTH_INTERVAL);
      info.removeXField(0);
      info.addXField(month);

      service().setFormat("tok", principal(), fieldRequest(month.getFullName(), monthYear(), false),
                          "");

      XFormatAssert.user(month, "DateFormat", "MMM yyyy");
   }

   @Test
   void anUnboundFieldIsRefusedWithTheBindableFields() {
      info.addYField(aggregate("Revenue"));
      ViewsheetSessionService sessions = sessionsFor(rvs);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service(sessions).setFormat("tok", principal(),
                                           fieldRequest("Sum(Profit)", dollars(), false), ""));

      assertTrue(thrown.getMessage().contains("Sum(Profit)") &&
                 thrown.getMessage().contains("Sum(Revenue)"), thrown.getMessage());

      try {
         verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   /** The handler's documented "a null value clears" now holds (a HashMap carries the null). */
   @Test
   void applyFieldFormatsClearsAFieldGivenANullValue() {
      VSChartAggregateRef revenue = aggregate("Revenue");
      info.addYField(revenue);
      info.setSeparatedGraph(true);
      VSFormat dollars = new VSFormat();
      dollars.setFormatValue("DecimalFormat");
      dollars.setFormatExtentValue("'$'#,##0");
      handler.applyFieldFormats(rvs, chart, Map.of(revenue.getFullName(), dollars));
      assertEquals("DecimalFormat", axisUserFormat(revenue).getFormat());

      Map<String, VSFormat> clear = new HashMap<>();
      clear.put(revenue.getFullName(), null);

      assertEquals(java.util.Set.of(), handler.applyFieldFormats(rvs, chart, clear));
      assertNull(axisUserFormat(revenue).getFormat());
   }

   /** WizAutoBindingService shares the check: a numeric format now fits a part level too. */
   @Test
   void theSharedTypeCheckJudgesByEffectiveType() {
      VSChartDimensionRef monthOfYear =
         dimension("Order Date", XSchema.DATE, DateRangeRef.MONTH_OF_YEAR_PART);
      List<ChartRef> refs = List.of(monthOfYear);
      VSFormat decimal = new VSFormat();
      decimal.setFormatValue("DecimalFormat");
      VSFormat date = new VSFormat();
      date.setFormatValue("DateFormat");

      WizFormatChecks.checkFormatFitsFieldType(monthOfYear.getFullName(), decimal, refs);
      assertThrows(IllegalArgumentException.class,
                   () -> WizFormatChecks.checkFormatFitsFieldType(monthOfYear.getFullName(),
                                                                  date, refs));
   }

   // ── helpers ─────────────────────────────────────────────────────────────────────────────

   private ViewsheetFormatService service() {
      return service(sessionsFor(rvs));
   }

   private ViewsheetFormatService service(ViewsheetSessionService sessions) {
      return new ViewsheetFormatService(sessions, mock(FormatPainterService.class),
                                        mock(CalcTableService.class), handler);
   }

   /** The real handler; none of its collaborators is reached by applyFieldFormats. */
   private static VSWizardBindingHandler realBindingHandler() throws Exception {
      Constructor<?> ctor = VSWizardBindingHandler.class.getConstructors()[0];
      Object[] args = new Object[ctor.getParameterCount()];

      for(int i = 0; i < args.length; i++) {
         args[i] = mock(ctor.getParameterTypes()[i]);
      }

      return (VSWizardBindingHandler) ctor.newInstance(args);
   }

   private static ViewsheetFormatService.FormatRequest fieldRequest(
      String field, VSObjectFormatInfoModel format, boolean reset)
   {
      return new ViewsheetFormatService.FormatRequest(List.of("Chart1"), format, reset, "field",
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

   private static inetsoft.uql.XFormatInfo axisUserFormat(ChartRef ref) {
      return ref.getAxisDescriptor().getAxisLabelTextFormat().getUserDefinedFormat().getFormat();
   }

   private static inetsoft.uql.XFormatInfo columnUserFormat(ChartRef ref) {
      return ref.getAxisDescriptor().getColumnLabelTextFormat(ref.getFullName())
         .getUserDefinedFormat().getFormat();
   }

   /** Assertions on a dimension's own axis user format. */
   private static final class XFormatAssert {
      static void user(ChartRef ref, String type, String spec) {
         assertEquals(type, axisUserFormat(ref).getFormat());
         assertEquals(spec, axisUserFormat(ref).getFormatSpec());
      }
   }

   private Viewsheet viewsheet;
   private ChartVSAssembly chart;
   private VSChartInfo info;
   private ViewsheetSandbox box;
   private RuntimeViewsheet rvs;
   private VSWizardBindingHandler handler;
}
