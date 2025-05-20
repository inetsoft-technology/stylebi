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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.SreeHome;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.event.NewViewsheetEvent;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.event.OpenPreviewViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class ComposerViewsheetApiControllerTest {
   @BeforeEach
   void setup() throws Exception {
      staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
      staticConfigurationContext.when(ConfigurationContext::getContext)
         .thenReturn(configurationContext);
      vsutil = Mockito.mockStatic(VSUtil.class);
      ComposerViewsheetService composerViewsheetService =
         new ComposerViewsheetService(runtimeViewsheetManager,
                                      coreLifecycleService,
                                      viewsheetService,
                                      vsObjectTreeService,
                                      refreshController,
                                      vsLayoutService,
                                      objectModelService,
                                      vsCompositionService);
      ComposerViewsheetServiceProxy composerViewsheetServiceProxy = new ComposerViewsheetServiceProxy();
      Mockito.when(configurationContext.getSpringBean(inetsoft.web.composer.vs.controller.ComposerViewsheetService.class))
         .thenReturn(composerViewsheetService);
      controller = new ComposerViewsheetController(runtimeViewsheetRef,
                                                   viewsheetService,
                                                   composerViewsheetServiceProxy);
   }

   @AfterEach
   void afterEach() throws Exception {
      staticConfigurationContext.close();
      vsutil.close();
   }

   // Bug #10686 Make sure permissions are set for preview viewsheets
   @Test
   void previewViewsheetSetPermissionsTest() throws Exception {
      when(viewsheetService.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getEntry()).thenReturn(assetEntry);
      when(rvs.getViewsheetSandbox()).thenReturn(box);
      when(box.isCancelled(any(Long.class))).thenReturn(false);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);

      controller.previewViewsheet(event, principal, dispatcher, "");
      final InOrder coreLifecycleServiceOrder = inOrder(coreLifecycleService);

      // first refresh
      coreLifecycleServiceOrder.verify(coreLifecycleService).refreshViewsheet(
         eq(rvs), any(), any(), anyInt(),
         anyInt(), anyBoolean(), any(), eq(dispatcher),
         anyBoolean(), anyBoolean(), anyBoolean(), any()
      );

      // then set permissions
      coreLifecycleServiceOrder.verify(coreLifecycleService)
         .setPermission(eq(rvs), eq(principal), eq(dispatcher));
   }

   // Bug #16746 Make sure vsobject tree is initalized when opening new vs
   @Test
   void initalizeObjectTreeOnNewVSTest() throws Exception {
      when(viewsheetService.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getEntry()).thenReturn(assetEntry);
      NewViewsheetEvent event = mock(NewViewsheetEvent.class);
      controller.newViewsheet(event, principal, dispatcher, "");
      verify(dispatcher).sendCommand(any(PopulateVSObjectTreeCommand.class));
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock RuntimeViewsheetManager runtimeViewsheetManager;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock ViewsheetService viewsheetService;
   @Mock RuntimeViewsheet rvs;
   @Mock ViewsheetSandbox box;
   @Mock Viewsheet viewsheet;
   @Mock AssetEntry assetEntry;
   @Mock LayoutInfo layoutInfo;
   @Mock OpenPreviewViewsheetEvent event;
   @Mock XPrincipal principal;
   @Mock CommandDispatcher dispatcher;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock VSRefreshController refreshController;
   @Mock VSLayoutService vsLayoutService;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSCompositionService vsCompositionService;
   @Mock ApplicationContext applicationContext;
   @Mock ConfigurationContext configurationContext;
   MockedStatic<ConfigurationContext> staticConfigurationContext;
   MockedStatic<VSUtil> vsutil;

   private ComposerViewsheetController controller;
}
