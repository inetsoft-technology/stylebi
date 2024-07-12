/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.ImagePropertyDialogModel;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImagePropertyDialogControllerTest {

   @BeforeEach
   void setup() throws Exception {
      trapService = new VSTrapService();
      placeholderService = new PlaceholderService(objectModelService, messagingTemplate,
                                                  viewsheetEngine);
      temporaryInfoService = new VSWizardTemporaryInfoService(viewsheetService);
      vsObjectPropertyService = spy(new VSObjectPropertyService(placeholderService,
                                                                vsInputService,
                                                                vsObjectTreeService,
                                                                infoHandler, viewsheetEngine,
                                                                temporaryInfoService));
      controller = new ImagePropertyDialogController(vsObjectPropertyService,
                                                     vsOutputService, runtimeViewsheetRef,
                                                     viewsheetEngine,
                                                     dialogService, 
                                                     trapService);
   }

   @Test
   void dynamicValueNotNull() throws Exception {
      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("Viewsheet1");
      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(viewsheetSandbox);
      when(viewsheetSandbox.getScope()).thenReturn(viewsheetScope);
      when(viewsheetScope.getVSAScriptable(anyString())).thenReturn(null);
      when(viewsheetScope.getVariableScriptable()).thenReturn(variableScriptable);
      when(variableScriptable.unwrap()).thenReturn(new VariableTable());
      when(variableScriptable.getIds()).thenReturn(null);
      when(viewsheet.getUploadedImageNames()).thenReturn(new String[0]);

      ImageVSAssembly assembly = spy(new ImageVSAssembly(viewsheet, "Image1"));
      ImageVSAssemblyInfo info = spy(new ImageVSAssemblyInfo());
      when(info.getImageValue()).thenReturn("imageValue");
      when(assembly.getVSAssemblyInfo()).thenReturn(info);

      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      when(viewsheet.getAssemblies(anyBoolean())).thenReturn(new Assembly[0]);
      when(viewsheet.getAssemblies()).thenReturn(new Assembly[0]);

      doReturn(new Point(0, 0)).when(dialogService)
         .getAssemblyPosition(Mockito.any(VSAssemblyInfo.class), Mockito.any(Viewsheet.class));
      doReturn(new Dimension(10, 10)).when(dialogService)
         .getAssemblySize(Mockito.any(VSAssemblyInfo.class), Mockito.any(Viewsheet.class));

      ImagePropertyDialogModel model =
         controller.getImagePropertyDialogModel("Image1", "Viewsheet1", null);

      String dynamicImageValue =
         model.imageAdvancedPaneModel().dynamicImagePaneModel().dynamicImageValue();

      assertNotEquals(dynamicImageValue, "N/A");
   }

   @Mock ViewsheetEngine viewsheetEngine;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock VSOutputService vsOutputService;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock ViewsheetScope viewsheetScope;
   @Mock VariableScriptable variableScriptable;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSDialogService dialogService;
   @Mock VSAssemblyInfoHandler infoHandler;
   @Mock SimpMessagingTemplate messagingTemplate;
   @Mock VSWizardTemporaryInfoService temporaryInfoService;
   @Mock ViewsheetService viewsheetService;

   private PlaceholderService placeholderService;
   @Mock VSInputService vsInputService;
   private VSObjectPropertyService vsObjectPropertyService;
   private ImagePropertyDialogController controller;
   private VSTrapService trapService;
}
