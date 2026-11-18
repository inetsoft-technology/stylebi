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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.ScriptException;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80328: {@code FormatPainterController.setFormat} does not reset the thread-local
 * {@code ASSET_EXCEPTIONS}, so a failure collected by an earlier request rethrew from every
 * later, unrelated format change on the thread. A failure collected by this request's own
 * execution still surfaces.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class FormatPainterServiceStaleExceptionTest {
   @BeforeEach
   void setup() throws Exception {
      service = new FormatPainterService(coreLifecycleService, chartRegionHandler,
                                         viewsheetEngine, objectModelService, bindingService,
                                         vsLayoutService);
      viewsheet = new Viewsheet();
      viewsheet.addAssembly(new TableVSAssembly(viewsheet, "Tbl"));
      lenient().when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class)))
         .thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(viewsheetSandbox));
      principalBefore = inetsoft.util.ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      WorksheetService.ASSET_EXCEPTIONS.remove();
      inetsoft.util.ThreadContext.setContextPrincipal(principalBefore);
   }

   @Test
   void staleExceptionOfEarlierRequestDoesNotFailUnrelatedFormat() throws Exception {
      List<Exception> stale = new ArrayList<>();
      stale.add(new ScriptException("left by an earlier request"));
      WorksheetService.ASSET_EXCEPTIONS.set(stale);

      assertDoesNotThrow(() -> service.setFormat("vs1", event(), null, dispatcher, null));
   }

   @Test
   void exceptionOfThisRequestStillSurfaces() throws Exception {
      doAnswer(inv -> {
         WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException("failed now"));
         return null;
      }).when(coreLifecycleService).execute(any(), anyString(), any(), anyInt(), any());

      ScriptException ex = assertThrows(
         ScriptException.class, () -> service.setFormat("vs1", event(), null, dispatcher, null));
      assertTrue(ex.getMessage().contains("failed now"));
   }

   private FormatVSObjectEvent event() {
      FormatVSObjectEvent event = new FormatVSObjectEvent();
      event.setObjects(new String[] { "Tbl" });
      event.setCharts(new String[0]);
      event.setFormat(new VSObjectFormatInfoModel());
      event.setOrigFormat(new VSObjectFormatInfoModel());
      return event;
   }

   @Mock ViewsheetEngine viewsheetEngine;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock ChartRegionHandler chartRegionHandler;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock CommandDispatcher dispatcher;
   @Mock VSBindingService bindingService;
   @Mock VSLayoutService vsLayoutService;
   private Viewsheet viewsheet;
   private Principal principalBefore;
   private FormatPainterService service;
}
