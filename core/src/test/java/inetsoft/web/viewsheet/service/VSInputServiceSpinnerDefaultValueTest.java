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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.SpinnerVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78308: the spinner property dialog must store the default value picked from the
 * data input target row, not 0.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class VSInputServiceSpinnerDefaultValueTest {
   @BeforeEach
   void setup() throws Exception {
      service = new VSInputService(
         mock(VSObjectService.class), mock(CoreLifecycleService.class), engine,
         vsObjectPropertyService, mock(VSDialogService.class), mock(VSTrapService.class),
         mock(DataRefModelFactoryService.class), mock(VSAssemblyInfoHandler.class),
         mock(VSColumnHandler.class), mock(QueryManagerService.class));

      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.setName("Spinner1");

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly("Spinner1")).thenReturn(spinnerAssembly);
      when(spinnerAssembly.getVSAssemblyInfo()).thenReturn(info);
   }

   @ParameterizedTest
   @CsvSource({ "42, 42.0", "12.5, 12.5", "-3, -3.0", "abc, 0.0", "'', 0.0" })
   void defaultValueIsStored(String defaultValue, double expected) throws Exception {
      SpinnerPropertyDialogModel model = createModel();
      model.getDataInputPaneModel().setDefaultValue(defaultValue);

      service.setSpinnerPropertyDialogModel("Viewsheet1", "Spinner1", model, "", null,
                                            commandDispatcher);

      assertEquals(expected,
                   ((Number) captureInfo().getSelectedObject()).doubleValue(), 0.0);
   }

   @Test
   void nullDefaultValueKeepsCurrentValue() throws Exception {
      SpinnerVSAssemblyInfo info =
         (SpinnerVSAssemblyInfo) spinnerAssembly.getVSAssemblyInfo();
      info.setValue(7);

      service.setSpinnerPropertyDialogModel("Viewsheet1", "Spinner1", createModel(), "", null,
                                            commandDispatcher);

      assertEquals(7, ((Number) captureInfo().getSelectedObject()).intValue());
   }

   private SpinnerPropertyDialogModel createModel() {
      SpinnerPropertyDialogModel model = new SpinnerPropertyDialogModel();
      SpinnerGeneralPaneModel general = model.getSpinnerGeneralPaneModel();
      general.getNumericRangePaneModel().setMinimum("0");
      general.getNumericRangePaneModel().setMaximum("100");
      general.getNumericRangePaneModel().setIncrement("1");
      general.getGeneralPropPaneModel().getBasicGeneralPaneModel().setName("Spinner1");
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().scriptEnabled(false).expression("").build());
      return model;
   }

   private SpinnerVSAssemblyInfo captureInfo() throws Exception {
      ArgumentCaptor<SpinnerVSAssemblyInfo> captor =
         ArgumentCaptor.forClass(SpinnerVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(), anyString(),
         nullable(Principal.class), any(CommandDispatcher.class));
      return captor.getValue();
   }

   @Mock ViewsheetService engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock SpinnerVSAssembly spinnerAssembly;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock CommandDispatcher commandDispatcher;
   private VSInputService service;
}
