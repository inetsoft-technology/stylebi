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
      interval.setGranularity(DateComparisonInfo.YEAR);
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setContextLevel(XConstants.YEAR_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);

      AttributeRef dateAttr = new AttributeRef("date");
      dateAttr.setDataType(XSchema.DATE);
      VSChartDimensionRef dateDim = new VSChartDimensionRef(dateAttr);
      dateDim.setDateLevel(XConstants.YEAR_DATE_GROUP);
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
}
