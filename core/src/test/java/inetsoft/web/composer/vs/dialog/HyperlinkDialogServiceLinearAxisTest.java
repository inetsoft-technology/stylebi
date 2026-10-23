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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.service.HighlightService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79269: on the wiz path (rejectDerivedMeasure) an axis link on a measure whose axis is a
 * linear scale is refused, since a linear axis has no label area to carry it and the link would
 * silently land on the data points only. Every case goes through the real
 * HyperlinkDialogService on a real chart; whatever is not provably a linear axis (dimensions,
 * date-typed measures, merged and polar charts) must still be accepted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HyperlinkDialogServiceLinearAxisTest {
   private static final Principal USER = () -> "admin";
   // the shape VHL-023 asserts on
   private static final Pattern MEASURE_AXIS = Pattern.compile(
      "\\bmeasure\\b[^.]*\\baxis\\b|\\baxis\\b[^.]*\\bmeasure\\b", Pattern.CASE_INSENSITIVE);

   private static class Fixture {
      HyperlinkDialogService service;
      VSObjectPropertyService props;
      VSChartInfo chartInfo;
   }

   private static VSChartDimensionRef dim(String column) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(column));
      dim.setGroupColumnValue(column);
      return dim;
   }

   /** What set_chart_shelf produces: aggregated, formula Sum. */
   private static VSChartAggregateRef sum(String column) {
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef(column));
      measure.setColumnValue(column);
      measure.setFormula(AggregateFormula.SUM);
      return measure;
   }

   private static VSChartAggregateRef plain(String column, String dataType) {
      ColumnRef col = new ColumnRef(new AttributeRef(column));
      col.setDataType(dataType);
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(col);
      measure.setColumnValue(column);
      measure.setFormula(AggregateFormula.NONE);
      measure.setAggregated(false);
      return measure;
   }

   private static Fixture chart(VSChartInfo chartInfo, VSChartDimensionRef[] xs,
                                VSChartAggregateRef... ys) throws Exception
   {
      chartInfo.setChartDescriptor(new ChartDescriptor());

      for(VSChartDimensionRef x : xs) {
         chartInfo.addXField(x);
      }

      for(VSChartAggregateRef y : ys) {
         chartInfo.addYField(y);
      }

      chartInfo.setRTXFields(chartInfo.getXFields());
      chartInfo.setRTYFields(chartInfo.getYFields());

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly assembly = new ChartVSAssembly(vs, "Chart1");
      assembly.setVSChartInfo(chartInfo);
      vs.addAssembly(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);

      Fixture f = new Fixture();
      f.chartInfo = chartInfo;
      f.props = mock(VSObjectPropertyService.class);
      f.service = new HyperlinkDialogService(null, f.props, viewsheetService,
                                             mock(DataRefModelFactoryService.class), null, null,
                                             mock(HighlightService.class), null);
      return f;
   }

   /** The VHL-023 chart: bar, x = REGION, y = Sum(REVENUE). */
   private static Fixture bar() throws Exception {
      return chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                   sum("REVENUE"));
   }

   private static HyperlinkDialogModel model(String colName, boolean axis, boolean reject) {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setColName(colName);
      model.setAxis(axis);
      model.setLinkType(Hyperlink.WEB_LINK);
      model.setWebLink("https://example.com/x");
      model.setRejectDerivedMeasure(reject);
      return model;
   }

   private static void set(Fixture f, HyperlinkDialogModel model) throws Exception {
      f.service.setHyperlinkDialogModel("rt", "Chart1", model, "", USER, null);
   }

   private static void assertRefused(Fixture f, String colName, String field) throws Exception {
      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> set(f, model(colName, true, true)));
      String msg = e.getMessage();
      assertTrue(msg.contains("'" + field + "'") && MEASURE_AXIS.matcher(msg).find(), msg);
      assertTrue(Pattern.compile("(?<!\\w)" + Pattern.quote(field) + "(?!\\w)").matcher(msg).find(),
                 msg);
      assertFalse(msg.toLowerCase().contains("only a dimension"), msg);
      assertNothingWritten(f);
   }

   private static void assertAccepted(Fixture f, HyperlinkDialogModel model) throws Exception {
      assertDoesNotThrow(() -> set(f, model));
      verify(f.props).editObjectProperty(any(), any(), anyString(), anyString(), any(), any(),
                                         any(), anyBoolean(), any());
   }

   private static void assertNothingWritten(Fixture f) throws Exception {
      verify(f.props, never()).editObjectProperty(any(), any(), anyString(), anyString(), any(),
                                                  any(), any(), anyBoolean(), any());

      for(ChartRef ref : f.chartInfo.getYFields()) {
         assertNull(((HyperlinkRef) ref).getHyperlink(), "stored link state must be unchanged");
      }
   }

   @Test
   void axisLinkOnAnAggregatedMeasureIsRefused() throws Exception {
      assertRefused(bar(), "REVENUE", "REVENUE");
   }

   @Test
   void theFullNameFormIsRefusedToo() throws Exception {
      Fixture f = bar();
      String full = f.chartInfo.getYFields()[0].getFullName();
      assertRefused(f, full, full);
   }

   @Test
   void sameCallIsAcceptedOnTheGuiPath() throws Exception {
      assertAccepted(bar(), model("REVENUE", true, false));
   }

   @Test
   void dataPointLinkOnTheMeasureIsAccepted() throws Exception {
      assertAccepted(bar(), model("REVENUE", false, true));
   }

   @Test
   void axisLinkOnADimensionIsAccepted() throws Exception {
      assertAccepted(bar(), model("REGION", true, true));
   }

   @Test
   void clearingIsAlwaysAccepted() throws Exception {
      HyperlinkDialogModel m = model("REVENUE", true, true);
      m.setLinkType(HyperlinkDialogService.NONE);
      assertAccepted(bar(), m);
   }

   @Test
   void nameOnBothShelvesIsAcceptedBecauseADimensionIsTouched() throws Exception {
      Fixture f = chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REVENUE") },
                        sum("REVENUE"));
      assertAccepted(f, model("REVENUE", true, true));
   }

   @Test
   void discreteMeasureIsAccepted() throws Exception {
      VSChartAggregateRef m = sum("REVENUE");
      m.setDiscrete(true);
      assertAccepted(chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                           m), model("REVENUE", true, true));
   }

   @Test
   void unaggregatedDateMeasureIsAccepted() throws Exception {
      assertAccepted(chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                           plain("ORDER_DATE", XSchema.DATE)),
                     model("ORDER_DATE", true, true));
   }

   @Test
   void unaggregatedStringMeasureIsAccepted() throws Exception {
      assertAccepted(chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                           plain("NOTE", XSchema.STRING)), model("NOTE", true, true));
   }

   @Test
   void unaggregatedNumericMeasureIsRefused() throws Exception {
      assertRefused(chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                          plain("QTY", XSchema.INTEGER)), "QTY", "QTY");
   }

   @Test
   void logAxisMeasureIsRefused() throws Exception {
      Fixture f = chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                        plain("NOTE", XSchema.STRING));
      f.chartInfo.setSeparatedGraph(false);
      f.chartInfo.getAxisDescriptor().setLogarithmicScale(true);
      assertRefused(f, "NOTE", "NOTE");
   }

   @Test
   void twoMeasuresInANonSeparatedGraphAreRefused() throws Exception {
      Fixture f = chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                        plain("A", XSchema.STRING), plain("B", XSchema.STRING));
      f.chartInfo.setSeparatedGraph(false);
      assertRefused(f, "A", "A");
   }

   @Test
   void twoUnaggregatedStringMeasuresInASeparatedGraphAreAccepted() throws Exception {
      Fixture f = chart(new DefaultVSChartInfo(), new VSChartDimensionRef[] { dim("REGION") },
                        plain("A", XSchema.STRING), plain("B", XSchema.STRING));
      f.chartInfo.setSeparatedGraph(true);
      assertAccepted(f, model("A", true, true));
   }

   @Test
   void emptyOrUnknownRefSetFailsOpen() throws Exception {
      Fixture f = bar();
      assertFalse(HyperlinkDialogService.isLinearMeasureAxisLink(f.chartInfo, new ChartRef[0]));
      assertFalse(HyperlinkDialogService.isLinearMeasureAxisLink(f.chartInfo, null));

      // an unknown name is refused by the existing field check, not by the new guard
      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> set(f, model("NOPE", true, true)));
      assertFalse(e.getMessage().contains("linear axis"), e.getMessage());
   }

   private static void assertTypeSkipped(int type, boolean rtOnly) throws Exception {
      Fixture f = bar();

      if(rtOnly) {
         f.chartInfo.setRTChartType(type);
      }
      else {
         f.chartInfo.setChartType(type);
      }

      assertAccepted(f, model("REVENUE", true, true));
   }

   @Test
   void mergedAndPolarChartTypesAreSkippedOnEitherTypeField() throws Exception {
      for(int type : new int[] { GraphTypes.CHART_MEKKO, GraphTypes.CHART_FUNNEL,
                                 GraphTypes.CHART_BOXPLOT, GraphTypes.CHART_PIE,
                                 GraphTypes.CHART_TREEMAP, GraphTypes.CHART_RADAR })
      {
         assertTypeSkipped(type, false);
         assertTypeSkipped(type, true);
      }
   }

   @Test
   void radarAndGanttInfoClassesAreSkipped() throws Exception {
      for(VSChartInfo info : new VSChartInfo[] { new RadarVSChartInfo(), new GanttVSChartInfo() }) {
         Fixture f = chart(info, new VSChartDimensionRef[] { dim("REGION") }, sum("REVENUE"));
         assertFalse(HyperlinkDialogService.isLinearMeasureAxisLink(
            f.chartInfo, f.chartInfo.getFields("REVENUE", false)));
      }
   }

   @Test
   void perMeasurePieTypeUnderABarChartIsSkipped() throws Exception {
      Fixture f = bar();
      ((VSChartAggregateRef) f.chartInfo.getYFields()[0]).setChartType(GraphTypes.CHART_PIE);
      assertAccepted(f, model("REVENUE", true, true));

      Fixture rt = bar();
      ((VSChartAggregateRef) rt.chartInfo.getYFields()[0]).setRTChartType(GraphTypes.CHART_PIE);
      assertAccepted(rt, model("REVENUE", true, true));
   }

   @Test
   void barDesignTypeWithStalePolarRuntimeTypeIsSkipped() throws Exception {
      Fixture f = bar();
      f.chartInfo.setChartType(GraphTypes.CHART_BAR);
      f.chartInfo.setRTChartType(GraphTypes.CHART_PIE);
      assertAccepted(f, model("REVENUE", true, true));
   }
}
