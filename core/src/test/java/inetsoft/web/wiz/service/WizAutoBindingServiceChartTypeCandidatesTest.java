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

import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.vswizard.recommender.chart.ChartCombinationUtil;
import inetsoft.web.vswizard.model.recommender.VSChartRecommendation;
import inetsoft.web.vswizard.model.recommender.VSObjectRecommendation;
import inetsoft.web.vswizard.model.recommender.VSTableRecommendation;
import inetsoft.web.wiz.model.ChartTypeCandidate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The `candidates` chart-type menu returned to the AI layer.
 *
 * <p>MEMBERSHIP must come from {@code chartInfos} (the full feasible set, always populated) and ORDER
 * from {@code prefInfos} (the preference ranking, null when no pin was supplied). Reading only
 * prefInfos presented a narrower menu as though it were the feasible one — in both directions:
 * without pins the menu held no chart types at all, and with pins the ordinary Cartesian types
 * vanished, leaving a waterfall as the top suggestion for independent per-period totals.
 */
@Tag("core")
class WizAutoBindingServiceChartTypeCandidatesTest {
   private static ChartInfo info(int chartType) {
      ChartInfo ci = mock(ChartInfo.class);
      when(ci.getChartType()).thenReturn(chartType);
      return ci;
   }

   private static ChartCombinationUtil.ScoredInfo scored(int chartType, int score) {
      return new ChartCombinationUtil.ScoredInfo(info(chartType), score);
   }

   private static VSChartRecommendation chartRec(List<ChartInfo> chartInfos,
                                                 List<ChartCombinationUtil.ScoredInfo> prefInfos)
   {
      VSChartRecommendation rec = mock(VSChartRecommendation.class);
      when(rec.getChartInfos()).thenReturn(chartInfos);
      when(rec.getPrefInfos()).thenReturn(prefInfos);
      return rec;
   }

   private static List<String> typesOf(List<ChartTypeCandidate> candidates) {
      return candidates.stream().map(ChartTypeCandidate::getType).toList();
   }

   /**
    * The common case: no pin, so prefInfos is null. The menu used to come back with only
    * "table"/"crosstab" while the chart itself rendered as a perfectly good line.
    */
   @Test
   void listsEveryFeasibleTypeWhenNoPreferenceWasSupplied() {
      List<ChartTypeCandidate> candidates = WizAutoBindingService.buildChartTypeCandidates(List.of(
         chartRec(List.of(info(GraphTypes.CHART_BAR), info(GraphTypes.CHART_LINE),
                          info(GraphTypes.CHART_POINT)),
                  null),
         mock(VSTableRecommendation.class)));

      assertTrue(typesOf(candidates).containsAll(List.of("bar", "line", "point")),
                 "feasible chart types must be listed even with no preference: " + typesOf(candidates));
   }

   /** Base-score order (the order chartInfos arrives in) survives the equal-score sort. */
   @Test
   void preservesTheRecommendersOwnOrderAmongUnpreferredTypes() {
      List<ChartTypeCandidate> candidates = WizAutoBindingService.buildChartTypeCandidates(List.of(
         chartRec(List.of(info(GraphTypes.CHART_LINE), info(GraphTypes.CHART_BAR),
                          info(GraphTypes.CHART_POINT)),
                  null)));

      assertEquals(List.of("line", "bar", "point"), typesOf(candidates));
   }

   /**
    * The B2 case: pinning x/y must not delete the Cartesian types from the menu. A pin says where a
    * field goes, not that bar and line stopped being possible.
    */
   @Test
   void aPinReRanksButNeverRemovesAFeasibleType() {
      List<ChartTypeCandidate> candidates = WizAutoBindingService.buildChartTypeCandidates(List.of(
         chartRec(List.of(info(GraphTypes.CHART_BAR), info(GraphTypes.CHART_LINE),
                          info(GraphTypes.CHART_POINT), info(GraphTypes.CHART_WATERFALL)),
                  List.of(scored(GraphTypes.CHART_POINT, 80),
                          scored(GraphTypes.CHART_WATERFALL, 40)))));

      List<String> types = typesOf(candidates);
      assertTrue(types.containsAll(List.of("bar", "line")),
                 "pinning an axis must not drop bar/line: " + types);

      // The pinned preference still decides the ORDER — point first, then waterfall.
      assertEquals("point", types.get(0));
      assertEquals("waterfall", types.get(1));
      assertTrue(candidates.get(0).getScore() > candidates.get(1).getScore());
   }

   /** A preferred type keeps its score; one known only from chartInfos takes 0 rather than an invented rank. */
   @Test
   void unpreferredTypesTakeZeroRatherThanAnInventedScore() {
      List<ChartTypeCandidate> candidates = WizAutoBindingService.buildChartTypeCandidates(List.of(
         chartRec(List.of(info(GraphTypes.CHART_POINT), info(GraphTypes.CHART_BAR)),
                  List.of(scored(GraphTypes.CHART_POINT, 60)))));

      assertEquals(1.0, candidates.get(0).getScore(), 0.0001);   // normalized against the max
      assertEquals("bar", candidates.get(1).getType());
      assertEquals(0.0, candidates.get(1).getScore(), 0.0001);
   }

   /** A type in both lists must appear once, at its preference score. */
   @Test
   void doesNotDuplicateATypePresentInBothLists() {
      List<ChartTypeCandidate> candidates = WizAutoBindingService.buildChartTypeCandidates(List.of(
         chartRec(List.of(info(GraphTypes.CHART_BAR), info(GraphTypes.CHART_LINE)),
                  List.of(scored(GraphTypes.CHART_BAR, 50)))));

      assertEquals(1, typesOf(candidates).stream().filter("bar"::equals).count());
      assertEquals("bar", candidates.get(0).getType());
      assertEquals(1.0, candidates.get(0).getScore(), 0.0001);
   }

   @Test
   void returnsEmptyForNoRecommendations() {
      assertTrue(WizAutoBindingService.buildChartTypeCandidates(null).isEmpty());
      assertTrue(WizAutoBindingService.buildChartTypeCandidates(List.<VSObjectRecommendation>of()).isEmpty());
   }
}
