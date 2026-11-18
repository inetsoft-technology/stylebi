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
package inetsoft.web.wiz.binding;

import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #81526: a shelf write that takes a highlight's field away, or a field its condition names,
 * used to say nothing. These tests run the same snapshot, rewrite, reapply and sweep sequence as
 * {@code ChartBindingService.setShelf}, with the real model factories.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartHighlightSweepTest {
   private final DataRefModelFactoryService dataRefs = new DataRefModelFactoryService(List.of());
   private final ChartRefModelFactoryService refService = refService();

   private ChartRefModelFactoryService refService() {
      ChartAggregateInfoFactory.VSChartAggregateInfoFactory aggregates =
         new ChartAggregateInfoFactory.VSChartAggregateInfoFactory(
            new VisualFrameModelFactoryService(List.of()), dataRefs);
      aggregates.setChartAestheticService(mock(ChartAestheticService.class));
      return new ChartRefModelFactoryService(List.of(
         aggregates, new ChartDimensionInfoFactory.VSChartDimensionInfoFactory(dataRefs)));
   }

   /** A highlight named {@code name} whose only condition is on {@code field}. */
   private static HighlightGroup group(String name, String field) {
      TextHighlight highlight = new TextHighlight();

      if(field != null) {
         ConditionList conditions = new ConditionList();
         Condition condition = new Condition(XSchema.STRING);
         condition.setOperation(Condition.EQUAL_TO);
         condition.addValue("USA East");
         conditions.append(new ConditionItem(new AttributeRef(null, field), condition, 0));
         highlight.setConditionGroup(conditions);
      }

      HighlightGroup group = new HighlightGroup();
      group.addHighlight(name, highlight);
      return group;
   }

   private static VSChartAggregateRef measure(String column, String formula) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new ColumnRef(attribute));
      ref.setFormulaValue(formula);
      return ref;
   }

   private static VSChartDimensionRef dimension(String column, int level) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(level == XConstants.NONE_DATE_GROUP ? XSchema.STRING : XSchema.DATE);
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setDataRef(new ColumnRef(attribute));
      ref.setDateLevelValue(String.valueOf(level));
      return ref;
   }

   private static FieldRef agg(String column, String formula) {
      return new FieldRef(column, "measure", formula, null, null);
   }

   private static FieldRef dim(String column, String level) {
      return new FieldRef(column, "dimension", null, level, null);
   }

   /** The two notes {@code setShelf} adds, in its order: reapply's, then the sweep's. */
   private record Notes(List<String> carried, List<String> swept) {
      List<String> all() {
         List<String> all = new ArrayList<>(carried);
         all.addAll(swept);
         return all;
      }
   }

   private Notes rewrite(VSChartInfo info, String shelf, FieldRef... fields) {
      List<ChartRef> fresh = new ArrayList<>();

      for(FieldRef field : fields) {
         ChartRefModel model = FieldRefFactory.toChartRef(field);
         fresh.add(refService.pasteChartRef(info, model));
      }

      return rewrite(info, shelf, fresh);
   }

   /** As above, writing the given refs (for example ones that carry a calculator). */
   private static Notes rewrite(VSChartInfo info, String shelf, List<ChartRef> fresh) {
      return rewrite(info, shelf, () -> {
         if(shelf.equals("y")) {
            info.removeYFields();
            fresh.forEach(info::addYField);
         }
         else {
            info.removeXFields();
            fresh.forEach(info::addXField);
         }
      });
   }

   /** The same snapshot, write, reapply and sweep sequence, with the write left to the caller. */
   private static Notes rewrite(VSChartInfo info, String shelf, Runnable write) {
      List<ChartHyperlinkCarry.Saved> saved = ChartHyperlinkCarry.snapshot(info, shelf);
      ChartHighlightSweep.Snapshot highlights = ChartHighlightSweep.snapshot(info);
      write.run();
      Set<String> reported = new HashSet<>();
      List<String> carried = ChartHyperlinkCarry.reapply(info, shelf, saved, reported);
      return new Notes(carried, ChartHighlightSweep.sweep(info, highlights, reported));
   }

   private static VSChartAggregateRef measure(String column, String formula, Calculator calc) {
      VSChartAggregateRef ref = measure(column, formula);
      ref.setCalculator(calc);
      return ref;
   }

   private static ChangeCalc changeFromPrevious(String dimension) {
      ChangeCalc calc = new ChangeCalc();
      calc.setFrom(ValueOfCalc.PREVIOUS);
      calc.setColumnName(dimension);
      return calc;
   }

   private static VSChartAggregateRef y(VSChartInfo info, int i) {
      return (VSChartAggregateRef) info.getYField(i);
   }

   @Test
   void aMeasureReplacedByAnotherColumnReportsItsMarkAndTextHighlights() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = measure("REVENUE", "Sum");
      revenue.setHighlightGroup(group("Big", "REGION"));
      revenue.setTextHighlightGroup(group("BigText", "REGION"));
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addYField(revenue);

      Notes notes = rewrite(info, "y", agg("QUANTITY", "Sum"));

      assertTrue(notes.carried().isEmpty(), "reapply stays silent for a column that left");
      assertEquals(2, notes.swept().size(), notes.swept().toString());
      assertTrue(notes.swept().stream().anyMatch(n -> n.contains("'Big' on Sum(REVENUE) (mark)") &&
         n.contains("was removed together with its field on the y shelf")), notes.swept().toString());
      assertTrue(notes.swept().stream().anyMatch(n -> n.contains("'BigText' on Sum(REVENUE) (text)")),
                 notes.swept().toString());
      assertNull(y(info, 0).getHighlightGroup(), "the sweep does not give anything back");
   }

   @Test
   void aClearedShelfReportsTheHighlightOfItsOnlyMeasure() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = measure("REVENUE", "Sum");
      revenue.setHighlightGroup(group("Big", null));
      info.addYField(revenue);

      Notes notes = rewrite(info, "y");

      assertEquals(1, notes.swept().size(), notes.swept().toString());
      assertTrue(notes.swept().get(0).contains("'Big' on Sum(REVENUE)"), notes.swept().get(0));
   }

   @Test
   void aMeasureThatStaysKeepsItsHighlightAndOnlyTheRemovedOneIsReported() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef quantity = measure("QUANTITY", "Sum");
      quantity.setHighlightGroup(group("QtyHl", null));
      VSChartAggregateRef revenue = measure("REVENUE", "Sum");
      revenue.setHighlightGroup(group("RevHl", null));
      info.addYField(quantity);
      info.addYField(revenue);

      Notes notes = rewrite(info, "y", agg("REVENUE", "Sum"));

      assertEquals(1, notes.swept().size(), notes.swept().toString());
      assertTrue(notes.swept().get(0).contains("'QtyHl' on Sum(QUANTITY)"), notes.swept().get(0));
      assertNotNull(y(info, 0).getHighlightGroup().getHighlight("RevHl"));
   }

   @Test
   void anAxisHighlightOnADimensionThatLeftIsReported() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartDimensionRef region = dimension("REGION", XConstants.NONE_DATE_GROUP);
      region.setHighlightGroup(group("East", "REGION"));
      info.addXField(region);

      Notes notes = rewrite(info, "x", dim("CATEGORY_NAME", null));

      assertEquals(1, notes.swept().size(), notes.swept().toString());
      assertTrue(notes.swept().get(0).contains("'East' on REGION (axis)") &&
                    notes.swept().get(0).contains("on the x shelf"), notes.swept().get(0));
   }

   @Test
   void aConditionOnAFieldThatLeftTheChartIsReportedAndLeftInPlace() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef quantity = measure("QUANTITY", "Sum");
      quantity.setHighlightGroup(group("East", "REGION"));
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addYField(quantity);

      Notes notes = rewrite(info, "x", dim("CATEGORY_NAME", null));

      assertEquals(1, notes.swept().size(), notes.swept().toString());
      assertTrue(notes.swept().get(0).contains("'East' on Sum(QUANTITY) (mark) has a condition on " +
         "REGION, which is no longer bound"), notes.swept().get(0));
      assertNotNull(y(info, 0).getHighlightGroup().getHighlight("East"),
                    "the highlight is reported, never deleted");
   }

   @Test
   void aConditionFieldMovedToColorIsStillBound() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef quantity = measure("QUANTITY", "Sum");
      quantity.setHighlightGroup(group("East", "REGION"));
      VSChartDimensionRef region = dimension("REGION", XConstants.NONE_DATE_GROUP);
      info.addXField(region);
      info.addYField(quantity);
      // the same write also puts REGION on color
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dimension("REGION", XConstants.NONE_DATE_GROUP));
      List<ChartHyperlinkCarry.Saved> saved = ChartHyperlinkCarry.snapshot(info, "x");
      ChartHighlightSweep.Snapshot before = ChartHighlightSweep.snapshot(info);
      info.removeXFields();
      info.addXField(refService.pasteChartRef(
         info, FieldRefFactory.toChartRef(dim("CATEGORY_NAME", null))));
      info.setColorField(color);
      Set<String> reported = new HashSet<>();
      ChartHyperlinkCarry.reapply(info, "x", saved, reported);

      assertTrue(ChartHighlightSweep.sweep(info, before, reported).isEmpty());
   }

   @Test
   void aDateLevelledDimensionThatStaysDoesNotMakeItsConditionInert() {
      for(String field : new String[] { "ORDER_DATE", "Year(ORDER_DATE)" }) {
         VSChartInfo info = new DefaultVSChartInfo();
         VSChartAggregateRef quantity = measure("QUANTITY", "Sum");
         quantity.setHighlightGroup(group("Recent", field));
         info.addXField(dimension("ORDER_DATE", XConstants.YEAR_DATE_GROUP));
         info.addYField(quantity);

         Notes notes = rewrite(info, "y", agg("QUANTITY", "Sum"));

         assertTrue(notes.all().isEmpty(), field + ": " + notes.all());
      }
   }

   @Test
   void aSameColumnFormulaChangeIsReportedOnceByTheExistingNote() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = measure("REVENUE", "Sum");
      revenue.setHighlightGroup(group("Big", "REGION"));
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addYField(revenue);

      Notes notes = rewrite(info, "y", agg("REVENUE", "Average"));

      assertEquals(1, notes.carried().size(), notes.carried().toString());
      assertEquals("The highlight on Sum(REVENUE) was removed: its field stays on the y shelf " +
         "under a different aggregate formula, and a highlight's conditions name the old field. " +
         "Re-create it with set_highlight on the new field.", notes.carried().get(0));
      assertTrue(notes.swept().isEmpty(), "the sweep must not repeat it: " + notes.swept());
   }

   @Test
   void aConditionThatWasNeverBoundIsNotReportedByAnUnrelatedWrite() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef quantity = measure("QUANTITY", "Sum");
      quantity.setHighlightGroup(group("Stale", "NO_SUCH_FIELD"));
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addYField(quantity);

      Notes notes = rewrite(info, "x", dim("CATEGORY_NAME", null));

      assertTrue(notes.all().isEmpty(), notes.all().toString());
   }

   @Test
   void aCalculatorAddedToAMeasureKeepsItsHighlightAndSaysNothing() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = measure("REVENUE", "Sum");
      revenue.setHighlightGroup(group("HiRegion", "REGION"));
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addYField(revenue);

      Notes notes = rewrite(info, "y", List.of(measure("REVENUE", "Sum", new PercentCalc())));

      assertTrue(notes.all().isEmpty(), notes.all().toString());
      assertNotNull(y(info, 0).getHighlightGroup().getHighlight("HiRegion"));
      assertNotEquals("Sum(REVENUE)", ChartHyperlinkCarry.fullName(y(info, 0)),
                      "the calculator renamed the measure");
   }

   @Test
   void aCalculatorRemovedFromAMeasureKeepsItsHighlightAndSaysNothing() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = measure("REVENUE", "Sum", new PercentCalc());
      revenue.setHighlightGroup(group("HiRegion", "REGION"));
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addYField(revenue);

      Notes notes = rewrite(info, "y", List.of(measure("REVENUE", "Sum")));

      assertTrue(notes.all().isEmpty(), notes.all().toString());
      assertNotNull(y(info, 0).getHighlightGroup().getHighlight("HiRegion"));
   }

   @Test
   void aRenamedMeasureWhoseConditionDimensionLeftGetsOnlyTheNoLongerBoundNote() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = measure("REVENUE", "Sum", changeFromPrevious("Year(ORDER_DATE)"));
      revenue.setHighlightGroup(group("HiYear", "Year(ORDER_DATE)"));
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addXField(dimension("ORDER_DATE", XConstants.YEAR_DATE_GROUP));
      info.addYField(revenue);

      // the x write drops the date, which also renames the Change measure ("from previous")
      Notes notes = rewrite(info, "x", () -> {
         info.removeXFields();
         info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
         y(info, 0).setCalculator(changeFromPrevious(null));
      });

      assertEquals(1, notes.all().size(), notes.all().toString());
      String note = notes.all().get(0);
      assertTrue(note.contains("'HiYear' on Change from previous") &&
                    note.contains("has a condition on Year(ORDER_DATE), which is no longer bound"),
                 note);
      assertFalse(note.contains("was removed"), note);
      assertNotNull(y(info, 0).getHighlightGroup().getHighlight("HiYear"));
   }

   @Test
   void aConditionOnTheMeasuresOwnNameGoesStaleWhenACalculatorRenamesIt() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = measure("REVENUE", "Sum");
      revenue.setHighlightGroup(group("Big", "Sum(REVENUE)"));
      info.addYField(revenue);

      Notes notes = rewrite(info, "y", List.of(measure("REVENUE", "Sum", new PercentCalc())));

      assertEquals(1, notes.all().size(), notes.all().toString());
      assertTrue(notes.all().get(0).contains("has a condition on Sum(REVENUE), which is no " +
         "longer bound"), notes.all().get(0));
      assertFalse(notes.all().get(0).contains("was removed"), notes.all().get(0));
   }

   @Test
   void twoMeasuresOfOneFieldUnderDifferentCalculatorsAreNotCollapsed() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef plain = measure("REVENUE", "Sum");
      plain.setHighlightGroup(group("Hi", null));
      VSChartAggregateRef percent = measure("REVENUE", "Sum", new PercentCalc());
      percent.setHighlightGroup(group("Hi", null));
      info.addYField(plain);
      info.addYField(percent);
      ChartHighlightSweep.Snapshot before = ChartHighlightSweep.snapshot(info);

      // one of the two goes away, with no reapply to cover for it
      info.removeYFields();
      info.addYField(plain);

      List<String> notes = ChartHighlightSweep.sweep(info, before, new HashSet<>());

      assertEquals(1, notes.size(), notes.toString());
      assertTrue(notes.get(0).contains("'Hi' on ") && notes.get(0).contains("was removed"),
                 notes.get(0));
   }

   @Test
   void aTextHighlightOnADimensionIsNotTakenForOneOnAnUnaggregatedMeasureOfTheSameColumn() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartDimensionRef dimension = dimension("REVENUE", XConstants.NONE_DATE_GROUP);
      dimension.setTextHighlightGroup(group("T", null));
      VSChartAggregateRef measure = measure("REVENUE", "None");
      measure.setTextHighlightGroup(group("T", null));
      info.addXField(dimension);
      info.addYField(measure);

      Notes notes = rewrite(info, "x");

      assertEquals(1, notes.swept().size(), notes.swept().toString());
      assertTrue(notes.swept().get(0).contains("'T' on REVENUE (text)") &&
                    notes.swept().get(0).contains("on the x shelf"), notes.swept().get(0));
   }

   @Test
   void aChartWithNoHighlightsAddsNothing() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("REGION", XConstants.NONE_DATE_GROUP));
      info.addYField(measure("QUANTITY", "Sum"));

      assertTrue(rewrite(info, "y", agg("REVENUE", "Sum")).all().isEmpty());
   }
}
