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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * P5 review round 2 (PR #4904) blocker: {@link TabularCatalogService} is {@code @Service}, has
 * two constructors (a public 2-arg and a package-private 3-arg test seam), no no-arg constructor,
 * and — before this fix — no {@code @Autowired} on either. Spring's
 * {@code AutowiredAnnotationBeanPostProcessor.determineCandidateConstructors()} scans
 * {@code getDeclaredConstructors()} (the package-private one counts), finds more than one
 * candidate with none annotated, and falls back to default-constructor resolution — which does
 * not exist here. Bean creation fails at context startup, not merely at first use.
 *
 * <p><b>No test in this suite previously went through a real Spring {@code ApplicationContext}.</b>
 * {@code TabularCatalogServiceTest}, {@code MetadataApiServiceNonJdbcBranchTest}, and
 * {@code TabularCatalogDatasetExtensionTest} all construct {@link TabularCatalogService} directly
 * or mock it, and every {@code MetadataApiService} test does the same. That is precisely why this
 * defect passed a full build + verify + adversarial-review pass undetected: nothing asked Spring
 * to resolve the bean graph. This is the closest thing to a general Spring-context smoke test
 * found under {@code core/src/test/java} — {@code AdminAiBeanGraphTest} solves an adjacent problem
 * (an unresolvable constructor parameter type) with a deliberately context-free reflection scan,
 * by its own documented design choice, so it is not reused here; this test intentionally does the
 * thing that one avoids on purpose.
 *
 * <p>Registers only {@link TabularCatalogService} and {@link MetadataApiService} plus their
 * non-{@code @Service} collaborators as manually-supplied {@code @Bean} mocks — the lightest
 * context that can exercise real constructor-candidate resolution, not the whole application's
 * {@code @ComponentScan}.
 */
@Tag("core")
class MetadataApiServiceSpringWiringTest {
   @Test
   void metadataApiServiceResolvesAsARealSpringBeanWithATabularCatalogServiceDependency() {
      try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
         context.register(Collaborators.class, TabularCatalogService.class, MetadataApiService.class);
         context.refresh();

         TabularCatalogService tabularCatalogService = context.getBean(TabularCatalogService.class);
         MetadataApiService metadataApiService = context.getBean(MetadataApiService.class);

         assertNotNull(tabularCatalogService);
         assertNotNull(metadataApiService);
      }
   }

   @Configuration
   static class Collaborators {
      @Bean
      XRepository xrepository() {
         return mock(XRepository.class);
      }

      @Bean
      DataSourceService dataSourceService() {
         return mock(DataSourceService.class);
      }

      @Bean
      AssetRepository assetRepository() {
         return mock(AssetRepository.class);
      }

      @Bean
      AssetTreeService assetTreeService() {
         return mock(AssetTreeService.class);
      }

      @Bean
      ObjectMapper objectMapper() {
         return new ObjectMapper();
      }
   }
}
