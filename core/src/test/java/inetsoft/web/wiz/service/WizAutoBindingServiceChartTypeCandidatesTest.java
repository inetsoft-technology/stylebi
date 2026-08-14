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

   /**
    * Naming the type a substituted recommendation actually renders as.
    *
    * <p>changeType falls back to the first recommendation when the requested type has no candidate,
    * and returns 200 with ordinary coordinates — so the caller narrates the type it ASKED for and
    * prints "changed to a pie" over a bar chart. The warning that fixes that is only as good as this
    * lookup: name the wrong type and it is worse than saying nothing.
    *
    * <p>The index is the part worth pinning. {@code selectedIndex} addresses two lists through one
    * number — below {@code chartInfos.size()} it means chartInfos, at or above it means prefInfos
    * offset by that size (see setChartIndexForType, which writes it) — so reading it as a plain index
    * into either list alone silently names a different chart.
    */
   @Test
   void namesTheChartTypeAtASelectedIndexInChartInfos() {
      VSChartRecommendation rec = chartRec(
         List.of(info(GraphTypes.CHART_BAR), info(GraphTypes.CHART_LINE)), null);
      // The fallback changeType applies when nothing matched the requested type.
      when(rec.getSelectedIndex()).thenReturn(0);

      assertEquals("bar", WizAutoBindingService.selectedRecommendationType(rec));
   }

   @Test
   void namesTheChartTypeAtASelectedIndexThatAddressesPrefInfos() {
      VSChartRecommendation rec = chartRec(
         List.of(info(GraphTypes.CHART_BAR), info(GraphTypes.CHART_LINE)),
         List.of(scored(GraphTypes.CHART_PIE, 90)));
      // chartInfos.size() == 2, so index 2 is prefInfos[0] — not an out-of-range chartInfos read.
      when(rec.getSelectedIndex()).thenReturn(2);

      assertEquals("pie", WizAutoBindingService.selectedRecommendationType(rec));
   }

   @Test
   void namesTheNonChartRecommendationTypes() {
      assertEquals("table",
                   WizAutoBindingService.selectedRecommendationType(mock(VSTableRecommendation.class)));
   }

   @Test
   void returnsNullRatherThanGuessingWhenTheIndexAddressesNothing() {
      // Better to say only that the requested type was unavailable than to name a chart at random.
      VSChartRecommendation rec = chartRec(List.of(info(GraphTypes.CHART_BAR)), null);
      when(rec.getSelectedIndex()).thenReturn(7);

      assertNull(WizAutoBindingService.selectedRecommendationType(rec));
   }

   /**
    * Regression: {@code getChartTypeString}/{@code graphTypeForName} used to spell
    * {@link GraphTypes#CHART_ICICLE} as "icircle" — not StyleBI's own name for this chart type
    * (every other surface, including this class's own {@link GraphTypes} javadoc and its catalog
    * label "Icicle", spells it "icicle"). Found live from the wiz-services side, where a caller
    * asking for an "icicle" chart got silently substituted with a different chart type because the
    * bridge only recognized "icircle". Fixed at the source: this candidate menu (and the inverse
    * name→type lookup it shares logic with) must use the same spelling as the rest of the product.
    */
   @Test
   void spellsTheIcicleChartTypeCorrectly() {
      List<ChartTypeCandidate> candidates = WizAutoBindingService.buildChartTypeCandidates(List.of(
         chartRec(List.of(info(GraphTypes.CHART_ICICLE), info(GraphTypes.CHART_TREEMAP)), null)));

      assertTrue(typesOf(candidates).contains("icicle"),
                 "CHART_ICICLE must be spelled \"icicle\", not \"icircle\": " + typesOf(candidates));
      assertFalse(typesOf(candidates).contains("icircle"));
   }
}
