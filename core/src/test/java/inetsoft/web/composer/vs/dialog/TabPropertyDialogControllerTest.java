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
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
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
class TabPropertyDialogControllerTest {
   // Shared viewsheet and assemblies initialised once per test in setUp.
   private Viewsheet realVS;
   private TabVSAssembly tab;
   private TextVSAssembly child;

   @BeforeEach
   void setUp() throws Exception {
      controller = new TabPropertyDialogController(
         vsObjectPropertyService,
         runtimeViewsheetRef,
         vsObjectTreeService,
         coreLifecycleService,
         new VSDialogService(),      // real service — we test its interaction with the controller
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

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("vs1");
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
    * - the child must be repositioned flush above the tab bar (childY = tabY - childHeight = 0)
    * - the tab bar must remain at its original Y
    */
   @Test
   void testFlipToBottomTabsKeepsSamePosition() throws Exception {
      TabPropertyDialogModel model = buildModel("Tab1", true, 10, 200, 200, 30);

      controller.setTabPropertyDialogModel("Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      assertEquals(200, captured.getPixelOffset().y,
                   "tab bar Y must remain at the originally submitted position");

      // Child (height 200) must be flush above the tab bar at Y=200: 200 - 200 = 0
      assertEquals(0, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must be repositioned flush above the tab bar");
   }

   /**
    * Flip from top-tabs to bottom-tabs while simultaneously moving the tab bar to a
    * new Y.  The child must end up flush above the NEW tab bar position.
    */
   @Test
   void testFlipToBottomTabsWithNewPosition() throws Exception {
      // User moves the tab to Y=300 and also switches to bottom-tabs.
      TabPropertyDialogModel model = buildModel("Tab1", true, 10, 300, 200, 30);

      controller.setTabPropertyDialogModel("Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      assertEquals(300, captured.getPixelOffset().y,
                   "tab bar Y must be at the user-submitted position");

      // Child (height 200) must be flush above tab at Y=300: 300 - 200 = 100
      assertEquals(100, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must be flush above the new tab bar position");
   }

   /**
    * Flip from top-tabs to bottom-tabs while simultaneously growing the tab bar height.
    * The height-change correction in {@code setContainerPosition} must NOT be applied in
    * bottom-tabs mode.  After the call:
    * - the assembly info must report bottomTabs=true
    * - the tab bar must be at the user-submitted Y
    * - the child must be flush above the tab bar (childY = tabY - childHeight)
    *
    * <p>This test guards the interaction between the DynamicValue dvalue fallback in
    * {@link inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo#isBottomTabs()} and the
    * height-change correction in
    * {@link inetsoft.web.viewsheet.service.VSDialogService#setContainerPosition}.
    * After {@code setBottomTabsValue(true)}, {@code isBottomTabs()} must return {@code true}
    * (via the {@code DynamicValue.getRValue()} dvalue fallback), so the correction is skipped.
    */
   @Test
   void testFlipToBottomTabsWithHeightChange() throws Exception {
      // User moves tab to Y=300, switches to bottom-tabs, and grows height from 30 to 50.
      TabPropertyDialogModel model = buildModel("Tab1", true, 10, 300, 200, 50);

      controller.setTabPropertyDialogModel("Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      assertEquals(300, captured.getPixelOffset().y,
                   "tab bar Y must be at the user-submitted position");

      // Child (height 200) must be flush above tab at Y=300: 300 - 200 = 100.
      // If the height-change correction (+20) were wrongly applied the child would land at 120.
      assertEquals(100, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must be flush above the tab bar (correction must NOT be applied in bottom-tabs mode)");
   }

   /**
    * Flip from bottom-tabs to top-tabs while simultaneously growing the tab bar height.
    * The height-change correction in {@code setContainerPosition} MUST be applied in
    * top-tabs mode so children remain flush below the new (taller) tab bar bottom.
    */
   @Test
   void testFlipToTopTabsWithHeightChange() throws Exception {
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(10, 400));
      child.getVSAssemblyInfo().setPixelOffset(new Point(10, 200));

      // User keeps tab at Y=400 and grows height from 30 to 50.
      TabPropertyDialogModel model = buildModel("Tab1", false, 10, 400, 200, 50);

      controller.setTabPropertyDialogModel("Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertFalse(captured.getBottomTabsValue(), "bottomTabs must be persisted as false");
      assertEquals(400, captured.getPixelOffset().y,
                   "tab bar Y must remain at the originally submitted position");

      // In top-tabs mode with height=50, children must be at tabY + tabHeight = 400 + 50 = 450.
      // If the height-change correction (+20) were NOT applied the child would be at 430.
      assertEquals(450, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must be flush below the new (taller) tab bar bottom");
   }

   /**
    * Flip from bottom-tabs back to top-tabs while keeping the submitted position
    * identical to the current position.  After the call:
    * - the assembly info must report bottomTabs=false
    * - the child must be repositioned flush below the tab bar (childY = tabY + tabHeight)
    */
   @Test
   void testFlipToTopTabsKeepsSamePosition() throws Exception {
      // Set up the viewsheet in bottom-tabs layout: tab lower, child above it.
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setPixelOffset(new Point(10, 400));
      child.getVSAssemblyInfo().setPixelOffset(new Point(10, 200)); // flush above tab in bottom-tabs

      TabPropertyDialogModel model = buildModel("Tab1", false, 10, 400, 200, 30);

      controller.setTabPropertyDialogModel("Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertFalse(captured.getBottomTabsValue(), "bottomTabs must be persisted as false");
      assertEquals(400, captured.getPixelOffset().y,
                   "tab bar Y must remain at the originally submitted position");

      // Child (height 200) must be flush below the tab bar at Y=400: 400 + 30 = 430
      assertEquals(430, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must be repositioned flush below the tab bar");
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

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CommandDispatcher commandDispatcher;
   @Mock RuntimeViewsheet rvs;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock CoreLifecycleService coreLifecycleService;

   private TabPropertyDialogController controller;
}
