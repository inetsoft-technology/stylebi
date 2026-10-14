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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.chart.ChartRefCombination;
import inetsoft.web.vswizard.recommender.chart.DonutChartFilter;
import inetsoft.web.vswizard.recommender.chart.PieChartFilter;
import inetsoft.web.wiz.BindingFieldSettings;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78535: a multi-style chart -- the donut the recommender builds -- carries its category
 * dimension on the measure's OWN color field ({@code DonutChartFilter.putInside}), not on the
 * chart-level one. Every pass that walks a rebuilt chart's refs must reach those per-measure
 * aesthetics too, or a {@code $(Component)} binding freezes to the column it resolved to, and a
 * ranking / sort / date level on the category is dropped.
 *
 * <p>Runs the real {@link DonutChartFilter} / {@link PieChartFilter} over a real {@link VSChartInfo},
 * which needs the Spring context. Each donut test has a pie control: pie puts the same category on
 * the chart-level color field, which was always handled.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizAutoBindingServiceMultiStyleChartTest {
   private static final String DYNAMIC = "$(RB)";

   /** The wizard temp chart as the rebuild sees it: State (already resolved) by Sum(Sales). */
   private static VSChartInfo temp(boolean ranked) {
      VSChartInfo temp = new VSChartInfo();
      VSChartDimensionRef state = new VSChartDimensionRef();
      state.setGroupColumnValue("State");

      if(ranked) {
         state.setRankingOptionValue(String.valueOf(XCondition.TOP_N));
         state.setRankingNValue("3");
         state.setRankingColValue("Sum(Sales)");
      }

      temp.addXField(state);
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setColumnValue("Sales");
      sales.setFormulaValue("Sum");
      temp.addYField(sales);
      return temp;
   }

   private static ChartRefCombination comb() {
      // getAllRefs = [State (x), Sum(Sales) (y)]
      return new ChartRefCombination(new IntArrayList(), IntArrayList.of(1), IntArrayList.of(0));
   }

   private static VSChartInfo donut() {
      DonutChartFilter filter = new DonutChartFilter(new AssetEntry[0], temp(false), new ArrayList<>());
      assertTrue(filter.isValid(comb()));
      VSChartInfo info = filter.createChartInfo(comb());
      assertTrue(info.isMultiStyles());
      assertNull(info.getColorField(), "a donut has no chart-level color field");
      return info;
   }

   private static VSChartInfo pie() {
      PieChartFilter filter =
         new PieChartFilter(new AssetEntry[0], temp(false), new ArrayList<>(), true);
      assertTrue(filter.isValid(comb()));
      return filter.createChartInfo(comb());
   }

   private static VSChartDimensionRef donutCategory(VSChartInfo info) {
      return (VSChartDimensionRef) ((VSChartAggregateRef) info.getYField(0))
         .getColorField().getDataRef();
   }

   private static VSChartDimensionRef pieCategory(VSChartInfo info) {
      return (VSChartDimensionRef) info.getColorField().getDataRef();
   }

   private static ChartVSAssembly chart(VSChartInfo info) {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      return chart;
   }

   private static Map<String, SimpleFieldInfo> configMap(SimpleFieldInfo config) {
      Map<String, SimpleFieldInfo> map = new HashMap<>();
      map.put(config.getField(), config);
      return map;
   }

   /** What resolveDynamicFieldConfigs leaves for a "$(RB)" field resolving to State. */
   private static Map<String, SimpleFieldInfo> resolvedState() {
      SimpleFieldInfo fc = new SimpleFieldInfo();
      fc.setField("State");
      fc.setDynamicColumnValue(DYNAMIC);
      return configMap(fc);
   }

   private static Map<String, SimpleFieldInfo> descendingState() {
      DimensionFieldInfo fc = new DimensionFieldInfo();
      fc.setField("State");
      fc.setOrder(2); // SORT_DESC
      return configMap(fc);
   }

   private static WizAutoBindingService service() {
      return new WizAutoBindingService(null, null, null, null, null, null, null, null);
   }

   // ── restoreChartDynamicColumnValues ─────────────────────────────────────────────────────────

   @Test
   void donutCategoryIsRestoredToTheDynamicReference() {
      VSChartInfo info = donut();

      WizAutoBindingService.restoreChartDynamicColumnValues(info, resolvedState());

      assertEquals(DYNAMIC, donutCategory(info).getGroupColumnValue());
   }

   @Test
   void pieCategoryIsRestoredToTheDynamicReference() {
      VSChartInfo info = pie();

      WizAutoBindingService.restoreChartDynamicColumnValues(info, resolvedState());

      assertEquals(DYNAMIC, pieCategory(info).getGroupColumnValue());
   }

   @Test
   void donutTotalMeasureIsLeftAloneByTheRestore() {
      // The donut's synthetic Total@Sales measure (with its own text field) matches no config.
      VSChartInfo info = donut();
      SimpleFieldInfo fc = new SimpleFieldInfo();
      fc.setField("Sales");
      fc.setDynamicColumnValue("$(Measure)");

      WizAutoBindingService.restoreChartDynamicColumnValues(info, configMap(fc));

      assertEquals("$(Measure)", ((VSChartAggregateRef) info.getYField(0)).getColumnValue());
      VSChartAggregateRef total = (VSChartAggregateRef) info.getYField(1);
      assertEquals("Total@Sales", total.getColumnValue());
      assertEquals("Total@Sales",
                   ((VSChartAggregateRef) total.getTextField().getDataRef()).getColumnValue());
      assertEquals("State", donutCategory(info).getGroupColumnValue());
   }

   // ── applyFieldConfigs (through applyResolvedFormulaOverrides) ───────────────────────────────

   @Test
   void fieldConfigReachesTheDonutCategory() {
      VSChartInfo info = donut();

      service().applyResolvedFormulaOverrides(chart(info), descendingState());

      assertEquals(2, donutCategory(info).getOrder());
   }

   @Test
   void fieldConfigReachesThePieCategory() {
      VSChartInfo info = pie();

      service().applyResolvedFormulaOverrides(chart(info), descendingState());

      assertEquals(2, pieCategory(info).getOrder());
   }

   // ── BindingFieldSettings.refsOf (changeType fast path: temp chart -> candidate) ─────────────

   @Test
   void tempChartRankingIsRestoredOntoTheDonutCategory() {
      VSChartInfo info = donut();

      BindingFieldSettings.restore(BindingFieldSettings.refsOf(chart(temp(true))),
                                   BindingFieldSettings.refsOf(chart(info)));

      assertEquals("3", donutCategory(info).getRankingNValue());
   }

   @Test
   void tempChartRankingIsRestoredOntoThePieCategory() {
      VSChartInfo info = pie();

      BindingFieldSettings.restore(BindingFieldSettings.refsOf(chart(temp(true))),
                                   BindingFieldSettings.refsOf(chart(info)));

      assertEquals("3", pieCategory(info).getRankingNValue());
   }

   // ── changeType branch choice for a NON-donut multi-style chart (refsOf now sees per-measure refs) ──

   private static VSChartDimensionRef boundDim(String column, String rankingN) {
      VSChartDimensionRef d = new VSChartDimensionRef();
      d.setGroupColumnValue(column);
      d.setDataRef(new ColumnRef(new AttributeRef(null, column)));

      if(rankingN != null) {
         d.setRankingOptionValue(String.valueOf(XCondition.TOP_N));
         d.setRankingNValue(rankingN);
         d.setRankingColValue("Sum(Sales)");
      }

      return d;
   }

   private static VSChartAggregateRef boundSales() {
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setColumnValue("Sales");
      sales.setFormulaValue("Sum");
      sales.setDataRef(new ColumnRef(new AttributeRef(null, "Sales")));
      return sales;
   }

   private static VSAestheticRef aesthetic(VSChartDimensionRef dim) {
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(dim);
      return aref;
   }

   /** The temp chart as recorded after the create: State, Region (top-N), Sum(Sales). */
   private static ChartVSAssembly recordedTemp(String regionN) {
      VSChartInfo temp = new VSChartInfo();
      temp.addXField(boundDim("State", null));
      temp.addXField(boundDim("Region", regionN));
      temp.addYField(boundSales());
      return chart(temp);
   }

   /** x = State, y = Sum(Sales), Region on the MEASURE's own color field of a multi-style bar. */
   private static ChartVSAssembly multiStyleTarget(String regionN) {
      VSChartInfo info = new VSChartInfo();
      info.setMultiStyles(true);
      info.addXField(boundDim("State", null));
      VSChartAggregateRef sales = boundSales();
      sales.setColorField(aesthetic(boundDim("Region", regionN)));
      info.addYField(sales);
      assertNull(info.getColorField());
      ChartVSAssembly chart = chart(info);
      when(chart.getName()).thenReturn("Chart1");
      return chart;
   }

   /** The single-style twin: the same columns, Region on the chart-level color field. */
   private static ChartVSAssembly singleStyleTarget(String regionN) {
      VSChartInfo info = new VSChartInfo();
      info.addXField(boundDim("State", null));
      info.addYField(boundSales());
      info.setColorField(aesthetic(boundDim("Region", regionN)));
      ChartVSAssembly chart = chart(info);
      when(chart.getName()).thenReturn("Chart1");
      return chart;
   }

   private static VSTemporaryInfo tempInfo(ChartVSAssembly tempChart) {
      VSTemporaryInfo tempInfo = new VSTemporaryInfo();
      tempInfo.setTempChart(tempChart);
      tempInfo.setWizSourceAssemblyName("Chart1");
      return tempInfo;
   }

   @Test
   void multiStyleTargetIsDescribedByItsTempChartLikeItsSingleStyleTwin() {
      // Before #78535 refsOf skipped Region on the measure's color field, so the column sets differed
      // and changeType rebuilt; now the multi-style chart takes the fast path, as its twin always did.
      VSTemporaryInfo tempInfo = tempInfo(recordedTemp("3"));

      assertTrue(service().tempChartDescribes(tempInfo, singleStyleTarget("3")));
      assertTrue(service().tempChartDescribes(tempInfo, multiStyleTarget("3")));
   }

   @Test
   void fastPathCarriesWhatTheRebuildWouldForAMultiStyleTarget() {
      ChartVSAssembly target = multiStyleTarget("3");
      ChartVSAssembly temp = recordedTemp("3");

      // Fast path: a cached candidate (cloned from the temp chart at autoBinding, before the top-N was
      // set) gets the temp chart's settings restored -- applyTempChartFieldSettings.
      VSChartInfo candidateInfo = new VSChartInfo();
      candidateInfo.addXField(boundDim("State", null));
      candidateInfo.addYField(boundSales());
      VSChartDimensionRef candidateRegion = boundDim("Region", null);
      candidateInfo.setColorField(aesthetic(candidateRegion));
      ChartVSAssembly candidate = chart(candidateInfo);
      BindingFieldSettings.restore(BindingFieldSettings.refsOf(temp),
                                   BindingFieldSettings.refsOf(candidate));

      // Rebuild: the field list deriveFieldConfigs reads off the target.
      WizVsService wizVsService = new WizVsService(null, null, null, null, null, null);
      List<SimpleFieldInfo> rebuild =
         new WizAutoBindingService(null, null, null, null, null, wizVsService, null, null)
            .deriveFieldConfigs(target);
      Set<String> rebuildColumns =
         rebuild.stream().map(SimpleFieldInfo::getField).collect(Collectors.toSet());
      DimensionFieldInfo rebuildRegion = rebuild.stream()
         .filter(c -> c instanceof DimensionFieldInfo && "Region".equals(c.getField()))
         .map(DimensionFieldInfo.class::cast).findFirst().orElseThrow();

      assertEquals(Set.of("State", "Region", "Sales"), rebuildColumns);
      assertEquals(rebuildColumns, BindingFieldSettings.columnKeys(candidate));
      assertEquals(3, rebuildRegion.getRanking().getRankingN());
      assertEquals("3", candidateRegion.getRankingNValue());
   }

   @Test
   void columnOnlyOnAPerMeasureAestheticMakesTheTempChartStale() {
      // The temp chart never bound Region; a multi-style chart that adds it on the measure's own color
      // field must rebuild, not take the fast path and drop Region (it did before #78535).
      VSChartInfo temp = new VSChartInfo();
      temp.addXField(boundDim("State", null));
      temp.addYField(boundSales());

      assertFalse(service().tempChartDescribes(tempInfo(chart(temp)), multiStyleTarget(null)));
   }
}
