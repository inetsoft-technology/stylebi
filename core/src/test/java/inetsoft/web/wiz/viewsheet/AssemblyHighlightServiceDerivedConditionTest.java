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

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78540 P3: a highlight condition naming a measure the date comparison derives was accepted
 * and then dangled once the comparison was cleared. The chart shape is the real one built by
 * ChartDcProcessor. (The crosstab half is tracked separately.)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssemblyHighlightServiceDerivedConditionTest {
   private record Built(VSAssembly assembly, String baseName, String derivedName) {
   }

   private static Built chart() {
      return chart(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.ALL);
   }

   private static Built chart(int dateLevel, int intervalLevel) {
      return chart(dateLevel, intervalLevel, DateComparisonInfo.YEAR, XConstants.YEAR_DATE_GROUP);
   }

   private static Built chart(int dateLevel, int intervalLevel, int granularity,
                              int contextLevel)
   {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(intervalLevel);
      interval.setEndDayAsToDate(true);
      interval.setContextLevel(contextLevel);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);

      AttributeRef dateAttr = new AttributeRef("date");
      dateAttr.setDataType(XSchema.DATE);
      VSChartDimensionRef dateDim = new VSChartDimensionRef(dateAttr);
      dateDim.setDateLevel(dateLevel);
      dateDim.setDataType(XSchema.DATE);

      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef("v"));
      measure.setColumnValue("v");
      measure.setFormula(AggregateFormula.SUM);

      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.addXField(dateDim);
      info.addYField(measure);
      info.setRTXFields(new ChartRef[] { (ChartRef) dateDim.clone() });
      info.setRTYFields(new ChartRef[] { (ChartRef) measure.clone() });

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly assembly = new ChartVSAssembly(vs, "Chart1");
      assembly.setVSChartInfo(info);
      vs.addAssembly(assembly);

      new ChartDcProcessor(info, dcInfo).process("ds", vs);

      String derived = Arrays.stream(info.getRTYFields())
         .filter(r -> r instanceof VSChartAggregateRef a && a.getCalculator() != null)
         .map(r -> ((VSChartAggregateRef) r).getFullName()).findFirst().orElse(null);
      assertNotNull(derived, "date comparison must produce a derived measure");
      assertNull(info.getFieldByName(derived, false), "derived measure has no design ref");
      return new Built(assembly, measure.getFullName(), derived);
   }

   private static AssemblyHighlightService.Highlight highlightOn(String... fields) {
      List<ConditionVocabulary.Clause> clauses = new java.util.ArrayList<>();

      for(int i = 0; i < fields.length; i++) {
         clauses.add(new ConditionVocabulary.Clause(fields[i], ">", List.of(0),
                                                    i < fields.length - 1 ? "and" : null,
                                                    false, false, 0));
      }

      return new AssemblyHighlightService.Highlight("h", "#ff0000", null, null, clauses, false);
   }

   private static void assertRefused(Built built, String field) {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> AssemblyHighlightService.requireNoDerivedConditionField(
            built.assembly(), highlightOn(field)));
      assertTrue(e.getMessage().contains("derived"), e.getMessage());
   }

   @Test
   void chartConditionOnTheDerivedMeasureIsRefused() {
      Built built = chart();
      assertRefused(built, built.derivedName());
   }

   @Test
   void chartDerivedMatchIsCaseInsensitiveLikeTheVocabulary() {
      Built built = chart();
      assertRefused(built, built.derivedName().toLowerCase());
   }

   @Test
   void chartConditionOnTheBaseMeasureAndDimensionIsAccepted() {
      Built built = chart();
      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), highlightOn(built.baseName(), "Year(date)")));
   }

   @Test
   void noComparisonMeansNoRefusal() {
      Built built = chart();
      ((ChartVSAssemblyInfo) built.assembly().getInfo()).getVSChartInfo()
         .setRuntimeDateComparisonRefs(null);

      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), highlightOn(built.derivedName())));
   }

   private static VSChartInfo chartInfo(Built built) {
      return ((ChartVSAssemblyInfo) built.assembly().getInfo()).getVSChartInfo();
   }

   private static List<String> derivedDimensions(Built built) {
      VSChartInfo info = chartInfo(built);
      return Arrays.stream(info.getRuntimeDateComparisonRefs())
         .filter(r -> r instanceof VSChartDimensionRef)
         .map(ChartRef::getFullName)
         .filter(n -> info.getFieldByName(n, false) == null).toList();
   }

   private static AssemblyHighlightService.Highlight operandHighlight(String conditionField,
                                                                       String operator,
                                                                       List<Object> values)
   {
      return new AssemblyHighlightService.Highlight(
         "h", "#ff0000", null, null,
         List.of(new ConditionVocabulary.Clause(conditionField, operator, values, null, false,
                                                false, 0)),
         false);
   }

   private static Map<String, Object> fieldOperand(String type, String field) {
      return Map.of("type", type, "field", field);
   }

   private static void assertOperandRefused(Built built, AssemblyHighlightService.Highlight hl,
                                            String named)
   {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> AssemblyHighlightService.requireNoDerivedConditionField(built.assembly(), hl));
      assertTrue(e.getMessage().contains("'" + named + "'"), e.getMessage());
      assertTrue(e.getMessage().toLowerCase().contains("date comparison"), e.getMessage());
   }

   @Test
   void fieldTypedValueOperandOnADerivedMeasureOrDimensionIsRefused() {
      Built built = chart(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH);
      List<String> names = new java.util.ArrayList<>(derivedDimensions(built));
      names.add(built.derivedName());
      assertTrue(names.size() > 1);

      for(String name : names) {
         assertOperandRefused(built, operandHighlight(built.baseName(), ">",
            List.of(fieldOperand("field", name))), name);
      }
   }

   @Test
   void fieldTypedValueOperandTypeIsCaseInsensitiveAndNameTrimmed() {
      Built built = chart();
      assertOperandRefused(built, operandHighlight(built.baseName(), ">",
         List.of(fieldOperand(" FIELD ", " " + built.derivedName() + " "))), built.derivedName());
   }

   @Test
   void derivedOperandInTheSecondBetweenSlotIsRefused() {
      Built built = chart();
      assertOperandRefused(built, operandHighlight(built.baseName(), "between",
         List.of(0, fieldOperand("field", built.derivedName()))), built.derivedName());
   }

   @Test
   void rankingGroupFieldOnADerivedFieldIsRefused() {
      Built built = chart(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH);
      String dim = derivedDimensions(built).get(0);
      assertOperandRefused(built, operandHighlight(built.baseName(), "top_n",
         List.of(Map.of("n", 3, "groupField", dim))), dim);
   }

   @Test
   void operandOnABaseFieldOrALiteralIsAccepted() {
      Built built = chart();
      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), operandHighlight(built.baseName(), ">",
            List.of(fieldOperand("field", built.baseName())))));
      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), operandHighlight(built.baseName(), "top_n",
            List.of(Map.of("n", 3, "groupField", "Year(date)")))));
      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), operandHighlight(built.baseName(), "=",
            List.of(built.derivedName(), Map.of("type", "variable", "name", "x")))));
   }

   @Test
   void chartConditionOnAReLeveledOrInsertedDimensionIsRefused() {
      Built built = chart(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH);
      List<String> dims = derivedDimensions(built);
      assertFalse(dims.isEmpty(), "date comparison must derive a dimension");

      for(String dim : dims) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> AssemblyHighlightService.requireNoDerivedConditionField(
               built.assembly(), highlightOn(built.baseName(), dim.toLowerCase())));
         assertTrue(e.getMessage().contains("date comparison"), e.getMessage());
      }
   }

   @Test
   void chartConditionOnADimensionTheComparisonKeptIsAccepted() {
      Built built = chart();
      VSChartInfo info = chartInfo(built);
      String kept = Arrays.stream(info.getRuntimeDateComparisonRefs())
         .filter(r -> r instanceof VSChartDimensionRef)
         .map(ChartRef::getFullName)
         .filter(n -> info.getFieldByName(n, false) != null).findFirst().orElse(null);
      assertNotNull(kept, "the design dimension the comparison kept must be listed");

      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), highlightOn(kept)));
   }

   @Test
   void chartConditionOnATempGroupWithoutADesignFieldIsRefused() {
      Built built = chart();
      AttributeRef attr = new AttributeRef("other");
      attr.setDataType(XSchema.DATE);
      VSChartDimensionRef temp = new VSChartDimensionRef(attr);
      temp.setDateLevel(XConstants.DAY_DATE_GROUP);
      temp.setDataType(XSchema.DATE);
      chartInfo(built).setDcTempGroups(new XDimensionRef[] { temp });
      assertNull(chartInfo(built).getFieldByName(temp.getFullName(), false));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> AssemblyHighlightService.requireNoDerivedConditionField(
            built.assembly(), highlightOn(temp.getFullName().toUpperCase())));
      assertTrue(e.getMessage().contains("date comparison"), e.getMessage());
   }

   @Test
   void chartConditionOnTheProcessorsOwnTempGroupIsRefused() {
      Built built = chart(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH_TO_DATE,
                          DateComparisonInfo.YEAR, XConstants.YEAR_DATE_GROUP);
      VSChartInfo info = chartInfo(built);
      XDimensionRef[] temps = info.getDcTempGroups();
      assertNotNull(temps, "the processor must produce temp groups");
      assertTrue(temps.length > 0, "the processor must produce temp groups");

      for(XDimensionRef temp : temps) {
         String name = temp.getFullName();
         assertNull(info.getFieldByName(name, false), name);
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> AssemblyHighlightService.requireNoDerivedConditionField(
               built.assembly(), highlightOn(name.toUpperCase())));
         assertTrue(e.getMessage().contains("date comparison"), e.getMessage());
      }
   }

   @Test
   void refusalAdviceDependsOnWhetherTheFieldIsAMeasureOrADimension() {
      Built built = chart(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH);
      String dim = derivedDimensions(built).get(0);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> AssemblyHighlightService.requireNoDerivedConditionField(
            built.assembly(), highlightOn(dim)));
      assertTrue(e.getMessage().contains("date comparison"), e.getMessage());
      assertFalse(e.getMessage().contains("base measure"), e.getMessage());
      assertTrue(e.getMessage().contains("listed without the date comparison"), e.getMessage());

      Exception m = assertThrows(IllegalArgumentException.class,
         () -> AssemblyHighlightService.requireNoDerivedConditionField(
            built.assembly(), highlightOn(built.derivedName())));
      assertTrue(m.getMessage().contains("Put the condition on the base measure instead."),
                 m.getMessage());
   }

   @Test
   void noComparisonMeansADimensionIsNotRefused() {
      Built built = chart(XConstants.MONTH_DATE_GROUP, DateComparisonInfo.MONTH);
      List<String> dims = derivedDimensions(built);
      chartInfo(built).setRuntimeDateComparisonRefs(null);

      for(String dim : dims) {
         assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
            built.assembly(), highlightOn(dim)));
      }
   }
}
