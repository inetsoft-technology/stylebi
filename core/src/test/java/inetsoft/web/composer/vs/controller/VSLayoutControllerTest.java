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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.composer.model.vs.ImagePropertyDialogModel;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.dialog.ImagePreviewPaneService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class VSLayoutControllerTest {

   @BeforeEach
   void setup() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ConfigurationContext  spyContext = Mockito.spy(context);
      staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
      staticConfigurationContext.when(ConfigurationContext::getContext)
         .thenReturn(spyContext);
      ImagePreviewPaneService imagePreviewPaneService =
         new ImagePreviewPaneService(viewsheetService, objectService);
      VSLayoutControllerService vsLayoutControllerService =
         new VSLayoutControllerService(coreLifecycleService, viewsheetService,
                                    imagePreviewPaneService, objectModelService,
                                    vsLayoutService, vsObjectTreeService);
      doReturn(vsLayoutControllerService)
         .when(spyContext)
         .getSpringBean(VSLayoutControllerService.class);

      VSLayoutControllerServiceProxy vsLayoutControllerServiceProxy = new VSLayoutControllerServiceProxy();
      controller = new VSLayoutController(runtimeViewsheetRef, vsLayoutControllerServiceProxy);
   }

   @AfterEach
   void afterEach() throws Exception {
      staticConfigurationContext.close();
   }

   // Bug #16600 Make sure that when not setting a script, the default value doesnt error out.
   @Test
   void scriptIsSet() throws Exception {
      when(viewsheetService.getViewsheet(any(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);
      when(viewsheet.getUploadedImageNames()).thenReturn(new String[0]);

      PrintLayout layout = new PrintLayout();
      List<VSAssemblyLayout> layouts = new ArrayList<>();
      VSEditableAssemblyLayout assemblyLayout = new VSEditableAssemblyLayout();
      String assemblyLayoutName = "ImageLayout";
      assemblyLayout.setName(assemblyLayoutName);
      assemblyLayout.setInfo(new ImageVSAssemblyInfo());
      layouts.add(assemblyLayout);
      layout.setHeaderLayouts(layouts);

      when(layoutInfo.getPrintLayout()).thenReturn(layout);
      when(vsLayoutService.findAssemblyLayout(layout, assemblyLayoutName, 0))
         .thenReturn(Optional.of(assemblyLayout));

      ImagePropertyDialogModel result =
         controller.getImagePropertyDialogModel(0, assemblyLayoutName, "", null);

      assertFalse(result.clickableScriptPaneModel().scriptEnabled());
      assertEquals(result.clickableScriptPaneModel().scriptExpression(), "");
      assertEquals(result.clickableScriptPaneModel().onClickExpression(), "");
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock VSObjectService objectService;
   @Mock ViewsheetService viewsheetService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock LayoutInfo layoutInfo;
   @Mock CommandDispatcher commandDispatcher;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSLayoutService vsLayoutService;
   @Mock VSObjectTreeService vsObjectTreeService;
   MockedStatic<ConfigurationContext> staticConfigurationContext;

   private VSLayoutController controller;
}
