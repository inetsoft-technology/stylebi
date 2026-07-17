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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * duplicatePrimaryAssembly is the single shared "copy" primitive: it reuses the existing
 * uniqueAssemblyName + rebindAssembly building blocks (the same ones the standard create/rebind
 * path already relies on) so copy+apply callers (setChartColors/setChartFormat) never reimplement
 * assembly duplication themselves — the exact class of divergence that broke save_viewsheet for
 * FILTER's old copy-to-"_1" behavior.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceDuplicatePrimaryAssemblyTest {
   private static WizVsService service() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return new WizVsService(vsService, engine, sec);
   }

   private static ChartVSAssembly primaryChart(Viewsheet vs, String name) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      vs.addAssembly(chart);
      chart.setPrimary(true);
      return chart;
   }

   @Test
   void returnsANewUniquelyNamedAssemblyMarkedPrimary() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly original = primaryChart(vs, "Chart1");

      VSAssembly copy = service().duplicatePrimaryAssembly(vs, original);

      assertNotNull(copy);
      assertNotEquals("Chart1", copy.getName());
      assertTrue(copy.isPrimary());
   }

   @Test
   void keepsTheOriginalAssemblyButDemotesItFromPrimary() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly original = primaryChart(vs, "Chart1");

      service().duplicatePrimaryAssembly(vs, original);

      Assembly stillThere = vs.getAssembly("Chart1");
      assertNotNull(stillThere, "the original assembly must not be removed");
      assertFalse(((VSAssembly) stillThere).isPrimary(),
         "the original assembly must be demoted once the copy becomes primary");
   }

   @Test
   void addsTheCopyToTheSameViewsheetAlongsideTheOriginal() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly original = primaryChart(vs, "Chart1");

      VSAssembly copy = service().duplicatePrimaryAssembly(vs, original);

      assertSame(copy, vs.getAssembly(copy.getName()));
      assertEquals(2, vs.getAssemblies().length, "both the original and the copy must be present");
   }

   @Test
   void returnsNullForAnAssemblyTypeWithNoRebindFactory() throws Exception {
      Viewsheet vs = new Viewsheet();
      VSAssembly unsupported = mock(VSAssembly.class);
      when(unsupported.getName()).thenReturn("Weird1");

      VSAssembly copy = service().duplicatePrimaryAssembly(vs, unsupported);

      assertNull(copy);
   }
}
