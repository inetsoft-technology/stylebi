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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class GroupingServiceTest {
   @Mock RuntimeViewsheet rvs;
   @Mock CommandDispatcher dispatcher;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock VSLayoutService vsLayoutService;

   private Viewsheet parentVS;
   private GroupingService service;

   @BeforeEach
   void setUp() {
      service = new GroupingService(coreLifecycleService, vsLayoutService);

      parentVS = new Viewsheet();
      parentVS.getVSAssemblyInfo().setName("parentVS");
   }

   /**
    * Adding a child to a bottom-tabs tab container must position the child above
    * the tab bar, using the child's actual pixel height.
    */
   @Test
   void addChildToBottomTabsPositionsAboveTabBar() throws Exception {
      when(rvs.getViewsheet()).thenReturn(parentVS);
      TabVSAssembly tab = new TabVSAssembly();
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setName("Tab1");
      tabInfo.setBottomTabs(true);
      tabInfo.setPixelOffset(new Point(0, 300));
      tabInfo.setPixelSize(new Dimension(200, 30));
      tabInfo.setAssemblies(new String[0]);
      parentVS.addAssembly(tab);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(50, 50));
      child.getVSAssemblyInfo().setPixelSize(new Dimension(200, 150));
      parentVS.addAssembly(child);

      service.groupComponents(rvs, tab, child, false, "", dispatcher);

      // child must be positioned so its bottom edge is flush with the tab bar top
      // y = tabY - childHeight = 300 - 150 = 150
      assertEquals(150, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must be positioned above tab bar (tabY - childHeight)");
   }

   /**
    * Adding a child to a top-tabs tab container must position the child below
    * the tab bar.
    */
   @Test
   void addChildToTopTabsPositionsBelowTabBar() throws Exception {
      when(rvs.getViewsheet()).thenReturn(parentVS);
      TabVSAssembly tab = new TabVSAssembly();
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setName("Tab1");
      tabInfo.setPixelOffset(new Point(0, 100));
      tabInfo.setPixelSize(new Dimension(200, 30));
      tabInfo.setAssemblies(new String[0]);
      parentVS.addAssembly(tab);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(50, 50));
      child.getVSAssemblyInfo().setPixelSize(new Dimension(200, 150));
      parentVS.addAssembly(child);

      service.groupComponents(rvs, tab, child, false, "", dispatcher);

      // child must be positioned below tab bar: y = tabY + tabHeight = 100 + 30 = 130
      assertEquals(130, child.getVSAssemblyInfo().getPixelOffset().y,
                   "child must be positioned below tab bar (tabY + tabHeight)");
   }

   /**
    * Adding an embedded viewsheet to a bottom-tabs tab container must use the
    * embedded viewsheet's actual computed size for positioning, not the (100, 20)
    * fallback.
    */
   @Test
   void addEmbeddedViewsheetToBottomTabsUsesCorrectSize() throws Exception {
      when(rvs.getViewsheet()).thenReturn(parentVS);
      TabVSAssembly tab = new TabVSAssembly();
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setName("Tab1");
      tabInfo.setBottomTabs(true);
      tabInfo.setPixelOffset(new Point(0, 400));
      tabInfo.setPixelSize(new Dimension(300, 30));
      tabInfo.setAssemblies(new String[0]);
      parentVS.addAssembly(tab);

      // Create an embedded viewsheet with a child at (0,0) size (250, 300).
      // After layout, the embedded VS's pixel size should be (250, 300).
      Viewsheet embeddedVS = new Viewsheet();
      embeddedVS.getVSAssemblyInfo().setName("Viewsheet1");

      TextVSAssembly innerChild = new TextVSAssembly();
      innerChild.getVSAssemblyInfo().setName("InnerText");
      innerChild.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      innerChild.getVSAssemblyInfo().setPixelSize(new Dimension(250, 300));
      embeddedVS.addAssembly(innerChild);

      parentVS.addAssembly(embeddedVS);
      // layout computes size from children and stores it on the info
      embeddedVS.layout();

      Dimension embeddedSize = embeddedVS.getPixelSize();
      assertEquals(300, embeddedSize.height,
                   "embedded VS pixel height must reflect child bounds");

      service.groupComponents(rvs, tab, embeddedVS, false, "", dispatcher);

      // child must be positioned using actual height (300), not fallback (20)
      // y = tabY - embeddedHeight = 400 - 300 = 100
      assertEquals(100, embeddedVS.getVSAssemblyInfo().getPixelOffset().y,
                   "embedded VS must be positioned using its actual height, not (100,20) fallback");
   }

   /**
    * Verifies that calling layout() on an embedded viewsheet after addAssembly()
    * stores the correct pixel size computed from child bounds, replacing the
    * default (100, 20) from the AssemblyInfo constructor.
    */
   @Test
   void embeddedViewsheetLayoutSetsPixelSize() {
      Viewsheet parent = new Viewsheet();
      parent.getVSAssemblyInfo().setName("parent");

      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setName("Embedded1");

      TextVSAssembly text = new TextVSAssembly();
      text.getVSAssemblyInfo().setName("Text1");
      text.getVSAssemblyInfo().setPixelOffset(new Point(10, 20));
      text.getVSAssemblyInfo().setPixelSize(new Dimension(180, 250));
      embedded.addAssembly(text);

      parent.addAssembly(embedded);

      // Before layout, the pixel size is the default (100, 20) from AssemblyInfo
      Dimension before = embedded.getVSAssemblyInfo().getPixelSize();
      assertEquals(100, before.width, "default width before layout");
      assertEquals(20, before.height, "default height before layout");

      embedded.layout();

      Dimension after = embedded.getVSAssemblyInfo().getPixelSize();
      // bounds = bottomRight - upperLeft = (10+180, 20+250) - (10, 20) = (180, 250)
      assertEquals(180, after.width, "width must match child extent after layout");
      assertEquals(250, after.height, "height must match child extent after layout");
   }

}
