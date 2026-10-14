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

import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
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
 * and then dangled (chart) or was dropped (crosstab) once the comparison was cleared. The chart
 * shape is the real one built by ChartDcProcessor.
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

   /** Built through the real CrosstabDcProcessor, as VSCrosstabInfo.updateRuntimeRefs does. */
   private static Built crosstab() {
      AttributeRef dateAttr = new AttributeRef("date");
      dateAttr.setDataType(XSchema.DATE);
      VSDimensionRef dateDim = new VSDimensionRef(dateAttr);
      dateDim.setDateLevel(XConstants.YEAR_DATE_GROUP);
      dateDim.setDataType(XSchema.DATE);

      VSAggregateRef base = new VSAggregateRef();
      base.setDataRef(new AttributeRef("v"));
      base.setColumnValue("v");
      base.setFormula(AggregateFormula.SUM);

      VSCrosstabInfo info = new VSCrosstabInfo();
      info.setDesignRowHeaders(new DataRef[] { dateDim });
      info.setDesignAggregates(new DataRef[] { base });

      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly assembly = new CrosstabVSAssembly(vs, "Crosstab1");
      assembly.setVSCrosstabInfo(info);
      DateComparisonInfo dcInfo = chartDcInfo();
      ((CrosstabVSAssemblyInfo) assembly.getVSAssemblyInfo()).setDateComparisonInfo(dcInfo);
      vs.addAssembly(assembly);

      new CrosstabDcProcessor(info, dcInfo, new DataRef[] { (DataRef) dateDim.clone() },
                              new DataRef[0], new DataRef[] { (DataRef) base.clone() })
         .process("ds", vs);

      String derived = Arrays.stream(info.getRuntimeAggregates())
         .filter(r -> r instanceof VSAggregateRef a && a.getCalculator() != null)
         .map(r -> ((VSAggregateRef) r).getFullName()).findFirst().orElse(null);
      assertNotNull(derived, "date comparison must produce a derived aggregate");
      return new Built(assembly, base.getFullName(), derived);
   }

   private static DateComparisonInfo chartDcInfo() {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(1);
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.YEAR);
      interval.setLevel(DateComparisonInfo.ALL);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);
      return dcInfo;
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
   void crosstabConditionOnTheDerivedMeasureIsRefused() {
      Built built = crosstab();
      assertRefused(built, built.derivedName());
      assertRefused(built, built.derivedName().toUpperCase());
   }

   /** The live crosstab lists the derived column by its table header, with a ".N" suffix. */
   @Test
   void crosstabConditionOnTheListedDerivedColumnWithADedupSuffixIsRefused() {
      Built built = crosstab();
      assertRefused(built, built.derivedName() + ".1");
   }

   @Test
   void crosstabBaseColumnWithADedupSuffixIsAccepted() {
      Built built = crosstab();
      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), highlightOn(built.baseName() + ".1")));
   }

   @Test
   void crosstabConditionOnTheBaseMeasureIsAccepted() {
      Built built = crosstab();
      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), highlightOn(built.baseName())));
   }

   @Test
   void noComparisonMeansNoRefusal() {
      Built built = crosstab();
      VSCrosstabInfo info = ((CrosstabVSAssembly) built.assembly()).getVSCrosstabInfo();
      info.setRuntimeAggregates(info.getDesignAggregates());

      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         built.assembly(), highlightOn(built.derivedName())));
   }
}
