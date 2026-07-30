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
package inetsoft.web.vswizard.handler;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSCube;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.graph.handler.ChartRegionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Guards the null-tempInfo config-sync path (WizVsService rebuild calls syncConfigs(null, ..) -> the real
 * SyncChartHandler.syncChart with tempInfo == null). SyncInfoHandlerSyncConfigsTest mocks SyncChartHandler,
 * so it never exercises this real path; without the null-guard in syncChartProperties a scripted source chart
 * NPEs on tempInfo.getOriginalModel() and syncChart's catch silently swallows it, aborting the ENTIRE
 * per-chart sync (script/condition/format/hyperlink/highlight). This test drives the real handler.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SyncChartHandlerNullTempInfoTest {
   private ChartVSAssembly newChart(Viewsheet vs, String name) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setVSChartInfo(new VSChartInfo());
      chart.setXCube(new VSCube());
      chart.setChartDescriptor(new ChartDescriptor());
      return chart;
   }

   @Test
   void syncChartWithNullTempInfoCopiesScriptFromScriptedSource() {
      SyncChartHandler handler = new SyncChartHandler(mock(ChartRegionHandler.class));
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly from = newChart(vs, "Source");
      ChartVSAssembly target = newChart(vs, "Target");

      // A non-empty script drives the previously-unguarded tempInfo.getOriginalModel() deref.
      from.getChartInfo().setScript("data['x'] = 1;");

      // tempInfo == null mirrors the rebuild config-sync path — must NOT NPE.
      handler.syncChart(null, from, target, true, true);

      // Script is set at the very line that used to NPE. If the deref had thrown, syncChart's catch would
      // have swallowed it and left the target script null (and everything after it un-synced).
      assertEquals("data['x'] = 1;", target.getChartInfo().getScript());
   }
}
