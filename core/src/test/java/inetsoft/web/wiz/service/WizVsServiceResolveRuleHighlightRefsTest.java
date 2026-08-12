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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.HighlightRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.ApplyHighlightModel;
import inetsoft.web.wiz.model.ApplyWarning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Bug #75889: a chart highlight rule must attach to the ONE binding ref it names, not to every ref.
 * Attaching a rule to every ref put the same HighlightGroup on a measure AND a dimension at once, and
 * because GraphGenerator splits plot-vs-axis coloring by ref KIND (findHighlightRef adopts a ref for the
 * axis only when it is not a measure), a bar chart came back with its bars AND its category axis colored.
 *
 * <p>These cover {@link WizVsService#resolveRuleHighlightRefs} directly: it is the whole of the fix, and
 * {@link WizVsServiceApplyHighlightCopyTest} — the only other highlight test — exercises the TEXT-output
 * branch, which never touches chart binding refs.
 *
 * <p>The narrowing cases assert the SINGLE named ref comes back; the fallback cases assert the SAME list
 * instance comes back, since "fall back to all refs" is deliberately the untouched pre-fix behaviour.
 *
 * <p>WizVsService's constructor has no side effects (see {@link WizVsServiceFilterCopyTest}), and this
 * method reads nothing but the rule, the ref list and the chart info, so the three collaborators are
 * plain mocks this path never touches.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceResolveRuleHighlightRefsTest {
   private WizVsService service;
   private VSChartDimensionRef dim;
   private VSChartAggregateRef agg;
   private VSChartInfo chartInfo;
   private List<HighlightRef> allRefs;

   @BeforeEach
   void setUp() {
      service = new WizVsService(
         mock(ViewsheetService.class), mock(AssetRepository.class), mock(SecurityEngine.class), null, null);

      // The exact shape bug #75889 reproduced on: one dimension on X, one measure on Y — a plain bar
      // chart, where the dimension carries the axis highlight and the measure the plot highlight.
      dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("State");

      agg = measure("Sales");

      chartInfo = new VSChartInfo();
      chartInfo.addXField(dim);
      chartInfo.addYField(agg);

      allRefs = List.of(dim, agg);
   }

   private static VSChartAggregateRef measure(String column) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue(column);
      ref.setFormulaValue("Sum");
      return ref;
   }

   private static ApplyHighlightModel.Highlight ruleOn(String field) {
      ApplyHighlightModel.Highlight rule = new ApplyHighlightModel.Highlight();
      rule.setName("r1");
      rule.setField(field);
      return rule;
   }

   @Test
   void aRuleNamingAMeasureByItsFullNameNarrowsToThatRefAlone() {
      // The spelling the chart DataSet exposes as a header, e.g. "Sum(Sales)". Guard first that it really
      // is distinct from the plain name, so this case isn't silently identical to the one below.
      String fullName = agg.getFullName();
      assertNotEquals(agg.getName(), fullName);

      List<HighlightRef> refs = service.resolveRuleHighlightRefs(
         ruleOn(fullName), allRefs, chartInfo, false, null);

      assertEquals(1, refs.size());
      assertSame(agg, refs.get(0));
   }

   @Test
   void aRuleNamingAMeasureByItsPlainNameNarrowsToThatRefAlone() {
      List<HighlightRef> refs = service.resolveRuleHighlightRefs(
         ruleOn(agg.getName()), allRefs, chartInfo, false, null);

      assertEquals(1, refs.size());
      assertSame(agg, refs.get(0));
   }

   @Test
   void aRuleNamingTheDimensionNarrowsToTheDimensionNotTheMeasure() {
      // The other half of the bug: naming the dimension must colour the axis only, leaving the measure —
      // and so the plot marks — untouched.
      List<HighlightRef> refs = service.resolveRuleHighlightRefs(
         ruleOn(dim.getFullName()), allRefs, chartInfo, false, null);

      assertEquals(1, refs.size());
      assertSame(dim, refs.get(0));
   }

   @Test
   void matchingIgnoresCase() {
      // Deliberately lenient, unlike the case-sensitive crosstab path: a miss here silently widens to every
      // ref (i.e. reproduces this very bug) rather than failing loud the way applyTableHighlight does.
      List<HighlightRef> refs = service.resolveRuleHighlightRefs(
         ruleOn(agg.getName().toUpperCase()), allRefs, chartInfo, false, null);

      assertEquals(1, refs.size());
      assertSame(agg, refs.get(0));
   }

   @Test
   void aRuleNamingNoFieldFallsBackToEveryRef() {
      assertSame(allRefs, service.resolveRuleHighlightRefs(ruleOn(null), allRefs, chartInfo, false, null));
      assertSame(allRefs, service.resolveRuleHighlightRefs(ruleOn("   "), allRefs, chartInfo, false, null));
   }

   @Test
   void aRuleNamingAnUnboundFieldFallsBackToEveryRef() {
      assertSame(allRefs, service.resolveRuleHighlightRefs(
         ruleOn("NoSuchColumn"), allRefs, chartInfo, false, null));
   }

   @Test
   void aWordCloudFallsBackToEveryRefEvenWhenTheFieldMatches() {
      // Its highlightable ref is the TEXT aesthetic, not the X/Y binding this narrowing understands, so
      // narrowing by an X/Y-shaped name would drop the highlight rather than merely widen it.
      assertSame(allRefs, service.resolveRuleHighlightRefs(
         ruleOn(agg.getFullName()), allRefs, chartInfo, true, null));
   }

   @Test
   void aTreemapFallsBackToEveryRefEvenWhenTheFieldMatches() {
      chartInfo.setChartType(GraphTypes.CHART_TREEMAP);

      assertSame(allRefs, service.resolveRuleHighlightRefs(
         ruleOn(agg.getFullName()), allRefs, chartInfo, false, null));
   }

   @Test
   void aGanttIsRecognisedByItsDESIGNChartTypeNotItsRuntimeOne() {
      // GraphGenerator.getHighlightRefs() special-cases gantt off the design-time type where it reads the
      // runtime type for the others; resolveRuleHighlightRefs mirrors that. A runtime type that is NOT
      // gantt must therefore not defeat the fallback.
      chartInfo.setChartType(GraphTypes.CHART_GANTT);
      chartInfo.setRTChartType(GraphTypes.CHART_BAR);

      assertSame(allRefs, service.resolveRuleHighlightRefs(
         ruleOn(agg.getFullName()), allRefs, chartInfo, false, null));
   }

   @Test
   void aRelationChartFallsBackToEveryRefEvenWhenTheFieldMatches() {
      // Its highlightable refs are the source/target fields, not the X/Y binding.
      chartInfo.setChartType(GraphTypes.CHART_NETWORK);

      assertSame(allRefs, service.resolveRuleHighlightRefs(
         ruleOn(agg.getFullName()), allRefs, chartInfo, false, null));
   }

   @Test
   void aScatterMatrixFallsBackToEveryRefEvenWhenTheFieldMatches() {
      // GraphTypeUtil.isScatterMatrix recognises the shape rather than a chart type: the SAME measures on
      // both axes and no dimension on either, which is why this case needs its own chart info.
      VSChartAggregateRef sales = measure("Sales");
      VSChartAggregateRef profit = measure("Profit");
      VSChartInfo matrix = new VSChartInfo();
      matrix.addXField(measure("Sales"));
      matrix.addXField(measure("Profit"));
      matrix.addYField(sales);
      matrix.addYField(profit);

      List<HighlightRef> matrixRefs = List.of(sales, profit);

      assertSame(matrixRefs, service.resolveRuleHighlightRefs(
         ruleOn(sales.getFullName()), matrixRefs, matrix, false, null));
   }

   /**
    * Reporting the one fallback the caller cannot otherwise detect.
    *
    * <p>Falling back to every ref is deliberate and stays — narrowing wrongly would drop the highlight
    * entirely, which is worse than widening it. But when the rule NAMED a field and nothing matched, the
    * name the caller supplied did nothing and every series is highlighted instead of the one they asked
    * about. Nothing in the 200 response said so, and the caller went on to report a clean success.
    */
   @Test
   void aRuleNamingAnUnboundFieldReportsThatItWasWidened() {
      List<ApplyWarning> warnings = new ArrayList<>();

      assertSame(allRefs, service.resolveRuleHighlightRefs(
         ruleOn("NoSuchColumn"), allRefs, chartInfo, false, warnings));

      assertEquals(1, warnings.size());
      assertEquals("highlight:NoSuchColumn", warnings.get(0).getOption());
      assertTrue(warnings.get(0).getReason().contains("every series"),
                 "the reason must say what happened INSTEAD, not just that the name was unknown");
   }

   @Test
   void aRuleThatNarrowsSuccessfullyReportsNothing() {
      List<ApplyWarning> warnings = new ArrayList<>();

      service.resolveRuleHighlightRefs(ruleOn(agg.getFullName()), allRefs, chartInfo, false, warnings);

      assertTrue(warnings.isEmpty());
   }

   @Test
   void theFallbacksThatWereNeverAskedToNarrowReportNothing() {
      // A rule naming no field never asked for one ref, and the special chart types cannot be narrowed
      // at all — reporting those would fire on every rule those charts carry and bury the case above.
      List<ApplyWarning> warnings = new ArrayList<>();

      service.resolveRuleHighlightRefs(ruleOn(null), allRefs, chartInfo, false, warnings);
      service.resolveRuleHighlightRefs(ruleOn("NoSuchColumn"), allRefs, chartInfo, true, warnings);

      assertTrue(warnings.isEmpty());
   }

   @Test
   void aSingleRefChartShortCircuitsToThatRef() {
      // Nothing to narrow: one ref cannot be attached to "every ref" wrongly, so the name is never consulted.
      List<HighlightRef> single = List.of(agg);

      assertSame(single, service.resolveRuleHighlightRefs(
         ruleOn("NoSuchColumn"), single, chartInfo, false, null));
   }
}
