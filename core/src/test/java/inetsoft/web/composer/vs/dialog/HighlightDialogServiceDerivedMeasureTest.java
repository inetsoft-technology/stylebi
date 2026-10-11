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
package inetsoft.web.composer.vs.dialog;

import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78310: setting a highlight on a measure derived by the date comparison ("Change from
 * previous year of Year(D): Sum(M)") was accepted and then lost on clear. The earlier guards were
 * tested against hand-built shapes that never occur; this drives the real runtime shape, built by
 * ChartDcProcessor, in which the derived measure is a runtime Y field carrying a calculator.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HighlightDialogServiceDerivedMeasureTest {
   private static final HighlightDialogService.HighlightConverter CONVERT = m -> {
      TextHighlight highlight = new TextHighlight();
      highlight.setName(m.getName());
      return highlight;
   };

   private record Chart(VSChartInfo info, String derivedName) {
   }

   /** A design chart Year(D) x Sum(M) with the date comparison applied to the runtime fields. */
   private static Chart comparisonChart(int option) {
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
      dcInfo.setComparisonOption(option);

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

      new ChartDcProcessor(info, dcInfo).process("ds", new Viewsheet());

      String derivedName = Arrays.stream(info.getRTYFields())
         .filter(r -> r instanceof VSChartAggregateRef a && a.getCalculator() != null)
         .map(r -> ((VSChartAggregateRef) r).getFullName()).findFirst().orElse(null);
      assertNotNull(derivedName, "date comparison must produce a derived measure");
      assertNull(info.getFieldByName(derivedName, false), "derived measure has no design ref");

      return new Chart(info, derivedName);
   }

   private static HighlightDialogModel dialogModel(String measure, boolean rejectDerived,
                                                   String... names)
   {
      HighlightDialogModel model = new HighlightDialogModel();
      model.setMeasure(measure);
      model.setRejectDerivedMeasure(rejectDerived);
      HighlightModel[] highlights = new HighlightModel[names.length];

      for(int i = 0; i < names.length; i++) {
         highlights[i] = new HighlightModel();
         highlights[i].setName(names[i]);
      }

      model.setHighlights(highlights);
      return model;
   }

   private static void assertRefused(int option) {
      Chart chart = comparisonChart(option);
      VSChartInfo info = (VSChartInfo) chart.info().clone();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> HighlightDialogService.applyChartHighlights(
            info, dialogModel(chart.derivedName(), true, "Hot"), CONVERT));
      assertTrue(e.getMessage().contains(chart.derivedName()), e.getMessage());
      assertTrue(e.getMessage().contains("derived"), e.getMessage());
   }

   @Test
   void changeAndValueDerivedMeasureIsRefused() {
      assertRefused(DateComparisonInfo.CHANGE_VALUE);
   }

   @Test
   void percentChangeAndValueDerivedMeasureIsRefused() {
      assertRefused(DateComparisonInfo.PERCENT_VALUE);
   }

   @Test
   void changeOnlyDerivedMeasureIsRefused() {
      assertRefused(DateComparisonInfo.CHANGE);
   }

   @Test
   void derivedMeasureIsAcceptedOnTheGuiPath() {
      Chart chart = comparisonChart(DateComparisonInfo.CHANGE_VALUE);
      VSChartInfo info = (VSChartInfo) chart.info().clone();

      assertDoesNotThrow(() -> HighlightDialogService.applyChartHighlights(
         info, dialogModel(chart.derivedName(), false, "Hot"), CONVERT));
   }

   @Test
   void baseMeasureStillAcceptedAndPersistedOnTheDesignRef() throws Exception {
      Chart chart = comparisonChart(DateComparisonInfo.CHANGE_VALUE);
      VSChartInfo info = (VSChartInfo) chart.info().clone();
      VSChartAggregateRef design = (VSChartAggregateRef) info.getYFields()[0];

      HighlightDialogService.applyChartHighlights(
         info, dialogModel(design.getFullName(), true, "Hot"), CONVERT);

      HighlightGroup group = design.getHighlightGroup();
      assertNotNull(group);
      assertNotNull(group.getHighlight("Hot"));
      info.setRuntimeDateComparisonRefs(null);
      assertNotNull(design.getHighlightGroup().getHighlight("Hot"));
   }

   @Test
   void noComparisonMeansNoRefusal() {
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef("v"));
      measure.setFormula(AggregateFormula.SUM);
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(measure);

      assertDoesNotThrow(() -> HighlightDialogService.applyChartHighlights(
         info, dialogModel("Change from previous year of Year(date): Sum(v)", true, "Hot"),
         CONVERT));
   }
}
