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
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class TabPropertyDialogServiceTest {
   // Shared viewsheet and assemblies initialised once per test in setUp.
   private Viewsheet realVS;
   private TabVSAssembly tab;
   private TextVSAssembly child;

   @BeforeEach
   void setUp() throws Exception {
      service = new TabPropertyDialogService(
         vsObjectPropertyService,
         vsObjectTreeService,
         coreLifecycleService,
         new VSDialogService(),      // real service — we test its interaction with the service
         viewsheetService);

      // Build a minimal viewsheet with one Tab containing one Text child.
      realVS = new Viewsheet();
      realVS.getVSAssemblyInfo().setName("vs1");

      tab = new TabVSAssembly();
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setName("Tab1");
      tabInfo.setPixelOffset(new Point(10, 200));
      tabInfo.setPixelSize(new Dimension(200, 30));
      tabInfo.setAssemblies(new String[]{"Text1"});

      child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(10, 230)); // flush below tab in top-tabs mode
      child.getVSAssemblyInfo().setPixelSize(new Dimension(200, 200));

      realVS.addAssembly(tab);
      realVS.addAssembly(child);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(realVS);
      when(rvs.getID()).thenReturn("vs1");
      when(vsObjectTreeService.getObjectTree(any(RuntimeViewsheet.class)))
         .thenReturn(new VSObjectTreeNode());
   }

   /**
    * Flip from top-tabs to bottom-tabs while keeping the submitted position identical
    * to the current position.  After the call:
    * - the assembly info passed to editObjectProperty must report bottomTabs=true
    * - the tab bar must move below the child's bottom edge
    * - the child must stay in place
    */
   @Test
   void testFlipToBottomTabsKeepsSamePosition() throws Exception {
      TabPropertyDialogModel model = buildModel("Tab1", true, 10, 200, 200, 30);

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      // tab moves below child bottom edge: 230 + 200 = 430
      assertEquals(430, captured.getPixelOffset().y,
                   "tab bar Y must move below child bottom edge");

      // child stays in place; bottom edge (230 + 200 = 430) flush with tab top
      assertEquals(230, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must stay in place");
   }

   /**
    * Flip from top-tabs to bottom-tabs while simultaneously moving the tab bar to a
    * new Y.  The user's submitted position is honored; setContainerPosition translates
    * the whole group so the flush layout from repositionForBottomTabs is preserved.
    */
   @Test
   void testFlipToBottomTabsWithNewPosition() throws Exception {
      // User moves the tab to Y=300 and also switches to bottom-tabs.
      TabPropertyDialogModel model = buildModel("Tab1", true, 10, 300, 200, 30);

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      // user submitted Y=300; child (h=200) flush above: 300 - 200 = 100
      assertEquals(300, captured.getPixelOffset().y,
                   "tab bar Y must honor the user-submitted position");

      assertEquals(100, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child bottom edge (300) must be flush with tab top");
   }

   /**
    * Flip from top-tabs to bottom-tabs while simultaneously growing the tab bar height.
    * The user's submitted position is honored; the height-change correction in
    * {@code setContainerPosition} must NOT be applied in bottom-tabs mode.
    */
   @Test
   void testFlipToBottomTabsWithHeightChange() throws Exception {
      // User moves tab to Y=300, switches to bottom-tabs, and grows height from 30 to 50.
      TabPropertyDialogModel model = buildModel("Tab1", true, 10, 300, 200, 50);

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      // user submitted Y=300; child (h=200) flush above: 300 - 200 = 100
      assertEquals(300, captured.getPixelOffset().y,
                   "tab bar Y must honor the user-submitted position");

      // height correction not applied in bottom-tabs mode; child stays flush
      assertEquals(100, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child bottom edge (300) must be flush with tab top");
   }

   /**
    * Flip from bottom-tabs to top-tabs while simultaneously growing the tab bar height.
    * The reposition moves the tab above the child; the height-change correction in
    * {@code setContainerPosition} MUST be applied in top-tabs mode.
    */
   @Test
   void testFlipToTopTabsWithHeightChange() throws Exception {
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(10, 400));
      child.getVSAssemblyInfo().setPixelOffset(new Point(10, 200));

      // User keeps tab at Y=400 and grows height from 30 to 50.
      TabPropertyDialogModel model = buildModel("Tab1", false, 10, 400, 200, 50);

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertFalse(captured.getBottomTabsValue(), "bottomTabs must be persisted as false");
      // reposition moves tab above child top edge: max(0, 200 - 30) = 170
      assertEquals(170, captured.getPixelOffset().y,
                   "tab bar Y must move above child top edge");

      // height correction (+20) applied to child: 200 + 20 = 220
      assertEquals(220, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must shift down by height-change correction");
   }

   /**
    * Flip from bottom-tabs back to top-tabs while keeping the submitted position
    * identical to the current position.  After the call:
    * - the assembly info must report bottomTabs=false
    * - the tab bar must move above the child's top edge
    * - the child must stay in place
    */
   @Test
   void testFlipToTopTabsKeepsSamePosition() throws Exception {
      // Set up the viewsheet in bottom-tabs layout: tab lower, child above it.
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(10, 400));
      child.getVSAssemblyInfo().setPixelOffset(new Point(10, 200)); // flush above tab in bottom-tabs

      TabPropertyDialogModel model = buildModel("Tab1", false, 10, 400, 200, 30);

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertFalse(captured.getBottomTabsValue(), "bottomTabs must be persisted as false");
      // reposition moves tab above child top edge: max(0, 200 - 30) = 170
      assertEquals(170, captured.getPixelOffset().y,
                   "tab bar Y must move above child top edge");

      // child stays in place; top edge (200) flush with tab bottom (170 + 30 = 200)
      assertEquals(200, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must stay in place");
   }

   /**
    * Flip from bottom-tabs to top-tabs while simultaneously moving the tab bar to a
    * new Y.  The user's submitted position is honored; setContainerPosition translates
    * the whole group so the flush layout from repositionForBottomTabs is preserved.
    */
   @Test
   void testFlipToTopTabsWithNewPosition() throws Exception {
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(10, 400));
      child.getVSAssemblyInfo().setPixelOffset(new Point(10, 200));

      // User moves the tab to Y=500 and switches to top-tabs.
      TabPropertyDialogModel model = buildModel("Tab1", false, 10, 500, 200, 30);

      controller.setTabPropertyDialogModel("Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertFalse(captured.getBottomTabsValue(), "bottomTabs must be persisted as false");
      // user submitted Y=500; child flush below: 500 + 30 = 530
      assertEquals(500, captured.getPixelOffset().y,
                   "tab bar Y must honor the user-submitted position");

      assertEquals(530, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child top edge (530) must be flush with tab bottom");
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   /**
    * Builds a minimal {@link TabPropertyDialogModel} with the given properties.
    *
    * @param name        assembly name
    * @param newMode     the bottomTabs value the user is submitting
    * @param left        desired X position
    * @param top         desired Y position
    * @param width       desired width
    * @param height      desired height (tab bar height)
    */
   private static TabPropertyDialogModel buildModel(String name, boolean newMode,
                                                    int left, int top, int width, int height)
   {
      TabPropertyDialogModel dialogModel = new TabPropertyDialogModel();

      TabGeneralPaneModel general = dialogModel.getTabGeneralPaneModel();
      general.getGeneralPropPaneModel().getBasicGeneralPaneModel().setName(name);
      general.getTabListPaneModel().setAssemblies(new String[]{"Text1"});
      general.getTabListPaneModel().setLabels(new String[]{"Text1"});
      general.setBottomTabs(newMode);

      SizePositionPaneModel sizePos = general.getSizePositionPaneModel();
      sizePos.setLeft(left);
      sizePos.setTop(top);
      sizePos.setWidth(width);
      sizePos.setHeight(height);

      dialogModel.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().scriptEnabled(false).expression("").build());

      return dialogModel;
   }

   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock CoreLifecycleService coreLifecycleService;

   private TabPropertyDialogService service;
}
