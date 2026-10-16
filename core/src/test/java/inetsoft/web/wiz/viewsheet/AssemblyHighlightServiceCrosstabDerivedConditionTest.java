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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.binding.Field;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.service.HighlightService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78540, crosstab half: a highlight condition on a field the crosstab lists only because of
 * the active date comparison was accepted, then deleted by the next binding apply (a derived
 * measure, a re-leveled date dimension) or left dangling (an inserted period dimension).
 *
 * <p>The names here are the live ones. The runtime aggregates are pushed down the way
 * {@code AbstractCrosstabVSAQuery.createAggregates} does it after the comparison has run, so the
 * derived aggregate's full name is {@code Change ...: Sum(Sum(DISCOUNT))}, while the lens header
 * the condition is written against is {@code Change ...: Sum(DISCOUNT).1}. A fixture built only by
 * {@code CrosstabDcProcessor} has neither, and is what let two earlier attempts pass.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssemblyHighlightServiceCrosstabDerivedConditionTest {
   private static final String DERIVED =
      "Change from previous year of Year(ORDER_DATE): Sum(DISCOUNT).1";
   private static final String BASE = "Sum(DISCOUNT)";

   private final HighlightService service = new HighlightService(
      mock(ViewsheetService.class), mock(DataRefModelFactoryService.class));

   private static DateComparisonInfo comparison(int granularity) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(1);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2026-10-31");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setContextLevel(XConstants.YEAR_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);
      return dcInfo;
   }

   private static VSDimensionRef orderDate(int level) {
      AttributeRef attr = new AttributeRef(null, "ORDER_DATE");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      VSDimensionRef dim = new VSDimensionRef(column);
      dim.setGroupColumnValue("ORDER_DATE");
      dim.setDateLevelValue(String.valueOf(level));
      dim.setDataType(XSchema.DATE);
      return dim;
   }

   private static VSAggregateRef sumOf(String column) {
      AttributeRef attr = new AttributeRef(null, column);
      attr.setDataType(XSchema.DOUBLE);
      VSAggregateRef aggregate = new VSAggregateRef();
      aggregate.setDataRef(new ColumnRef(attr));
      aggregate.setColumnValue(column);
      aggregate.setFormula(AggregateFormula.SUM);
      return aggregate;
   }

   /**
    * What {@code AbstractCrosstabVSAQuery.createAggregates} leaves in the runtime aggregates when it
    * pushes Sum(DISCOUNT) into the source: the pre-aggregated column, summed again, with the
    * comparison's calculator carried over.
    */
   private static void pushDown(VSCrosstabInfo info) {
      DataRef[] aggregates = info.getRuntimeAggregates();

      for(int i = 0; i < aggregates.length; i++) {
         VSAggregateRef pushed = sumOf(BASE);
         Calculator calc = ((VSAggregateRef) aggregates[i]).getCalculator();
         // a copy, not the instance: the comparison's calculator is matched by equality
         pushed.setCalculator(calc == null ? null : (Calculator) calc.clone());
         aggregates[i] = pushed;
      }

      info.setRuntimeAggregates(aggregates);
   }

   /** A crosstab with rows ORDER_DATE at {@code level} and Sum(DISCOUNT), under the comparison. */
   private static CrosstabVSAssemblyInfo crosstab(int level, int granularity) {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly assembly = new CrosstabVSAssembly(vs, "Crosstab1");
      vs.addAssembly(assembly);
      CrosstabVSAssemblyInfo assemblyInfo = (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo();
      DateComparisonInfo dcInfo = comparison(granularity);
      assemblyInfo.setDateComparisonInfo(dcInfo);

      VSCrosstabInfo info = assemblyInfo.getVSCrosstabInfo();
      VSDimensionRef design = orderDate(level);
      info.setDesignRowHeaders(new DataRef[] { design });
      info.setDesignAggregates(new DataRef[] { sumOf("DISCOUNT") });

      new CrosstabDcProcessor(info, dcInfo, new DataRef[] { (DataRef) design.clone() },
                              new DataRef[0],
                              new DataRef[] { sumOf("DISCOUNT") }).process("ds", vs);
      pushDown(info);

      VSAggregateRef derived = Arrays.stream(info.getRuntimeAggregates())
         .map(VSAggregateRef.class::cast).filter(a -> a.getCalculator() != null)
         .findFirst().orElseThrow();
      // The live names: the runtime full name is pushed down, the listed one is not.
      assertEquals("Change from previous year of Year(ORDER_DATE): Sum(Sum(DISCOUNT))",
                   derived.getFullName());
      assertTrue(DateComparisonUtil.appliedDateComparison(assemblyInfo));
      return assemblyInfo;
   }

   private static Field[] lens(String... names) {
      return Arrays.stream(names).map(BaseField::new).toArray(Field[]::new);
   }

   private static AssemblyHighlightService.Highlight highlightOn(String field) {
      return new AssemblyHighlightService.Highlight(
         "h", "#ff0000", null, null,
         List.of(new ConditionVocabulary.Clause(field, ">", List.of(0), null, false, false, 0)),
         false);
   }

   private void assertRefused(CrosstabVSAssemblyInfo info, Field[] fields, String field) {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> AssemblyHighlightService.requireNoDerivedConditionField(
            highlightOn(field), service.getDateComparisonFields(info, fields)));
      assertTrue(e.getMessage().contains("'" + field + "'"), e.getMessage());
      assertTrue(e.getMessage().toLowerCase().contains("date comparison"), e.getMessage());
   }

   private void assertAccepted(CrosstabVSAssemblyInfo info, Field[] fields, String field) {
      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         highlightOn(field), service.getDateComparisonFields(info, fields)));
   }

   // L1: rows Year(ORDER_DATE), the comparison at year granularity. Lens headers as listed live.
   private static final Field[] YEAR_LENS = lens(DERIVED, BASE, "Year(ORDER_DATE)");

   @Test
   void derivedMeasureUnderItsListedLensNameIsRefused() {
      assertRefused(crosstab(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR), YEAR_LENS,
                    DERIVED);
   }

   @Test
   void derivedMeasureMatchIsCaseInsensitiveLikeTheVocabulary() {
      assertRefused(crosstab(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR), YEAR_LENS,
                    DERIVED.toLowerCase());
   }

   @Test
   void baseMeasureIsAccepted() {
      assertAccepted(crosstab(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR), YEAR_LENS,
                     BASE);
   }

   @Test
   void baseDateDimensionTheComparisonKeptIsAccepted() {
      assertAccepted(crosstab(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR), YEAR_LENS,
                     "Year(ORDER_DATE)");
   }

   // S5: rows Month(ORDER_DATE), year periods at month granularity: the comparison re-levels the
   // date dimension to MonthOfYear and inserts a Year period dimension. Lens headers as listed live.
   private static final Field[] MONTH_LENS =
      lens(DERIVED, "MonthOfYear(ORDER_DATE)", BASE, "Year(ORDER_DATE)");

   @Test
   void releveledDateDimensionIsRefused() {
      assertRefused(crosstab(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH), MONTH_LENS,
                    "MonthOfYear(ORDER_DATE)");
   }

   @Test
   void insertedPeriodDimensionIsRefused() {
      assertRefused(crosstab(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH), MONTH_LENS,
                    "Year(ORDER_DATE)");
   }

   @Test
   void derivedMeasureIsRefusedNextToTheComparisonDimensions() {
      assertRefused(crosstab(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH), MONTH_LENS,
                    DERIVED);
   }

   @Test
   void baseMeasureIsAcceptedNextToTheComparisonDimensions() {
      assertAccepted(crosstab(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH), MONTH_LENS,
                     BASE);
   }

   /**
    * The pairing hazard: the derived column's lens header is not calc-prefixed and comes first, so
    * the base aggregate takes it through the ".1" suffix and the comparison aggregate takes nothing.
    * Which measure that header is cannot be told, so it is refused, not called "base".
    */
   @Test
   void unpairableAggregateHeaderIsRefusedNotDefaultedToBase() {
      assertRefused(crosstab(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.YEAR),
                    lens(BASE + ".1", BASE, "Year(ORDER_DATE)"), BASE + ".1");
   }

   /** A design running total with no date comparison is a calculator, not a derived field. */
   @Test
   void designRunningTotalWithoutComparisonIsAccepted() {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly assembly = new CrosstabVSAssembly(vs, "Crosstab1");
      vs.addAssembly(assembly);
      CrosstabVSAssemblyInfo assemblyInfo = (CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo();
      VSCrosstabInfo info = assemblyInfo.getVSCrosstabInfo();
      VSDimensionRef design = orderDate(XConstants.YEAR_DATE_GROUP);
      VSAggregateRef total = sumOf(BASE);
      RunningTotalCalc calc = new RunningTotalCalc();
      total.setCalculator(calc);
      info.setDesignRowHeaders(new DataRef[] { design });
      info.setRuntimeRowHeaders(new DataRef[] { (DataRef) design.clone() });
      info.setRuntimeAggregates(new DataRef[] { total });
      String listed = calc.getPrefix() + BASE;

      assertFalse(DateComparisonUtil.appliedDateComparison(assemblyInfo));
      assertAccepted(assemblyInfo, lens(listed, "Year(ORDER_DATE)"), listed);
   }
}
