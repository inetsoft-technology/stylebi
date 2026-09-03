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
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
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
         new VSDialogService(null, null),      // real service — we test its interaction with the service
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
      // lenient: only consumed when the container-position sync path runs (left/top >= 0)
      lenient().when(rvs.getID()).thenReturn("vs1");
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

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

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

   /**
    * Bug #76407: flipping to bottom-tabs must reconcile the scaled/mobile-layout
    * position too, not just the master-pixel position -- otherwise a distinct,
    * previously-computed scaled-space child position is left stale and can overlap
    * the tab bar in mobile/responsive preview, exactly like the reported symptom
    * ("radio button bottom edge extends beyond the tab boundary"), just in scaled
    * space rather than the master view.
    *
    * left/top are submitted as -1 so {@code VSDialogService.setContainerPosition}
    * (an unrelated, pre-existing position-sync path with its own scaled/master
    * coordinate-mixing quirk -- see 03-fix.md) does not run and confound the
    * assertions; this isolates exactly the effect of the new scaled-space
    * reposition call added to this method.
    */
   @Test
   void testFlipToBottomTabsReconcilesScaledSpacePosition() throws Exception {
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      // stale scaled-space layout: tab bar sits at scaled Y=50 while the child's
      // scaled bottom edge (300 + 100 = 400) is far below it -- a large overlap.
      tabInfo.setScaledPosition(new Point(10, 50));
      tabInfo.setScaledSize(new Dimension(200, 30));
      child.getVSAssemblyInfo().setScaledPosition(new Point(10, 300));
      child.getVSAssemblyInfo().setScaledSize(new Dimension(200, 100));

      TabPropertyDialogModel model = buildModel("Tab1", true, -1, -1, 200, 30);

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      // scaled tab bar reconciled to the child's scaled bottom edge (300 + 100 = 400),
      // eliminating the overlap -- was left stale at 50 before this fix.
      assertEquals(400, captured.getLayoutPosition(true).y,
                   "scaled-space tab bar must move flush with the child's scaled bottom edge");
      // child's scaled position already defined the extent -- stays flush at 300,
      // now genuinely flush against the reconciled tab bar (400 - 100 = 300).
      assertEquals(300, child.getVSAssemblyInfo().getLayoutPosition(true).y,
                   "child scaled position must remain flush with the reconciled tab bar");
   }

   /**
    * Bug #76407 round 2: the realistic Composer flow -- an automated review found
    * that {@link #testFlipToBottomTabsReconcilesScaledSpacePosition} above only
    * proves the reposition math in isolation, by submitting left/top=-1 specifically
    * to skip {@code setContainerPosition}. In the ordinary "open dialog, toggle
    * bottomTabs, click OK without touching position" flow, {@code getAssemblyPosition}
    * (which populates this dialog) prefers the tab's scaled position when one is set,
    * so left/top come back non-negative -- equal to the CURRENT (stale) scaled
    * position -- and {@code setContainerPosition} DOES run afterward.
    *
    * Before the round-2 fix, that path would collapse the just-reconciled scaled
    * position back down using the stale submitted value: {@code setContainerPosition}
    * calls {@code info.setLayoutPosition(pos)}, which unconditionally nulls
    * {@code scaledPosition} and replaces it with a plain {@code layoutPosition} set to
    * whatever {@code pos} was -- the STALE value from before this method ran, since
    * nothing had synced {@code sizePositionPaneModel} to reflect the newly-reconciled
    * scaled position. Net effect: the fix's own reconciliation was silently undone
    * within the same request.
    */
   @Test
   void testFlipToBottomTabsReconcilesScaledSpacePositionRealisticFlow() throws Exception {
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      // same stale scaled-space layout as the isolated test above.
      tabInfo.setScaledPosition(new Point(10, 50));
      tabInfo.setScaledSize(new Dimension(200, 30));
      child.getVSAssemblyInfo().setScaledPosition(new Point(10, 300));
      child.getVSAssemblyInfo().setScaledSize(new Dimension(200, 100));

      // Realistic submission: left/top = the tab's CURRENT position as
      // getAssemblyPosition() would have returned it (scaled-first) when this
      // dialog was opened -- i.e. the stale scaled Y=50, not -1 and not the
      // master pixel Y (200). The user did not touch position; only bottomTabs.
      TabPropertyDialogModel model = buildModel("Tab1", true, 10, 50, 200, 30);

      service.setTabPropertyDialogModel("vs1", "Tab1", model, "", null, commandDispatcher);

      ArgumentCaptor<TabVSAssemblyInfo> captor = ArgumentCaptor.forClass(TabVSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), anyString(), anyString(),
         anyString(), nullable(Principal.class), any(CommandDispatcher.class));

      TabVSAssemblyInfo captured = captor.getValue();
      assertTrue(captured.getBottomTabsValue(), "bottomTabs must be persisted as true");
      // The scaled position must survive setContainerPosition's own scaled/master
      // collapsing behavior: the reconciled value (400) must be what the tab actually
      // ends up at, not reverted to the stale pre-toggle value (50).
      assertEquals(400, captured.getLayoutPosition(true).y,
                   "scaled-space tab bar must remain flush after the full method call, " +
                   "including setContainerPosition -- not reverted to the stale pre-toggle value");
      assertEquals(300, child.getVSAssemblyInfo().getLayoutPosition(true).y,
                   "child scaled position must remain flush with the reconciled tab bar " +
                   "after the full method call");
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
