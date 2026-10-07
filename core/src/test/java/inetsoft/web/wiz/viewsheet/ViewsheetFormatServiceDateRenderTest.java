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
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.*;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.VOText;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.service.VSBindingService;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77597, findings #1/#2a/#2b, checked on rendered text: {@code set_format} runs through the
 * real {@link ViewsheetFormatService} and the real {@link FormatPainterService} write, then the
 * table cells are read off a real {@link VSFormatTableLens} and the chart data labels off a real
 * generated and laid-out {@link VGraph}. Before the fix the custom pattern was dropped, so the
 * date cells and labels rendered the {@code yyyy-MM-dd} default, and a whole-table date format
 * rendered numeric cells as {@code 1970-01-01}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceDateRenderTest {
   @BeforeEach
   void setUp() throws Exception {
      viewsheet = new Viewsheet();
      writeBox = mock(ViewsheetSandbox.class);
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

   /** #2a: a whole-table custom date pattern reaches the date column. */
   @Test
   void aWholeTableCustomDatePatternRendersOnTheDateColumn() throws Exception {
      table(false);

      setFormat("{\"assemblies\":[\"TableView1\"],\"format\":" + MMM_DD + "}");

      assertEquals("Jan 05, 2026", render(1, 0), "not the yyyy-MM-dd default 2026-01-05");
      assertEquals("Widget", render(1, 1));
   }

   /** Named styles are matched ignoring case and render as that style. */
   @Test
   void aLowercaseNamedDateStyleRendersThatStyle() throws Exception {
      table(false);

      setFormat("{\"assemblies\":[\"TableView1\"],\"format\":" +
                "{\"format\":\"DateFormat\",\"formatSpec\":\"medium\"}}");

      assertEquals("Jan 5, 2026", render(1, 0));
   }

   /** #1: refused before anything is stored, so the numbers still render as numbers. */
   @Test
   void aWholeTableDateFormatOverNumericColumnsIsRefusedAndTheyStillRenderAsNumbers()
      throws Exception
   {
      TableVSAssembly table = table(true);

      IllegalArgumentException refused = assertThrows(
         IllegalArgumentException.class,
         () -> setFormat("{\"assemblies\":[\"TableView1\"],\"format\":" + MMM_DD + "}"));

      assertTrue(refused.getMessage().contains("Revenue, Unit Price"), refused.getMessage());
      assertTrue(refused.getMessage().contains("(Order Date)"), refused.getMessage());
      assertNull(table.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
                    .getUserDefinedFormat().getFormatValue());
      assertEquals("3600", render(1, 2), "not 1970-01-01");
      assertEquals("29.99", render(1, 3), "not 1970-01-01");
   }

   /** #1: the route the refusal names formats the date column only. */
   @Test
   void theAdvisedFieldScopedRouteFormatsOnlyTheDateColumn() throws Exception {
      table(true);

      setFormat("{\"assemblies\":[\"TableView1\"],\"target\":\"data\",\"field\":\"Order Date\"," +
                "\"format\":" + MMM_DD + "}");

      assertEquals("Jan 05, 2026", render(1, 0));
      assertEquals("3600", render(1, 2));
      assertEquals("29.99", render(1, 3));
   }

   /** #1: under target object, numeric columns with their own number format are exempt. */
   @Test
   void numericColumnsWithTheirOwnNumberFormatAreExemptAndKeepIt() throws Exception {
      TableVSAssembly table = table(true);

      for(int col : new int[] { 2, 3 }) {
         VSCompositeFormat own = new VSCompositeFormat();
         own.getUserDefinedFormat().setFormatValue("CurrencyFormat", true);
         table.getFormatInfo().setFormat(raw.getDescriptor().getCellDataPath(1, col), own);
      }

      setFormat("{\"assemblies\":[\"TableView1\"],\"format\":" + MMM_DD + "}");

      assertEquals("Jan 05, 2026", render(1, 0));
      assertEquals("$3,600.00", render(1, 2));
      assertEquals("$29.99", render(1, 3));
   }

   /** #2b: chart data labels bound to a Month date field render the requested pattern. */
   @Test
   void chartDataLabelsRenderTheCustomDatePattern() throws Exception {
      ChartVSAssembly chart = labelledChart();
      assertEquals(List.of("2026 Jan", "2026 Feb"), dataLabels(chart), "the date-level default");

      setFormat("{\"assemblies\":[\"Chart1\"],\"target\":\"text\",\"field\":\"Month(Order Date)\"," +
                "\"format\":{\"format\":\"DateFormat\",\"formatSpec\":\"MM/dd/yyyy\"}}");

      assertEquals(List.of("01/01/2026", "02/01/2026"), dataLabels(chart),
                   "not the yyyy-MM-dd default 2026-01-01");
   }

   /**
    * Review round 1: through the real painter, a whole-object value format to a Table raises
    * "Format applied to a string column" whatever the columns are (OBJECTPATH's data type is
    * the constructor default "string"). The response now carries it only when it is true.
    */
   @Test
   void theStringColumnWarningIsAbsentWhenTheTableHasNoStringColumn() throws Exception {
      TableVSAssembly table = new TableVSAssembly(viewsheet, "TableView1");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("Revenue", XSchema.INTEGER));
      columns.addAttribute(column("Unit Price", XSchema.DOUBLE));
      table.setColumnSelection(columns);
      viewsheet.addAssembly(table);

      ViewsheetFormatService.FormatResult result = setFormatResult(
         "{\"assemblies\":[\"TableView1\"],\"format\":" +
         "{\"format\":\"DecimalFormat\",\"formatSpec\":\"#,##0\"}}");

      assertEquals(List.of(), result.warnings());
      assertEquals("DecimalFormat", table.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getUserDefinedFormat().getFormatValue(), "the format itself was still applied");
   }

   @Test
   void theStringColumnWarningIsKeptWhenTheTableHasAStringColumn() throws Exception {
      table(false);

      ViewsheetFormatService.FormatResult result = setFormatResult(
         "{\"assemblies\":[\"TableView1\"],\"format\":" + MMM_DD + "}");

      String warning = inetsoft.util.Catalog.getCatalog(() -> "admin")
         .getString("composer.stringColumnFormat");
      assertEquals(List.of(warning), result.warnings());
   }

   private void setFormat(String json) throws Exception {
      setFormatResult(json);
   }

   private ViewsheetFormatService.FormatResult setFormatResult(String json) throws Exception {
      return service.setFormat("tok", () -> "admin",
                               new ObjectMapper().readValue(
                                  json, ViewsheetFormatService.FormatRequest.class),
                               "");
   }

   private TableVSAssembly table(boolean numeric) throws Exception {
      TableVSAssembly table = new TableVSAssembly(viewsheet, "TableView1");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("Order Date", XSchema.DATE));
      columns.addAttribute(column("Product", XSchema.STRING));

      if(numeric) {
         columns.addAttribute(column("Revenue", XSchema.INTEGER));
         columns.addAttribute(column("Unit Price", XSchema.DOUBLE));
         raw = new DefaultTableLens(new Object[][] {
            { "Order Date", "Product", "Revenue", "Unit Price" },
            { date(2026, Calendar.JANUARY, 5), "Widget", 3600, 29.99 } });
      }
      else {
         raw = new DefaultTableLens(new Object[][] {
            { "Order Date", "Product" }, { date(2026, Calendar.JANUARY, 5), "Widget" } });
      }

      table.setColumnSelection(columns);
      viewsheet.addAssembly(table);
      // the live lens the data/header targets compute their cell paths from
      when(writeBox.getVSTableLens(eq("TableView1"), anyBoolean()))
         .thenReturn(new VSTableLens(raw));
      return table;
   }

   /** The formatted text of a cell, as the table renderer produces it. */
   private String render(int row, int col) {
      ViewsheetSandbox box = new ViewsheetSandbox(
         viewsheet, RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE, null,
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                        "test/ViewsheetFormatServiceDateRenderTest", null,
                        OrganizationManager.getInstance().getCurrentOrgID()));
      VSFormatTableLens lens = new VSFormatTableLens(box, "TableView1", raw, true);
      return String.valueOf(lens.getObject(row, col));
   }

   /** Bar chart, x = Region, y = Sum(Revenue), data labels (text) = Month(Order Date). */
   private ChartVSAssembly labelledChart() {
      ChartVSAssembly chart = new ChartVSAssembly(viewsheet, "Chart1");
      viewsheet.addAssembly(chart);
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      chart.setVSChartInfo(info);

      VSChartAggregateRef revenue = new VSChartAggregateRef();
      revenue.setDataRef(new AttributeRef("Revenue"));
      revenue.setFormula(AggregateFormula.SUM);
      AttributeRef orderDate = new AttributeRef("Order Date");
      orderDate.setDataType(XSchema.DATE);
      VSChartDimensionRef month = new VSChartDimensionRef(orderDate);
      month.setDataType(XSchema.DATE);
      month.setDateLevelValue("" + DateRangeRef.MONTH_INTERVAL);
      month.setDateLevel(DateRangeRef.MONTH_INTERVAL);
      VSAestheticRef text = new VSAestheticRef();
      text.setDataRef(month);
      text.setVisualFrame(new DefaultTextFrame());

      info.addXField(new VSChartDimensionRef(new AttributeRef("Region")));
      info.addYField(revenue);
      info.setTextField(text);
      assertEquals("Month(Order Date)", month.getFullName());
      return chart;
   }

   /** The data-label text of the real generated and laid-out VGraph. */
   private static List<String> dataLabels(ChartVSAssembly chart) {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      // the runtime binding a refresh rebuilds (the painter clears it after a write)
      cinfo.setRTXFields(cinfo.getXFields());
      cinfo.setRTYFields(cinfo.getYFields());
      VSAestheticRef text = (VSAestheticRef) cinfo.getTextField();
      text.setRTDataRef((VSDataRef) text.getDataRef());

      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "Region", "Sum(Revenue)", "Month(Order Date)" },
         { "East", 2880.0, date(2026, Calendar.JANUARY, 1) },
         { "West", 3600.0, date(2026, Calendar.FEBRUARY, 1) } });
      GraphGenerator gen = GraphGenerator.getGenerator(info, null, data, new VariableTable(),
                                                       null, 0, new Dimension(400, 300));
      EGraph egraph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(gen.getData(), 0, 0, 400, 300);
      List<String> labels = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof ElementVO element && element.getVOTexts() != null) {
            for(VOText label : element.getVOTexts()) {
               if(label != null) {
                  labels.add(label.getText());
               }
            }
         }
      }

      return labels;
   }

   private static ColumnRef column(String name, String type) {
      AttributeRef attribute = new AttributeRef(null, name);
      attribute.setDataType(type);
      ColumnRef column = new ColumnRef(attribute);
      column.setDataType(type);
      return column;
   }

   private static Date date(int year, int month, int day) {
      Calendar calendar = Calendar.getInstance();
      calendar.clear();
      calendar.set(year, month, day);
      return calendar.getTime();
   }

   private static final String MMM_DD = "{\"format\":\"DateFormat\",\"formatSpec\":\"MMM dd, yyyy\"}";

   private Viewsheet viewsheet;
   private ViewsheetSandbox writeBox;
   private ViewsheetFormatService service;
   private DefaultTableLens raw;
}
