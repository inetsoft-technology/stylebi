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
package inetsoft.web.admin.logviewer;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.log.LogManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.context.annotation.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #78642: the responder of the log file list request must be registered with the cluster
 * on every node, not only on nodes that already served a log request. The application scans its
 * packages with {@code lazyInit = true} and sets {@code spring.main.lazy-initialization}; both
 * are reproduced here. A node is "silent" when its listener is not registered at startup.
 */
@Tag("core")
class LogMonitoringServiceRegistrationTest {
   @Test
   void listenerIsRegisteredWithoutAnyRequestUnderComponentScanLazyInit() {
      Cluster cluster = mock(Cluster.class);

      try(AnnotationConfigApplicationContext context = createContext(cluster, false)) {
         verify(cluster, times(1)).addMessageListener(any(LogMonitoringService.class));
      }
   }

   @Test
   void listenerIsRegisteredWithoutAnyRequestUnderGlobalLazyInitialization() {
      Cluster cluster = mock(Cluster.class);

      try(AnnotationConfigApplicationContext context = createContext(cluster, true)) {
         verify(cluster, times(1)).addMessageListener(any(LogMonitoringService.class));
      }
   }

   private AnnotationConfigApplicationContext createContext(Cluster cluster, boolean global) {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

      if(global) {
         context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
      }

      context.registerBean(Cluster.class, () -> cluster);
      context.registerBean(LogManager.class, () -> mock(LogManager.class));
      context.register(ScanConfiguration.class);
      context.refresh();
      return context;
   }

   @Configuration
   @ComponentScan(basePackageClasses = LogMonitoringService.class, lazyInit = true,
                  useDefaultFilters = false,
                  includeFilters = @ComponentScan.Filter(
                     type = FilterType.ASSIGNABLE_TYPE, classes = LogMonitoringService.class))
   static class ScanConfiguration {
   }
}
