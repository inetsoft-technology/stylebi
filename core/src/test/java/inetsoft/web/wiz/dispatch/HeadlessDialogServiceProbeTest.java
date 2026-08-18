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
package inetsoft.web.wiz.dispatch;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Phase 0 empirical probe, step 1: can the real composer service graph be instantiated in a
 * test context at all?
 *
 * <p><b>Answer so far: not with this harness.</b> {@code WebConfig} scans {@code inetsoft.web}
 * with {@code lazyInit = true}, so requesting one bean should build only its transitive
 * closure. But component-scanning {@code inetsoft.web} also drags in the real
 * {@code @Configuration} classes that {@link BaseTestConfiguration} and
 * {@link IntegrationTestConfiguration} exist to replace, and they override the test doubles:
 *
 * <ol>
 *   <li>Scanning {@code inetsoft.web} pulls in {@code inetsoft.web.factory.EngineConfiguration},
 *       whose {@code cluster} / {@code viewsheetEngine} beans displace the test ones.</li>
 *   <li>Excluding {@code inetsoft.web.factory} moves the failure to
 *       {@code inetsoft.storage.StorageConfiguration}, imported transitively, whose
 *       {@code keyValueEngine} displaces {@code BaseTestConfiguration}'s
 *       {@code TestKeyValueEngine} and then cannot resolve {@code InetsoftConfig}.</li>
 * </ol>
 *
 * <p>Each exclusion reveals the next conflict. The harness is built for tests that mock their
 * collaborators, not for booting the real graph, so this is a harness-design mismatch rather
 * than a missing bean.
 *
 * <p><b>Recommendation:</b> do not build a bespoke context for this. The plugin controller
 * specified in the viewsheet-editing design runs inside the real Spring context, so this probe
 * should become that plugin's first integration test during Phase 1, where the wiring exists
 * for free. What it must still verify:
 *
 * <ul>
 *   <li>a real {@code GaugePropertyDialogService} write mutates {@code VSAssemblyInfo};</li>
 *   <li>a rename does not break the browser's assembly tracking;</li>
 *   <li>{@code @ClusterProxy} routing works bean-to-bean;</li>
 *   <li>broadcast + {@code addCheckpoint} leave the human's undo coherent.</li>
 * </ul>
 *
 * <p>The dispatcher question itself is already settled: see
 * {@link CapturingCommandDispatcherTest} and the Phase 0 spike result in the design doc.
 */
@Disabled("Phase 0 probe: harness cannot boot the real composer graph — see class javadoc. " +
          "Reinstate as the plugin's first integration test in Phase 1.")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = {
      BaseTestConfiguration.class,
      IntegrationTestConfiguration.class,
      HeadlessDialogServiceProbeTest.ComposerScan.class
   },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
@Tag("integration")
class HeadlessDialogServiceProbeTest {
   /**
    * Scans the composer/binding graph but excludes {@code inetsoft.web.factory}, whose
    * {@code EngineConfiguration} defines the very beans (cluster, viewsheetEngine) that
    * BaseTestConfiguration and IntegrationTestConfiguration exist to replace.
    */
   @Configuration
   @ComponentScan(
      basePackages = "inetsoft.web",
      lazyInit = true,
      excludeFilters = @ComponentScan.Filter(
         type = FilterType.REGEX,
         pattern = "inetsoft\\.web\\.factory\\..*")
   )
   static class ComposerScan {
   }

   @Test
   void realComposerBeansCanBeInstantiated() {
      assertNotNull(vsObjectPropertyService, "VSObjectPropertyService should resolve");
      assertNotNull(assemblyInfoHandler, "VSAssemblyInfoHandler should resolve");
   }

   @Autowired
   private VSObjectPropertyService vsObjectPropertyService;

   @Autowired
   private VSAssemblyInfoHandler assemblyInfoHandler;
}
