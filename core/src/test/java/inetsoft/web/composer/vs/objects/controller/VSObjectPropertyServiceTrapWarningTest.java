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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.test.*;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #80476: {@code editObjectProperty} computed the trap check ({@code tinfo}) and discarded
 * it. Under the agent's capturing dispatcher a NEW trap is now sent as a WARNING and the write
 * proceeds; under the browser's dispatcher nothing changes. The detector itself is stubbed here
 * ({@code AgentTrapCheckTest} runs the real one); this drives the real method around it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class VSObjectPropertyServiceTrapWarningTest {
   @BeforeEach
   void setup() {
      service = new VSObjectPropertyService(
         mock(CoreLifecycleService.class), mock(VSColumnHandler.class),
         mock(VSObjectTreeService.class), mock(VSAssemblyInfoHandler.class),
         mock(VSWizardTemporaryInfoService.class), mock(VSCompositionService.class),
         mock(SharedFilterService.class), mock(DataSourceRegistry.class),
         mock(QueryManagerService.class));
      viewsheet = new Viewsheet();
      gauge = new GaugeVSAssembly(viewsheet, "Gauge1");
      viewsheet.addAssembly(gauge);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.isDisposed()).thenReturn(false);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
   }

   @Test
   void aNewTrapWarnsUnderTheCapturingDispatcherAndTheWriteStillHappens() throws Exception {
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo().clone();
      info.setMaxValue("123");
      List<String> warnings;

      try(MockedConstruction<VSModelTrapContext> ignored = trapContext(true)) {
         warnings = CapturingCommandDispatcher.withCapturingDispatcher(principal(), d -> {
            service.editObjectProperty(rvs, info, "Gauge1", "Gauge1", "", null, d, true, null);
            return d.getWarnings();
         });
      }

      assertEquals(List.of("Data-model trap: Chasm Trap is on Orders, Returns. " +
                           "The write was applied; use undo to revert it."), warnings);
      assertEquals("123", ((GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo()).getMaxValue(),
                   "the write must still be applied");
   }

   @Test
   void theBrowserDispatcherIsNotToldAnything() throws Exception {
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo().clone();
      info.setMaxValue("123");
      CommandDispatcher browser = mock(CommandDispatcher.class);

      try(MockedConstruction<VSModelTrapContext> ignored = trapContext(true)) {
         service.editObjectProperty(rvs, info, "Gauge1", "Gauge1", "", null, browser, true, null);
      }

      verify(browser, never()).sendCommand(any(MessageCommand.class));
      assertEquals("123", ((GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo()).getMaxValue(),
                   "the write must still be applied");
   }

   @Test
   void noNewTrapAddsNothing() throws Exception {
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo().clone();
      info.setMaxValue("123");
      List<String> warnings;

      try(MockedConstruction<VSModelTrapContext> ignored = trapContext(false)) {
         warnings = CapturingCommandDispatcher.withCapturingDispatcher(principal(), d -> {
            service.editObjectProperty(rvs, info, "Gauge1", "Gauge1", "", null, d, true, null);
            return d.getWarnings();
         });
      }

      assertTrue(warnings.isEmpty(), warnings.toString());
      assertEquals("123", ((GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo()).getMaxValue(),
                   "the write must still be applied");
   }

   private static MockedConstruction<VSModelTrapContext> trapContext(boolean trap) {
      return Mockito.mockConstruction(VSModelTrapContext.class, (context, ctx) -> {
         AbstractModelTrapContext.TrapInfo info = mock(AbstractModelTrapContext.TrapInfo.class);
         when(info.showWarning()).thenReturn(trap);
         when(context.isCheckTrap()).thenReturn(true);
         when(context.checkTrap(any(), any())).thenReturn(info);
         when(context.getTrapCondition()).thenReturn("Chasm Trap is on Orders, Returns");
      });
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private VSObjectPropertyService service;
   private Viewsheet viewsheet;
   private GaugeVSAssembly gauge;
   private RuntimeViewsheet rvs;
}
