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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GeographicOption;
import inetsoft.uql.viewsheet.graph.VSChartGeoRef;
import inetsoft.uql.viewsheet.graph.VSMapInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for bug-75798's map sibling: a "show sales by province" prompt over real
 * U.S. state data (STATE column values like "NJ"/"CA"/"NY") rendered as a map of CANADA, because
 * wiz-services naively matches the word "province" in the user's wording to the only StyleBI
 * layer literally called that -- Canada's -- and {@code WizVsService.createGeoFields} writes that
 * guess into the chart unconditionally. StyleBI's own {@code MapHelper.autoDetect} is data-driven,
 * but short-circuits whenever a recognized (not necessarily correct) map type is already set, so
 * it never catches the bad guess either.
 *
 * <p>Exercises {@link WizVsService#correctWizGeoMapping} directly -- package-private for exactly
 * this reason, the same as {@link WizVsService#executeAndExtract} -- rather than standing up a
 * real {@link inetsoft.report.composition.execution.ViewsheetSandbox}.
 *
 * <p>Calls {@link MapHelper#autoDetect} directly (not through {@code VSChartHandler.autoDetect}):
 * a live break during manual testing showed that wrapper re-resolves the geo ref itself via
 * runtime-index alignment between {@code getRTGeoColumns()} and {@code getGeoColumns()}, which on
 * a real (post-execution) chart did not line up and silently no-opped, leaving the mapping this
 * fix had just cleared never re-populated. No {@code VSChartHandler} is needed here as a result.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceCorrectsWrongGeoMapTypeTest {
   private static DataSet usStatesDataSet() {
      return new DefaultDataSet(new Object[][] {
         { "STATE" },
         { "California" },
         { "New Jersey" },
         { "New York" },
      });
   }

   /**
    * Builds a map chart the same shape {@code WizVsService.createChartAssembly} produces: one
    * geographic dimension on "STATE", seeded (via {@code createGeoFields}'s equivalent) with the
    * given (possibly wrong) map type/layer guess.
    */
   private static ChartVSAssembly mapChart(Viewsheet vs, String type, String layerName) {
      VSMapInfo mapInfo = new VSMapInfo();

      VSChartGeoRef designGeo = new VSChartGeoRef();
      designGeo.setGroupColumnValue("STATE");
      GeographicOption option = designGeo.getGeographicOption();
      option.setLayerValue(String.valueOf(MapData.getLayer(layerName)));
      option.getMapping().setType(type);
      mapInfo.getGeoColumns().addAttribute(designGeo);

      VSChartGeoRef boundGeo = new VSChartGeoRef();
      boundGeo.setGroupColumnValue("STATE");
      mapInfo.addGeoField(boundGeo);

      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "STATE")));
      mapInfo.updateGeoColumns(vs, columns);

      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      chart.setVSChartInfo(mapInfo);
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "ws1"));
      vs.addAssembly(chart);
      return chart;
   }

   private static RuntimeViewsheet rvsFor(Viewsheet vs) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static WizVsService newService() {
      return new WizVsService(null, null, null, null, null);
   }

   @Test
   void resetsAndRedetectsWhenTheGuessedTypeMatchesNoneOfTheRealValues() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = mapChart(vs, "Canada", "Province");
      WizVsService service = newService();

      boolean corrected = service.correctWizGeoMapping(rvsFor(vs), "chart1", usStatesDataSet());

      assertTrue(corrected, "a map type that matches none of the real values must be corrected");
      VSMapInfo mapInfo = (VSMapInfo) chart.getVSChartInfo();
      assertEquals("U.S.", mapInfo.getMapType(),
         "real U.S. state data must be re-detected as U.S., not left as the wrong Canada guess");
   }

   /** The exact 14 real STATE abbreviations from bug-75983's production data (not just the clean,
    * spelled-out synthetic set above) -- confirms abbreviations re-detect correctly too. */
   @Test
   void resetsAndRedetectsRealAbbreviatedStateCodes() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = mapChart(vs, "Canada", "Province");
      WizVsService service = newService();

      DataSet abbreviations = new DefaultDataSet(new Object[][] {
         { "STATE" }, { "AZ" }, { "CA" }, { "CO" }, { "CT" }, { "FL" }, { "IL" }, { "MA" },
         { "MD" }, { "NJ" }, { "NV" }, { "NY" }, { "PA" }, { "TX" }, { "WA" },
      });

      boolean corrected = service.correctWizGeoMapping(rvsFor(vs), "chart1", abbreviations);

      assertTrue(corrected, "2-letter state abbreviations must also be re-detected correctly");
      VSMapInfo mapInfo = (VSMapInfo) chart.getVSChartInfo();
      assertEquals("U.S.", mapInfo.getMapType());
   }

   @Test
   void leavesACorrectlyGuessedTypeAlone() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = mapChart(vs, "U.S.", "State");
      WizVsService service = newService();

      boolean corrected = service.correctWizGeoMapping(rvsFor(vs), "chart1", usStatesDataSet());

      assertFalse(corrected, "a map type that already matches real values must not be touched");
      VSMapInfo mapInfo = (VSMapInfo) chart.getVSChartInfo();
      assertEquals("U.S.", mapInfo.getMapType());
   }

   /**
    * Regression for a live break found via manual testing (bug-75983): {@code MapHelper.autoDetect}
    * can settle the mapping's type back to null instead of a usable type when re-detection is
    * inconclusive. Rendering a chart whose geo mapping type is null throws ("X layer is not
    * supported by null"), which is strictly worse than the original wrong-but-renderable guess.
    * Mocks the static {@code MapHelper.autoDetect} to force exactly that outcome, since coaxing
    * StyleBI's real feature-matching heuristics into an inconclusive result deterministically would
    * require a real, messy dataset this test can't fabricate.
    */
   @Test
   void rollsBackToTheOriginalGuessWhenRedetectionIsInconclusive() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = mapChart(vs, "Canada", "Province");
      WizVsService service = newService();

      try(MockedStatic<MapHelper> mapHelper = mockStatic(MapHelper.class, invocation -> {
         // Delegate every static call except autoDetect to the real implementation.
         if("autoDetect".equals(invocation.getMethod().getName())) {
            return null;
         }
         return invocation.callRealMethod();
      })) {
         boolean corrected = service.correctWizGeoMapping(rvsFor(vs), "chart1", usStatesDataSet());

         assertFalse(corrected, "an inconclusive re-detection must not be reported as a correction");
      }

      VSMapInfo mapInfo = (VSMapInfo) chart.getVSChartInfo();
      assertEquals("Canada", mapInfo.getMapType(),
         "the original guess must be restored rather than left with a null/unrenderable type");
   }

   @Test
   void ignoresNonMapCharts() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      chart.setVSChartInfo(new DefaultVSChartInfo());
      vs.addAssembly(chart);
      WizVsService service = newService();

      assertFalse(service.correctWizGeoMapping(rvsFor(vs), "chart1", usStatesDataSet()));
   }

   @Test
   void noopWhenSourceIsNull() {
      Viewsheet vs = new Viewsheet();
      mapChart(vs, "Canada", "Province");
      WizVsService service = newService();

      assertFalse(service.correctWizGeoMapping(rvsFor(vs), "chart1", null));
   }
}
