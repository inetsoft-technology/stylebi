/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.embed.EmbedAssemblyInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the bug where an embedded crosstab (or table/gauge/text/image)
 * rendered blank in the Wiz chat viewer while an embedded chart rendered correctly.
 *
 * Root cause: {@code applyEmbedChartSize} only pushed the embed container's size onto the
 * assembly's pixel size when the assembly was a {@link ChartVSAssemblyInfo}. A
 * programmatically-created crosstab (no composer-authored layout) therefore kept its own
 * default pixel size ({@link CrosstabVSAssemblyInfo}'s constructor default of 400x240) instead
 * of the embed container's actual size, rendering far smaller than the space it was given.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CoreLifecycleServiceEmbedSizeTest {
   private CoreLifecycleService createService() {
      return new CoreLifecycleService(
         mock(VSObjectModelFactoryService.class), mock(ViewsheetService.class),
         mock(VSLayoutService.class), mock(ParameterService.class),
         mock(VSCompositionService.class), mock(DataRefModelFactoryService.class),
         null, null, mock(LicenseManager.class), mock(SecurityEngine.class),
         mock(Cluster.class), mock(DataSourceRegistry.class));
   }

   private void invokeApplyEmbedChartSize(CoreLifecycleService service, RuntimeViewsheet rvs,
                                           VSAssembly assembly) throws Exception
   {
      Method method = CoreLifecycleService.class
         .getDeclaredMethod("applyEmbedChartSize", RuntimeViewsheet.class, VSAssembly.class);
      method.setAccessible(true);
      method.invoke(service, rvs, assembly);
   }

   @Test
   void appliesContainerSizeToEmbeddedCrosstab() throws Exception {
      Dimension containerSize = new Dimension(908, 600);
      EmbedAssemblyInfo embedAssemblyInfo = new EmbedAssemblyInfo();
      embedAssemblyInfo.setAssemblyName("vs_crosstab_1");
      embedAssemblyInfo.setAssemblySize(containerSize);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getEmbedAssemblyInfo()).thenReturn(embedAssemblyInfo);

      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
      VSAssembly assembly = mock(VSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("vs_crosstab_1");
      when(assembly.getInfo()).thenReturn((AssemblyInfo) info);

      invokeApplyEmbedChartSize(createService(), rvs, assembly);

      assertEquals(containerSize, info.getPixelSize(),
         "the embed container size must drive the crosstab's pixel size, or it renders at " +
         "the AssetUtil default (100x20)");
      assertEquals(containerSize, info.getMaxSize(),
         "BaseTableModel overrides objectFormat's position/size from getMaxSize() when set, " +
         "mirroring how chart uses max-mode size — pixel size alone is not enough");
   }

   @Test
   void chartsStillGetMaxModeSizeInAdditionToPixelSize() throws Exception {
      Dimension containerSize = new Dimension(908, 600);
      EmbedAssemblyInfo embedAssemblyInfo = new EmbedAssemblyInfo();
      embedAssemblyInfo.setAssemblyName("vs_chart_1");
      embedAssemblyInfo.setAssemblySize(containerSize);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getEmbedAssemblyInfo()).thenReturn(embedAssemblyInfo);

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      VSAssembly assembly = mock(VSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("vs_chart_1");
      when(assembly.getInfo()).thenReturn((AssemblyInfo) info);

      invokeApplyEmbedChartSize(createService(), rvs, assembly);

      assertEquals(containerSize, info.getPixelSize());
      assertEquals(containerSize, info.getMaxSize());
   }

   @Test
   void doesNotTouchAssemblyOutsideTheEmbedTarget() throws Exception {
      EmbedAssemblyInfo embedAssemblyInfo = new EmbedAssemblyInfo();
      embedAssemblyInfo.setAssemblyName("vs_crosstab_1");
      embedAssemblyInfo.setAssemblySize(new Dimension(908, 600));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getEmbedAssemblyInfo()).thenReturn(embedAssemblyInfo);

      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
      Dimension originalSize = info.getPixelSize();
      VSAssembly assembly = mock(VSAssembly.class);
      when(assembly.getAbsoluteName()).thenReturn("some_other_assembly");

      invokeApplyEmbedChartSize(createService(), rvs, assembly);

      assertEquals(originalSize, info.getPixelSize());
   }
}
