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
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class SelectionTreePropertyDialogControllerTest {
   @BeforeEach
   void setup(){
      controller = new SelectionTreePropertyDialogController(
         vsObjectPropertyService,
         vsOutputService,
         runtimeViewsheetRef,
         engine,
         trapService,
         dialogService,
         vsSelectionService,
         selectionDialogService,
         assemblyInfoHandler,
         dataRefService);
   }

   @Test
   void isLabelValueSet() throws Exception{
      given(selectionTreePropertyDialogModel.getSelectionTreePaneModel().getLabel())
         .willReturn("mockLabel");
      given(selectionTreePropertyDialogModel.getSelectionGeneralPaneModel()
               .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("SelectionTree1");
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(selectionTreeAssembly);
      when(selectionTreeAssembly.getVSAssemblyInfo()).thenReturn(new SelectionTreeVSAssemblyInfo());
      when(viewsheet.getViewsheet().getPixelSize()).thenReturn(size);
      when(selectionTreePropertyDialogModel.getSelectionTreePaneModel().getMode()).thenReturn(2);

      controller.setSelectionTreePropertyModel("SelectionTree1",
                                             selectionTreePropertyDialogModel,
                                             "", null, commandDispatcher);

      ArgumentCaptor<SelectionTreeVSAssemblyInfo> argument =
         ArgumentCaptor.forClass(SelectionTreeVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         argument.capture(),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));

      assertNotNull(argument.getValue().getLabelValue());

   }

   @Mock VSOutputService vsOutputService;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetService engine;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSTrapService trapService;
   @Mock SelectionTreeVSAssembly selectionTreeAssembly;
   @Mock Dimension size;
   @Mock VSDialogService dialogService;
   @Mock VSSelectionService vsSelectionService;
   @Mock SelectionDialogService selectionDialogService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock DataRefModelFactoryService dataRefService;
   @Mock (answer = Answers.RETURNS_DEEP_STUBS)
   private Viewsheet viewsheet;
   @Mock (answer = Answers.RETURNS_DEEP_STUBS)
   private SelectionTreePropertyDialogModel selectionTreePropertyDialogModel;

   private SelectionTreePropertyDialogController controller;
}
