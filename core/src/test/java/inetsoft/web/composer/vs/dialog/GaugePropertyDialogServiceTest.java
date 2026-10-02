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
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
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

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
class GaugePropertyDialogServiceTest {
   @BeforeEach
   void setup() throws Exception {
      // the real property service; with no sandbox it returns right after merging the dialog
      // info into the live assembly (setVSAssemblyInfo -> copyInfo)
      VSObjectPropertyService propertyService = new VSObjectPropertyService(
         mock(CoreLifecycleService.class), null, null, null, null, null, null, null);
      service = new GaugePropertyDialogService(propertyService, vsOutputService, dialogService,
                                               engine, trapService, assemblyInfoHandler);

      viewsheet = new Viewsheet();
      gauge = new GaugeVSAssembly(viewsheet, "Gauge1");
      viewsheet.addAssembly(gauge);

      lenient().when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      lenient().when(rvs.getViewsheet()).thenReturn(viewsheet);
      lenient().when(dialogService.getAssemblyPosition(any(), any())).thenReturn(new Point(0, 0));
      lenient().when(dialogService.getAssemblySize(any(), any()))
         .thenReturn(new Dimension(200, 200));
   }

   /**
    * Bug #77205: deleting a range-shrinking script through the real Gauge property dialog
    * GET/SET round trip (Advanced tab untouched) must restore the design ranges on the live
    * assembly.
    */
   @Test
   void deletingRangeScriptRestoresDesignRanges() throws Exception {
      GaugeVSAssemblyInfo live = (GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo();
      live.setRangeValues(new String[] { "500", "1000", "1500", "", "" });
      live.setRangeColorsValue(new Color[] { Color.RED, Color.YELLOW, Color.GREEN });

      saveWithScript("this.ranges = [520]; this.rangeColors = [\"#0000FF\"];");
      // executeView runs the new script
      live.setRanges(new Object[] { "520" });
      live.setRangeColors(new Color[] { Color.BLUE });
      assertArrayEquals(new double[] { 520.0 }, live.getRanges(), 1e-6);

      saveWithScript("");

      assertArrayEquals(new double[] { 500.0, 1000.0, 1500.0, Double.NaN, Double.NaN },
                        live.getRanges(), 1e-6);
      assertArrayEquals(new Color[] { Color.RED, Color.YELLOW, Color.GREEN, null, null, null },
                        live.getRangeColors());
   }

   private void saveWithScript(String script) throws Exception {
      GaugePropertyDialogModel model =
         service.getGaugePropertyDialogModel("Viewsheet1", "Gauge1", null);
      model.setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel.builder()
                                            .scriptEnabled(true)
                                            .expression(script)
                                            .build());
      service.setGaugePropertyDialogModel("Viewsheet1", "Gauge1", model, "", null,
                                          commandDispatcher);
   }

   @Mock VSOutputService vsOutputService;
   @Mock VSDialogService dialogService;
   @Mock ViewsheetService engine;
   @Mock VSTrapService trapService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock RuntimeViewsheet rvs;
   @Mock CommandDispatcher commandDispatcher;
   private Viewsheet viewsheet;
   private GaugeVSAssembly gauge;
   private GaugePropertyDialogService service;
}
