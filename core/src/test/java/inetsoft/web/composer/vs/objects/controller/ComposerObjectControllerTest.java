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
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.event.LockVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class ComposerObjectControllerTest {
   @BeforeEach
   void setup() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ConfigurationContext  spyContext = Mockito.spy(context);
      staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
      staticConfigurationContext.when(ConfigurationContext::getContext)
         .thenReturn(spyContext);
      ComposerObjectService composerObjectService = new ComposerObjectService(vsObjectTreeService, coreLifecycleService,
                                                                              engine, assemblyHandler, objectModelService,
                                                                              vsObjectService, vsCompositionService);
      doReturn(composerObjectService).when(spyContext).getSpringBean(ComposerObjectService.class);
      controller = new ComposerObjectController(runtimeViewsheetRef, new ComposerObjectServiceProxy());
   }

   @AfterEach
   void afterEach() throws Exception {
      staticConfigurationContext.close();
   }

   @Test
   void imageLockTest() throws Exception {
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      when(event.getName()).thenReturn("Assembly1");

      controller.changeLockState(event, principal, dispatcher);
      verify(vsObjectTreeService, times(1)).getObjectTree(rvs);
   }

   /**
    * Resizing a child from the top edge (y changes but bottom stays fixed) must not displace
    * the tab bar in bottom-tabs mode. move() translates the tab bar by the child's top-Y
    * delta; the correction block must undo that shift so the bar stays flush with the
    * child's unchanged bottom edge.
    */
   @Test
   void resizeChildTopEdgeInBottomTabsKeepsTabBarAtChildBottom() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");

      TabVSAssembly tab = new TabVSAssembly();
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setName("Tab1");
      tabInfo.setBottomTabs(true);
      tabInfo.setPixelOffset(new Point(0, 100)); // tab bar initially at y=100
      tabInfo.setPixelSize(new Dimension(200, 30));
      vs.addAssembly(tab);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 50)); // bottom = 50+50 = 100
      child.getVSAssemblyInfo().setPixelSize(new Dimension(200, 50));
      vs.addAssembly(child);
      tabInfo.setAssemblies(new String[]{"Text1"});

      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);

      // Drag top edge up: y 50→30, height 50→70; bottom edge stays at 100
      ResizeVSObjectEvent resizeEvent = new ResizeVSObjectEvent();
      resizeEvent.setName("Text1");
      resizeEvent.setxOffset(0);
      resizeEvent.setyOffset(30);
      resizeEvent.setWidth(200);
      resizeEvent.setHeight(70);

      controller.resizeObject(resizeEvent, principal, dispatcher, "/test");

      // Tab bar must still be at y=100 (child bottom edge did not change)
      assertEquals(100, tabInfo.getPixelOffset().y);
      // Child must be at y=30 with its new height — move() placed it correctly
      assertEquals(30, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock
   CoreLifecycleService coreLifecycleService;
   @Mock ViewsheetService engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock VSAssembly assembly;
   @Mock LockVSObjectEvent event;
   @Mock Principal principal;
   @Mock CommandDispatcher dispatcher;
   MockedStatic<ConfigurationContext> staticConfigurationContext;
   @Mock VSAssemblyInfoHandler assemblyHandler;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSObjectService vsObjectService;
   @Mock VSCompositionService vsCompositionService;

   private ComposerObjectController controller;
}
