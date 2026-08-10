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
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end regression (through the public {@link WizVsService#collectFlatBinding}, which is
 * only called for {@code ChartVSAssembly} — never crosstab) for the classifyChartRef fix: a chart
 * measure bound to the secondary Y axis must echo back {@code secondaryY: true} in the wiz API's
 * flat binding response, not silently default to false. See
 * {@link WizFieldInfoFactoryTest#chartMeasureEchoCarriesSecondaryYAndDiscreteFromChartAggregateRef}
 * for the narrower factory-level coverage of the same fix; this test additionally proves the fix
 * is wired all the way through classifyChartRef → collectChartFlatBinding → collectFlatBinding.
 *
 * <p>WizVsService's constructor has no side effects (see {@link WizVsServiceFilterCopyTest}), so
 * its three collaborators can be plain Mockito mocks that this code path never touches.
 * collectFlatBinding only reads the assembly's (real, un-mocked) VSChartInfo — no runtime
 * viewsheet, security check, or sandbox execution is involved — so the assembly itself only needs
 * a stubbed {@code getVSChartInfo()}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceClassifyChartRefSecondaryYTest {
   @Test
   void collectFlatBindingEchoesSecondaryYTrueForAChartMeasureOnTheSecondaryAxis() {
      WizVsService service = new WizVsService(
         mock(ViewsheetService.class), mock(AssetRepository.class), mock(SecurityEngine.class), null, null,
         null);

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("Sales");
      agg.setFormulaValue("Sum");
      agg.setSecondaryY(true);

      VSChartInfo info = new VSChartInfo();
      info.addYField(agg);

      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);

      CreateViewsheetResult.FlatBinding binding = service.collectFlatBinding(assembly);

      List<MeasureFieldInfo> measures = binding.getMeasures();
      assertEquals(1, measures.size());
      assertTrue(measures.get(0).isSecondaryY());
   }
}
