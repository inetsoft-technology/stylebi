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
package inetsoft.uql.serverfile;

import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two ways to get a {@code Config} Spring bean into {@link ConfigurationContext}, for two
 * different levels of "how much of the server does this test actually need."
 *
 * <p>{@link #install()}/{@link #uninstall()}: a bare mocked {@link ApplicationContext} exposing
 * only {@code Config} (mirroring {@code OneDriveTabularContractTest}/core's own
 * {@code TabularQueryContractSupportTest}). Sufficient for any test that calls {@code
 * TabularSchemaExtractor.extract}/{@code applyQueryContract} but never actually reads a workbook's
 * ROWS -- {@code TabularSchemaExtractor.extract} unconditionally reaches {@code
 * LayoutCreator.createTabularView}, which resolves a view label through {@code Config.getConfig()},
 * regardless of what the query is for.
 *
 * <p>{@link #ConfigBeanConfig}: a real, standard {@code BaseTestConfiguration}/{@code
 * SwapperTestConfiguration}-backed Spring test context (see {@code ODataCatalogCacheTest} for the
 * same shape), PLUS this class's own {@code Config} bean -- for tests that read an actual
 * workbook's cell content. Excel row reading builds an {@code XSwappableTable}
 * ({@code inetsoft.uql.table.XTableFragment}'s static init and {@code XSwapper.getSwapper()}), which
 * needs the REAL wiring {@code BaseTestConfiguration} provides ({@code FileSystemService},
 * {@code PropertiesEngine} backed by a real {@code KeyValueStorageManager}, a real {@code
 * XSwapper}) -- a hand-mocked bean set (tried first, see git history) chases one
 * {@code ShutdownException}/{@code NoClassDefFoundError} at a time without ever reaching the
 * bottom of that dependency chain.
 */
final class ServerFileSpringTestSupport {
   private ServerFileSpringTestSupport() {
   }

   static void install() {
      previous = ConfigurationContext.getContext().getApplicationContext();

      Config config = mock(Config.class);
      when(config.getResourceBundle(any())).thenReturn(null);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);

      ConfigurationContext.getContext().setApplicationContext(context);
   }

   static void uninstall() {
      ConfigurationContext.getContext().setApplicationContext(previous);
   }

   private static ApplicationContext previous;

   /** Add to a test's {@code @ContextConfiguration(classes = {..., ConfigBeanConfig.class})}. */
   @Configuration
   static class ConfigBeanConfig {
      @Bean
      public Config config() {
         Config config = mock(Config.class);
         when(config.getResourceBundle(any())).thenReturn(null);
         return config;
      }
   }
}
