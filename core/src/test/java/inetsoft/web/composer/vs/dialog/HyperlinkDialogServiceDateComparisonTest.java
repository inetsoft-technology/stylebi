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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78540 P2: a link set while a date comparison was active landed only on runtime clones
 * (the design measure is replaced by them) and was lost when the comparison was cleared. Drives
 * the real runtime shape built by ChartDcProcessor.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HyperlinkDialogServiceDateComparisonTest {
   private static final Principal USER = () -> "admin";

   private static class Fixture {
      HyperlinkDialogService service;
      VSObjectPropertyService props;
      ChartVSAssemblyInfo info;
      String derivedName;
      String baseName;
   }

   private static Fixture fixture(int option) throws Exception {
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

      VSChartInfo chartInfo = new DefaultVSChartInfo();
      chartInfo.setChartDescriptor(new ChartDescriptor());
      chartInfo.addXField(dateDim);
      chartInfo.addYField(measure);
      chartInfo.setRTXFields(new ChartRef[] { (ChartRef) dateDim.clone() });
      chartInfo.setRTYFields(new ChartRef[] { (ChartRef) measure.clone() });

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly assembly = new ChartVSAssembly(vs, "Chart1");
      assembly.setVSChartInfo(chartInfo);
      ((ChartVSAssemblyInfo) assembly.getVSAssemblyInfo()).setDateComparisonInfo(dcInfo);
      vs.addAssembly(assembly);

      new ChartDcProcessor(chartInfo, dcInfo).process("ds", vs);

      Fixture f = new Fixture();
      f.info = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      f.baseName = measure.getFullName();
      f.derivedName = Arrays.stream(chartInfo.getRTYFields())
         .filter(r -> r instanceof VSChartAggregateRef a && a.getCalculator() != null)
         .map(r -> ((VSChartAggregateRef) r).getFullName()).findFirst().orElse(null);
      assertNotNull(f.derivedName, "date comparison must produce a derived measure");
      assertNotNull(f.info.getDCBIndingRef(f.baseName),
                    "fixture must reproduce the date-comparison-applied state");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      f.props = mock(VSObjectPropertyService.class);
      f.service = new HyperlinkDialogService(null, f.props, viewsheetService, null, null, null,
                                             null, null);
      return f;
   }

   private static HyperlinkDialogModel model(String colName, boolean reject, boolean clear) {
      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setColName(colName);
      model.setLinkType(clear ? HyperlinkDialogService.NONE : Hyperlink.WEB_LINK);
      model.setWebLink("https://example.com/base");
      model.setRejectDerivedMeasure(reject);
      return model;
   }

   private static VSChartAggregateRef design(ChartVSAssemblyInfo info) {
      return (VSChartAggregateRef) info.getVSChartInfo().getYFields()[0];
   }

   private static ChartVSAssemblyInfo written(Fixture f) throws Exception {
      ArgumentCaptor<VSAssemblyInfo> captor = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(f.props).editObjectProperty(any(), captor.capture(), anyString(), anyString(),
                                         any(), any(), any(), anyBoolean(), any());
      return (ChartVSAssemblyInfo) captor.getValue();
   }

   private static void clearComparison(ChartVSAssemblyInfo info) {
      info.getVSChartInfo().setRuntimeDateComparisonRefs(null);
   }

   @Test
   void linkOnTheBaseMeasureSurvivesClearingTheComparison() throws Exception {
      for(int option : new int[] { DateComparisonInfo.CHANGE_VALUE,
                                   DateComparisonInfo.PERCENT_VALUE })
      {
         Fixture f = fixture(option);
         f.service.setHyperlinkDialogModel("rt", "Chart1", model(f.baseName, true, false), "",
                                           USER, null);
         ChartVSAssemblyInfo out = written(f);
         clearComparison(out);

         assertNotNull(design(out).getHyperlink(), "design ref must carry the link");
         assertEquals("https://example.com/base", design(out).getHyperlink().getLink());
      }
   }

   @Test
   void clearingALinkWhileTheComparisonIsActiveDoesNotResurrectItOnClear() throws Exception {
      Fixture f = fixture(DateComparisonInfo.CHANGE_VALUE);
      Hyperlink old = new Hyperlink();
      old.setLinkType(Hyperlink.WEB_LINK);
      old.setLink("https://example.com/old");
      design(f.info).setHyperlink(old);

      f.service.setHyperlinkDialogModel("rt", "Chart1", model(f.baseName, true, true), "",
                                        USER, null);
      ChartVSAssemblyInfo out = written(f);
      clearComparison(out);

      assertNull(design(out).getHyperlink(), "a cleared link must not come back on clear");
   }

   @Test
   void linkOnADerivedMeasureIsRefused() throws Exception {
      Fixture f = fixture(DateComparisonInfo.CHANGE_VALUE);

      Exception e = assertThrows(IllegalArgumentException.class, () ->
         f.service.setHyperlinkDialogModel("rt", "Chart1", model(f.derivedName, true, false), "",
                                           USER, null));
      assertTrue(e.getMessage().contains(f.derivedName) && e.getMessage().contains("derived"),
                 e.getMessage());
      verify(f.props, never()).editObjectProperty(any(), any(), anyString(), anyString(), any(),
                                                  any(), any(), anyBoolean(), any());
   }

   @Test
   void derivedMeasureIsAcceptedOnTheGuiPath() {
      assertDoesNotThrow(() -> {
         Fixture f = fixture(DateComparisonInfo.CHANGE_VALUE);
         f.service.setHyperlinkDialogModel("rt", "Chart1", model(f.derivedName, false, false), "",
                                           USER, null);
      });
   }
}
