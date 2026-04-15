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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.web.composer.model.vs.VSLayoutObjectModel;
import inetsoft.web.viewsheet.model.VSFormatModel;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class VSLayoutServiceTest {

   @BeforeEach
   void setup() {
      service = new VSLayoutService(objectModelService);
   }

   @Test
   void bottomTabsChildrenPositionedAtVisualTop() {
      Viewsheet vs = new Viewsheet();

      // tab assembly with bottomTabs enabled
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getInfo();
      tabInfo.setBottomTabsValue(true);

      // child assembly
      TextVSAssembly child = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(child);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{"Text1"});

      when(rvs.getViewsheet()).thenReturn(vs);

      // mock the object model service to return models with known formats
      int childHeight = 80;
      VSObjectModel tabModel = mockObjectModel();
      VSObjectModel childModel = mockObjectModel();
      VSFormatModel childFmt = childModel.getObjectFormat();
      childFmt.setPositions(0, 0, 200, childHeight);

      when(objectModelService.createModel(any(TabVSAssembly.class), eq(rvs))).thenReturn(tabModel);
      // child is cloned inside createObjectModel, so match by type
      when(objectModelService.createModel(any(TextVSAssembly.class), eq(rvs)))
         .thenReturn(childModel);

      int layoutX = 100;
      int layoutY = 300;
      int layoutW = 400;
      int layoutH = 30;
      VSAssemblyLayout layout = new VSAssemblyLayout(
         "Tab1", new Point(layoutX, layoutY), new Dimension(layoutW, layoutH));

      VSLayoutObjectModel result = service.createObjectModel(rvs, layout, objectModelService);

      // top is the visual top of the tab area (no shift for bottom tabs)
      assertEquals(layoutY, result.top(),
         "model top should be the visual top (layout position)");
      assertEquals(layoutX, result.left());

      // child positioned at the visual top of the layout area
      assertEquals(layoutX, (int) childFmt.getLeft(),
         "child left should match layout position");
      assertEquals(layoutY, (int) childFmt.getTop(),
         "child top should be at the visual top of the layout area");
   }

   @Test
   void bottomTabsMultipleChildrenAllAtVisualTop() {
      Viewsheet vs = new Viewsheet();

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getInfo();
      tabInfo.setBottomTabsValue(true);

      TextVSAssembly child1 = new TextVSAssembly(vs, "Text1");
      TextVSAssembly child2 = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(child1);
      vs.addAssembly(child2);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{"Text1", "Text2"});

      when(rvs.getViewsheet()).thenReturn(vs);

      int child1Height = 60;
      int child2Height = 120;

      VSObjectModel tabModel = mockObjectModel();
      VSObjectModel childModel1 = mockObjectModel();
      VSObjectModel childModel2 = mockObjectModel();
      childModel1.getObjectFormat().setPositions(0, 0, 200, child1Height);
      childModel2.getObjectFormat().setPositions(0, 0, 200, child2Height);

      when(objectModelService.createModel(any(TabVSAssembly.class), eq(rvs))).thenReturn(tabModel);
      when(objectModelService.createModel(any(TextVSAssembly.class), eq(rvs)))
         .thenReturn(childModel1, childModel2);

      int layoutY = 300;
      VSAssemblyLayout layout = new VSAssemblyLayout(
         "Tab1", new Point(50, layoutY), new Dimension(400, 30));

      VSLayoutObjectModel result = service.createObjectModel(rvs, layout, objectModelService);

      // top is the visual top (no shift), same for all tab types
      assertEquals(layoutY, result.top(),
         "model top should be the visual top (layout position)");

      // both children positioned at the visual top of the layout area
      assertEquals(layoutY,
         (int) childModel1.getObjectFormat().getTop(),
         "child1 top should be at the visual top of the layout area");
      assertEquals(layoutY,
         (int) childModel2.getObjectFormat().getTop(),
         "child2 top should be at the visual top of the layout area");
   }

   @Test
   void scriptSetBottomTabsPositionedCorrectly() {
      Viewsheet vs = new Viewsheet();

      // tab assembly with bottomTabs set via script (rValue only, dValue=false)
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getInfo();
      tabInfo.setBottomTabs(true);

      TextVSAssembly child = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(child);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{"Text1"});

      when(rvs.getViewsheet()).thenReturn(vs);

      int childHeight = 80;
      VSObjectModel tabModel = mockObjectModel();
      VSObjectModel childModel = mockObjectModel();
      childModel.getObjectFormat().setPositions(0, 0, 200, childHeight);

      when(objectModelService.createModel(any(TabVSAssembly.class), eq(rvs))).thenReturn(tabModel);
      when(objectModelService.createModel(any(TextVSAssembly.class), eq(rvs)))
         .thenReturn(childModel);

      int layoutX = 100;
      int layoutY = 300;
      VSAssemblyLayout layout = new VSAssemblyLayout(
         "Tab1", new Point(layoutX, layoutY), new Dimension(400, 30));

      VSLayoutObjectModel result = service.createObjectModel(rvs, layout, objectModelService);

      assertEquals(layoutY, result.top());
      assertEquals(layoutX, result.left());

      // child positioned at the visual top, same as dValue-set bottom tabs
      assertEquals(layoutX, (int) childModel.getObjectFormat().getLeft());
      assertEquals(layoutY, (int) childModel.getObjectFormat().getTop());
   }

   @Test
   void scriptOverrideToNonBottomTabsPositionedCorrectly() {
      Viewsheet vs = new Viewsheet();

      // dValue=true (UI), rValue=false (script override)
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getInfo();
      tabInfo.setBottomTabsValue(true);
      tabInfo.setBottomTabs(false);

      TextVSAssembly child = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(child);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{"Text1"});

      when(rvs.getViewsheet()).thenReturn(vs);

      VSObjectModel tabModel = mockObjectModel();
      VSObjectModel childModel = mockObjectModel();
      childModel.getObjectFormat().setPositions(0, 0, 200, 80);

      when(objectModelService.createModel(any(TabVSAssembly.class), eq(rvs))).thenReturn(tabModel);
      when(objectModelService.createModel(any(TextVSAssembly.class), eq(rvs)))
         .thenReturn(childModel);

      int layoutY = 300;
      VSAssemblyLayout layout = new VSAssemblyLayout(
         "Tab1", new Point(50, layoutY), new Dimension(400, 30));

      VSLayoutObjectModel result = service.createObjectModel(rvs, layout, objectModelService);

      // rValue=false overrides dValue=true, so top tabs behavior
      assertEquals(layoutY, result.top(),
         "model top should not be shifted for script-overridden non-bottom tabs");
   }

   @Test
   void nonBottomTabsTopNotShifted() {
      Viewsheet vs = new Viewsheet();

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      // bottomTabs defaults to false

      TextVSAssembly child = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(child);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{"Text1"});

      when(rvs.getViewsheet()).thenReturn(vs);

      VSObjectModel tabModel = mockObjectModel();
      VSObjectModel childModel = mockObjectModel();
      childModel.getObjectFormat().setPositions(0, 0, 200, 80);

      when(objectModelService.createModel(any(TabVSAssembly.class), eq(rvs))).thenReturn(tabModel);
      when(objectModelService.createModel(any(TextVSAssembly.class), eq(rvs)))
         .thenReturn(childModel);

      int layoutY = 300;
      VSAssemblyLayout layout = new VSAssemblyLayout(
         "Tab1", new Point(50, layoutY), new Dimension(400, 30));

      VSLayoutObjectModel result = service.createObjectModel(rvs, layout, objectModelService);

      // top should NOT be shifted for non-bottom tabs
      assertEquals(layoutY, result.top(),
         "model top should not be shifted for non-bottom tabs");
   }

   private VSObjectModel mockObjectModel() {
      VSObjectModel model = mock(VSObjectModel.class);
      VSFormatModel fmt = new VSFormatModel();
      lenient().when(model.getObjectFormat()).thenReturn(fmt);
      return model;
   }

   @Mock RuntimeViewsheet rvs;
   @Mock VSObjectModelFactoryService objectModelService;

   private VSLayoutService service;
}
