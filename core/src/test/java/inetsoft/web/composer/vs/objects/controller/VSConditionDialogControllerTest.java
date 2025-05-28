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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VSConditionDialogControllerTest {

   @BeforeEach
   void setup() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ConfigurationContext  spyContext = Mockito.spy(context);
      staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
      staticConfigurationContext.when(ConfigurationContext::getContext)
         .thenReturn(spyContext);

      VSConditionDialogService dialogService = new VSConditionDialogService(dataRefModelFactoryService, vsAssemblyInfoHandler, viewsheetEngine);
      doReturn(dialogService).when(spyContext).getSpringBean(VSConditionDialogService.class);
      controller = new VSConditionDialogController(runtimeViewsheetRef,
                                                   new VSConditionDialogServiceProxy());
   }

   @AfterEach
   void afterEach() throws Exception {
      staticConfigurationContext.close();
   }

   @Test
   void outputVSAssemblyGetModelWorks() throws Exception {
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);

      TextVSAssemblyInfo infoSpy = spy(new TextVSAssemblyInfo());
      TextVSAssembly textVSAssembly = spy(new TextVSAssembly());
      when(textVSAssembly.getVSAssemblyInfo()).thenReturn(infoSpy);
      when(infoSpy.getBindingInfo()).thenReturn(bindingInfo);
      when(bindingInfo.getTableName()).thenReturn("TableName");

      when(viewsheet.getAssembly(anyString())).thenReturn(textVSAssembly);
      when(viewsheet.getBaseWorksheet()).thenReturn(null);

      controller.getModel("Viewsheet1", "TextAssembly", null);

      verify(infoSpy).getPreConditionList();
   }

   @Test
   void whenBindingInfoNull() throws Exception {
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);

      TextVSAssemblyInfo infoSpy = spy(new TextVSAssemblyInfo());
      TextVSAssembly textVSAssembly = spy(new TextVSAssembly());
      when(textVSAssembly.getVSAssemblyInfo()).thenReturn(infoSpy);
      when(infoSpy.getBindingInfo()).thenReturn(null);

      when(viewsheet.getAssembly(anyString())).thenReturn(textVSAssembly);
      when(viewsheet.getBaseWorksheet()).thenReturn(null);

      controller.getModel("Viewsheet1", "TextAssembly", null);

      verify(infoSpy).getPreConditionList();
   }

   @Mock DataRefModelFactoryService dataRefModelFactoryService;
   @Mock VSAssemblyInfoHandler vsAssemblyInfoHandler;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock ViewsheetService viewsheetEngine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock BindingInfo bindingInfo;
   MockedStatic<ConfigurationContext> staticConfigurationContext;

   private VSConditionDialogController controller;
}
