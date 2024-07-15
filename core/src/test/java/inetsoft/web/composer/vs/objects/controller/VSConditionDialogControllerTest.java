/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.dialog.VSConditionDialogController;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
      controller = new VSConditionDialogController(dataRefModelFactoryService,
                                                   vsAssemblyInfoHandler,
                                                   runtimeViewsheetRef,
                                                   viewsheetEngine);
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

   private VSConditionDialogController controller;
}
