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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.WorksheetService;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.SheetWriteAspect;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Bug #80336: the painter is advised by SheetWriteAspect and therefore served as a Spring CGLIB
 * subclass. Every service entry point must reach the real target (fields wired), not the proxy
 * instance. Spring AOT pre-generates that subclass at build time, so a stale generated class that
 * predates a new method makes it run on the field-less proxy (NPE on viewsheetService); the unit
 * tests that build the service with {@code new} cannot see it. This drives the call through a real
 * advised bean the way the server does.
 */
@Tag("core")
class FormatPainterServiceSpringProxyTest {
   @Configuration
   @EnableAspectJAutoProxy(proxyTargetClass = true)
   static class Cfg {
      @Bean CoreLifecycleService a() { return mock(CoreLifecycleService.class); }
      @Bean ChartRegionHandler b() { return mock(ChartRegionHandler.class); }
      @Bean ViewsheetService c() { return mock(ViewsheetService.class); }
      @Bean VSObjectModelFactoryService d() { return mock(VSObjectModelFactoryService.class); }
      @Bean VSBindingService e() { return mock(VSBindingService.class); }
      @Bean VSLayoutService f() { return mock(VSLayoutService.class); }
      
      @Bean SheetWriteAspect h() { return new SheetWriteAspect(mock(WorksheetService.class)); }
      @Bean FormatPainterService svc(CoreLifecycleService a, ChartRegionHandler b, ViewsheetService c,
         VSObjectModelFactoryService d, VSBindingService e, VSLayoutService f) {
         return new FormatPainterService(a, b, c, d, e, f);
      }
   }

   @Test
   void chartRegionReadReachesTheWiredTarget() throws Exception {
      try(AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
         FormatPainterService painter = ctx.getBean(FormatPainterService.class);
         ViewsheetService viewsheets = ctx.getBean(ViewsheetService.class);

         assertThrows(NullPointerException.class,
                      () -> painter.getChartRegionFormatModel("rid", null, "Chart1", "x_title", 0,
                                                              ""));
         verify(viewsheets).getViewsheet(any(), any());
      }
   }
}
