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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression coverage for a live bug: a SAVED VISUALIZATION accumulating a second chart.
 *
 * <p>A saved visualization IS one chart — {@code ViewsheetRuntimeController#findChartAssembly}
 * resolves an asset to its FIRST ChartVSAssembly, and open/verify/reload all report that single
 * assembly. But re-binding a REOPENED saved viz does not replace its chart: the general
 * create/rebind path demotes the displaced primary and adds the new assembly beside it (deliberate
 * and correct for a SESSION viewsheet, which holds one card per turn), and persistViewsheet then
 * writes back whatever the viewsheet contains.
 *
 * <p>Observed live: a board's A1 chart, re-bound after a dataset regeneration, left its superseded
 * chart behind. Composing that board produced 6 chart assemblies for 5 tiles and the PDF export
 * failed its tile/caption count check. The orphan was invisible everywhere else, because every
 * other reader takes the first chart and stops.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServicePruneSupersededChartsTest {

   private static ChartVSAssembly chart(Viewsheet vs, String name, boolean primary) {
      ChartVSAssembly c = new ChartVSAssembly(vs, name);
      vs.addAssembly(c);
      c.setPrimary(primary);
      return c;
   }

   private static long chartCount(Viewsheet vs) {
      return Arrays.stream(vs.getAssemblies()).filter(a -> a instanceof ChartVSAssembly).count();
   }

   @Test
   void dropsTheSupersededChartAndKeepsThePrimary() {
      Viewsheet vs = new Viewsheet();
      chart(vs, "vs_old", false);   // demoted by an earlier re-bind, never removed
      chart(vs, "vs_new", true);

      WizVsService.pruneSupersededCharts(vs);

      assertNotNull(vs.getAssembly("vs_new"), "the primary chart survives");
      assertNull(vs.getAssembly("vs_old"), "the superseded chart must not be persisted");
      assertEquals(1, chartCount(vs));
   }

   @Test
   void leavesASingleChartAloneWhetherOrNotItIsPrimary() {
      Viewsheet one = new Viewsheet();
      chart(one, "vs_only", true);
      WizVsService.pruneSupersededCharts(one);
      assertNotNull(one.getAssembly("vs_only"));

      // No primary at all: there is no basis for choosing a survivor, so nothing is touched.
      // Guessing here would risk deleting the real chart.
      Viewsheet noPrimary = new Viewsheet();
      chart(noPrimary, "vs_a", false);
      chart(noPrimary, "vs_b", false);
      WizVsService.pruneSupersededCharts(noPrimary);
      assertEquals(2, chartCount(noPrimary), "with no primary, leave the asset whole");
   }

   @Test
   void neverTouchesNonChartAssemblies() {
      // An asset may legitimately carry annotations or shapes alongside its chart.
      Viewsheet vs = new Viewsheet();
      chart(vs, "vs_new", true);
      chart(vs, "vs_old", false);
      TextVSAssembly note = new TextVSAssembly(vs, "note1");
      vs.addAssembly(note);

      WizVsService.pruneSupersededCharts(vs);

      assertNotNull(vs.getAssembly("note1"), "non-chart assemblies are left alone");
      assertNull(vs.getAssembly("vs_old"));
   }

   @Test
   void toleratesAnEmptyOrNullViewsheet() {
      WizVsService.pruneSupersededCharts(null);
      Viewsheet empty = new Viewsheet();
      WizVsService.pruneSupersededCharts(empty);
      assertEquals(0, Arrays.stream(empty.getAssemblies()).filter(a -> a instanceof Assembly).count());
   }
}
