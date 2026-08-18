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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.GaugePropertyDialogModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Write-coordination wiring (2026-08-17-write-coordination-implementation.md, Phase 1): confirms
 * the dialog's own revision -- read at open time, held by the browser, sent back on commit --
 * reaches {@link VSObjectPropertyService#editObjectProperty} unchanged. The refusal logic itself
 * is {@link inetsoft.web.composer.vs.objects.controller.VSObjectPropertyServiceTest}'s job; this
 * only proves Gauge threads the value through rather than dropping it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class GaugePropertyDialogServiceTest {
   @BeforeEach
   void setup() {
      service = new GaugePropertyDialogService(
         vsObjectPropertyService,
         vsOutputService,
         dialogService,
         engine,
         trapService,
         assemblyInfoHandler);
   }

   @Test
   void forwardsTheModelsRevisionToEditObjectProperty() throws Exception {
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(gaugeAssembly);
      when(gaugeAssembly.getVSAssemblyInfo()).thenReturn(new GaugeVSAssemblyInfo());

      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.setRevision(42);
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().scriptEnabled(false).expression("").build());

      service.setGaugePropertyDialogModel("Viewsheet1", "Gauge1", model, "", null,
                                          commandDispatcher);

      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), any(GaugeVSAssemblyInfo.class), eq("Gauge1"),
         nullable(String.class), any(String.class), nullable(Principal.class),
         any(CommandDispatcher.class), eq(true), eq(42));
   }

   @Test
   void forwardsANullRevisionUnchanged() throws Exception {
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(gaugeAssembly);
      when(gaugeAssembly.getVSAssemblyInfo()).thenReturn(new GaugeVSAssemblyInfo());

      // A model built by a client that doesn't yet round-trip a revision leaves it null --
      // editObjectProperty must see null, not some invented default, so it does not participate
      // in the conflict check at all (today's unconditional-commit behavior).
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().scriptEnabled(false).expression("").build());

      service.setGaugePropertyDialogModel("Viewsheet1", "Gauge1", model, "", null,
                                          commandDispatcher);

      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), any(GaugeVSAssemblyInfo.class), eq("Gauge1"),
         nullable(String.class), any(String.class), nullable(Principal.class),
         any(CommandDispatcher.class), eq(true), isNull());
   }

   @Test
   void readSideAttachesTheCurrentWriteRevision() throws Exception {
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(gaugeAssembly);
      when(gaugeAssembly.getVSAssemblyInfo()).thenReturn(new GaugeVSAssemblyInfo());
      when(rvs.getWriteRevision()).thenReturn(7);
      when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new java.awt.Point(0, 0));
      when(dialogService.getAssemblySize(any(), any()))
         .thenReturn(new java.awt.Dimension(100, 100));

      GaugePropertyDialogModel result =
         service.getGaugePropertyDialogModel("Viewsheet1", "Gauge1", null);

      org.junit.jupiter.api.Assertions.assertEquals(7, result.getRevision());
   }

   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSOutputService vsOutputService;
   @Mock VSDialogService dialogService;
   @Mock ViewsheetService engine;
   @Mock VSTrapService trapService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock GaugeVSAssembly gaugeAssembly;

   private GaugePropertyDialogService service;
}
