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
package inetsoft.web.composer.model.vs;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.report.script.viewsheet.VSChartBindingScriptable;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77878: a chart that shares another chart's date comparison (comparisonShareFrom) keeps
 * its own dc, a snapshot of the source taken when the share was set that goes stale when the
 * source is edited. The Advanced pane's bar-corner gate, the script setYFields dc chart-type
 * update and the sandbox dc-condition fallback must read the share-resolved dc.
 *
 * Uses real Viewsheet / ChartVSAssembly / DateComparisonInfo objects rendered through
 * ChartVSAssemblyInfo.update(). GraphTypeUtil.checkChartStylePermission is stubbed to true
 * because without a security principal every chart type is denied in tests.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartAdvancedPaneModelDcShareTest {
   @BeforeEach
   void stubChartStylePermission() {
      graphTypeUtil = mockStatic(GraphTypeUtil.class, CALLS_REAL_METHODS);
      graphTypeUtil.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
   }

   @AfterEach
   void closeStub() {
      graphTypeUtil.close();
   }

   @Test
   void sharerWithStaleValueOnlyOwnDcShowsBarCornerOptions() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = chart(vs, "Chart1", dc(DateComparisonInfo.CHANGE_VALUE), null);
      ChartVSAssembly sharer = chart(vs, "Chart2", dc(DateComparisonInfo.VALUE), "Chart1");
      render(source, vs);
      render(sharer, vs);

      // the sharer renders the source's change & value dc
      assertEquals(DateComparisonInfo.CHANGE_VALUE,
                   DateComparisonUtil.getDateComparison(sharer.getChartInfo(), vs)
                      .getComparisonOption());

      ChartPlotOptionsPaneModelAccess sourcePane = pane(source);
      assertTrue(sourcePane.barCornerRadiusVisible);
      assertTrue(sourcePane.barRoundAllCornersVisible);

      ChartPlotOptionsPaneModelAccess sharerPane = pane(sharer);
      assertTrue(sharerPane.barCornerRadiusVisible,
                 "sharer rendering change & value must show the bar corner radius option");
      assertTrue(sharerPane.barRoundAllCornersVisible,
                 "sharer rendering change & value must show the round all corners option");
   }

   @Test
   void sandboxGivesNoDcConditionsForSharerWhoseShareNoLongerResolves() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = chart(vs, "Chart1", dc(DateComparisonInfo.CHANGE_VALUE), null);
      ChartVSAssembly sharer = chart(vs, "Chart2", dc(DateComparisonInfo.VALUE), "Chart1");
      ChartVSAssembly unshared = chart(vs, "Chart3", dc(DateComparisonInfo.VALUE), null);
      render(source, vs);
      render(sharer, vs);
      render(unshared, vs);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, Viewsheet.SHEET_RUNTIME_MODE, null,
                                                  false, null, "bug-77878-box");
      assertNotNull(box.getDateComparisonConditions("Chart2"));

      // disable DC on the source through its Advanced pane, which leaves the sharer's
      // comparisonShareFrom pointing at a source that no longer has a dc
      ChartAdvancedPaneModel adv = new ChartAdvancedPaneModel(source.getChartInfo());
      adv.setDateComparisonEnabled(false);
      adv.updateChartAdvancedPaneModel(source.getChartInfo());

      assertEquals("Chart1", sharer.getChartInfo().getComparisonShareFrom());
      assertNull(DateComparisonUtil.getDateComparison(sharer.getChartInfo(), vs));

      ConditionList sharerConds = box.getDateComparisonConditions("Chart2");
      assertNull(sharerConds, "a sharer whose share no longer resolves must not get the " +
         "stale own dc's conditions: " + sharerConds);

      ConditionList unsharedConds = box.getDateComparisonConditions("Chart3");
      assertNotNull(unsharedConds, "an un-shared chart keeps its own dc conditions");
      assertFalse(unsharedConds.isEmpty());
   }

   @Test
   void scriptSetYFieldsUsesShareResolvedDc() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly source = chart(vs, "Chart1", dc(DateComparisonInfo.CHANGE_VALUE), null);
      // same share, differing only in the own dc snapshot
      ChartVSAssembly staleSharer = chart(vs, "Chart2", dc(DateComparisonInfo.VALUE), "Chart1");
      ChartVSAssembly freshSharer =
         chart(vs, "Chart3", dc(DateComparisonInfo.CHANGE_VALUE), "Chart1");
      render(source, vs);
      render(staleSharer, vs);
      render(freshSharer, vs);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getViewsheet()).thenReturn(vs);
      setYFields(box, "Chart2");
      setYFields(box, "Chart3");

      List<Integer> fresh = aggregateRTChartTypes(freshSharer);
      List<Integer> stale = aggregateRTChartTypes(staleSharer);

      // change & value draws the change aggregate as points
      assertTrue(fresh.contains(GraphTypes.CHART_POINT), "fresh sharer agg types " + fresh);
      assertEquals(fresh, stale, "script setYFields on a sharer must apply the share-resolved " +
         "dc, not the stale value-only own dc");
   }

   private static void setYFields(ViewsheetSandbox box, String name) {
      ChartVSAScriptable scriptable = new ChartVSAScriptable(box);
      scriptable.setAssembly(name);
      new VSChartBindingScriptable(scriptable)
         .setYFields(new Object[] { new Object[] { "v", "number", "Sum" } });
   }

   private static List<Integer> aggregateRTChartTypes(ChartVSAssembly chart) {
      List<Integer> types = new ArrayList<>();

      for(ChartAggregateRef ref : chart.getVSChartInfo().getAestheticAggregateRefs(true)) {
         if(ref != null) {
            types.add(ref.getRTChartType());
         }
      }

      return types;
   }

   private static ChartPlotOptionsPaneModelAccess pane(ChartVSAssembly chart) {
      ChartAdvancedPaneModel model = new ChartAdvancedPaneModel(chart.getChartInfo());
      return new ChartPlotOptionsPaneModelAccess(
         model.getChartPlotOptionsPaneModel().isBarCornerRadiusVisible(),
         model.getChartPlotOptionsPaneModel().isBarRoundAllCornersVisible());
   }

   private record ChartPlotOptionsPaneModelAccess(boolean barCornerRadiusVisible,
                                                  boolean barRoundAllCornersVisible)
   {
   }

   private static DateComparisonInfo dc(int option) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(true);
      periods.setToDate(true);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.MONTH);
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setContextLevel(XConstants.YEAR_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(option);
      return dcInfo;
   }

   private static ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      ColumnRef date = new ColumnRef(new AttributeRef(null, "date"));
      date.setDataType(XSchema.DATE);
      ColumnRef value = new ColumnRef(new AttributeRef(null, "v"));
      value.setDataType(XSchema.DOUBLE);
      columns.addAttribute(date);
      columns.addAttribute(value);
      return columns;
   }

   private static ChartVSAssembly chart(Viewsheet vs, String name, DateComparisonInfo own,
                                        String shareFrom)
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      ChartVSAssemblyInfo info = chart.getChartInfo();
      info.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"));
      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_LINE);
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, "date"));
      dim.setDataType(XSchema.DATE);
      dim.setDateLevel(XConstants.MONTH_DATE_GROUP);
      dim.setGroupColumnValue("date");
      cinfo.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, "v"));
      agg.setColumnValue("v");
      agg.setFormula(AggregateFormula.SUM);
      cinfo.addYField(agg);
      info.setVSChartInfo(cinfo);
      info.setDateComparisonEnabledValue("true");
      info.setDateComparisonInfo(own);
      info.setComparisonShareFrom(shareFrom);
      vs.addAssembly(chart);
      return chart;
   }

   // ChartVSAssemblyInfo.update() applies the dc (ChartDcProcessor.process()) and then builds
   // the chart tree, which needs a real runtime and NPEs here after the dc state is set
   private static void render(ChartVSAssembly chart, Viewsheet vs) throws Exception {
      try {
         chart.getChartInfo().update(vs, columns());
      }
      catch(NullPointerException e) {
         boolean chartTree = Arrays.stream(e.getStackTrace())
            .anyMatch(f -> f.getClassName().endsWith("ChartTree"));

         if(!chartTree) {
            throw e;
         }
      }
   }

   private MockedStatic<GraphTypeUtil> graphTypeUtil;
}
