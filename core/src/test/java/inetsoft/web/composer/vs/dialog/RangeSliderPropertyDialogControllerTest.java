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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.RangeSliderPropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class RangeSliderPropertyDialogControllerTest {
   @BeforeEach
   void setup() throws Exception {
      controller = new RangeSliderPropertyDialogController(runtimeViewsheetRef,
                                                           new RangeSliderPropertyDialogServiceProxy());

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(timeSliderAssembly);
      when(timeSliderAssembly.getVSAssemblyInfo()).thenReturn(timeSliderVSAssemblyInfoSpy);
   }

   @Test
   void logValueUpdates() throws Exception {
      given(rangeSliderPropertyDialogModel.getRangeSliderAdvancedPaneModel()
               .getRangeSliderSizePaneModel().isLogScale()).willReturn(true);
      given(rangeSliderPropertyDialogModel.getRangeSliderGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("RangeSlider1");
      given(rangeSliderPropertyDialogModel.getRangeSliderDataPaneModel().getAdditionalTables())
         .willReturn(Collections.emptyList());

      controller.setRangeSliderPropertyModel("RangeSlider1",
                                             rangeSliderPropertyDialogModel,
                                             "", null, commandDispatcher);

      ArgumentCaptor<TimeSliderVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(TimeSliderVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      assertTrue(argument.getValue().getLogScaleValue());
   }

   @Spy TimeSliderVSAssemblyInfo timeSliderVSAssemblyInfoSpy = new TimeSliderVSAssemblyInfo();
   @Mock VSOutputService vsOutputService;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock TimeSliderVSAssembly timeSliderAssembly;
   @Mock ViewsheetService engine;
   @Mock VSDialogService dialogService;
   @Mock VSTrapService trapService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock
   VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock (answer = Answers.RETURNS_DEEP_STUBS)
   private RangeSliderPropertyDialogModel rangeSliderPropertyDialogModel;

   private RangeSliderPropertyDialogController controller;

}
