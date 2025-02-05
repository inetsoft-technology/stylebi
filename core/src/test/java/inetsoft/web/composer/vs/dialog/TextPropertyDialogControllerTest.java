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
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.TextPropertyDialogModel;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
public class TextPropertyDialogControllerTest {

   @BeforeEach
   public void setup() throws Exception {
      controller = new TextPropertyDialogController(vsObjectPropertyService,
                                                    vsOutputService,
                                                    runtimeViewsheetRef,
                                                    engine,
                                                    dialogService,
                                                    trapService,
                                                    infoHandler);

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(textAssembly);
      when(textAssembly.getVSAssemblyInfo()).thenReturn(textVSAssemblyInfoSpy);
   }

   @Test
   public void testSetModel() throws Exception {
      // This really isn't a very good test case, it just tests that the service method is called.
      // It doesn't validate what is sent to the service method, it just tests some basic control
      // flow.
      BDDMockito.given(textPropertyDialogModel.getTextGeneralPaneModel().getOutputGeneralPaneModel()
                          .getGeneralPropPaneModel().getBasicGeneralPaneModel().getName())
         .willReturn("Text1");

      TextPropertyDialogModel model = new TextPropertyDialogModel();
      model.getTextGeneralPaneModel().getOutputGeneralPaneModel().getGeneralPropPaneModel()
         .getBasicGeneralPaneModel().setName("Text1");

      controller.setTextPropertyDialogModel(
         "Text1", textPropertyDialogModel, "", null, commandDispatcher);

      verify(vsObjectPropertyService).editObjectProperty(any(RuntimeViewsheet.class),
                                                         any(VSAssemblyInfo.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         any(String.class),
                                                         nullable(Principal.class),
                                                         any(CommandDispatcher.class));
   }

   @Spy TextVSAssemblyInfo textVSAssemblyInfoSpy = new TextVSAssemblyInfo();
   @Mock VSOutputService vsOutputService;
   @Mock VSTrapService trapService;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock TextVSAssembly textAssembly;
   @Mock ViewsheetService engine;
   @Mock
   VSAssemblyInfoHandler infoHandler;
   @Mock VSDialogService dialogService;
   @Mock(answer = Answers.RETURNS_DEEP_STUBS)
   private TextPropertyDialogModel textPropertyDialogModel;

   private TextPropertyDialogController controller;
}
