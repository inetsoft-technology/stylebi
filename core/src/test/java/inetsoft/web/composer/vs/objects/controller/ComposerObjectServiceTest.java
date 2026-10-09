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
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.event.CopyVSObjectsEvent;
import inetsoft.web.composer.vs.objects.event.LockVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
class ComposerObjectServiceTest {
   @BeforeEach
   void setup() throws Exception {
      service = new ComposerObjectService(vsObjectTreeService, coreLifecycleService,
                                          engine, assemblyHandler, objectModelService,
                                          vsObjectService, vsCompositionService);
   }

   @Test
   void imageLockTest() throws Exception {
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(assembly);
      when(event.getName()).thenReturn("Assembly1");

      service.changeLockState(runtimeViewsheetRef.getRuntimeId(), event, principal, dispatcher);
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
      tabInfo.setBottomTabsValue(true);
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

      service.resizeObject(runtimeViewsheetRef.getRuntimeId(), resizeEvent, principal, dispatcher, "/test");

      // Tab bar must still be at y=100 (child bottom edge did not change)
      assertEquals(100, tabInfo.getPixelOffset().y);
      // Child must be at y=30 with its new height — move() placed it correctly
      assertEquals(30, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   /**
    * Bug #77865: ctrl-drag copy of an image whose uploaded image is missing must not put a
    * null image into the viewsheet, which would make every later save fail.
    */
   @Test
   void copyImageWithMissingUploadKeepsViewsheetSavable() throws Exception {
      Viewsheet vs = new Viewsheet();
      ImageVSAssembly image = new ImageVSAssembly(vs, "Image1");
      ((ImageVSAssemblyInfo) image.getInfo()).setImageValue(ImageVSAssemblyInfo.UPLOADED_IMAGE + "reg.png");
      vs.addAssembly(image);
      vs.addUploadedImage("reg.png", new byte[] { 1, 2, 3 });
      // the image is deleted in the image dialog, Image1 still refers to it
      vs.removeUploadedImage("reg.png");

      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);

      CopyVSObjectsEvent copyEvent = new CopyVSObjectsEvent();
      copyEvent.setObjects(new String[] { "Image1" });
      copyEvent.setxOffset(200);
      copyEvent.setyOffset(200);

      service.copyObject("vs1", copyEvent, principal, dispatcher, "/test");

      assertEquals(2, Arrays.stream(vs.getAssemblies())
         .filter(ImageVSAssembly.class::isInstance).count());
      // saving the viewsheet (and its undo checkpoints) must still work
      assertDoesNotThrow(() -> writeXml(vs));
      assertDoesNotThrow(() -> writeXml(vs.clone()));
      // no phantom entry for the missing image in the image dialog
      assertArrayEquals(new String[0], vs.getUploadedImageNames());
   }

   private static String writeXml(Viewsheet vs) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      vs.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   @Test
   void aDraggedContainerIsFlaggedButNotItsChildren() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");

      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getVSAssemblyInfo().setPixelSize(new Dimension(300, 30));
      vs.addAssembly(list);

      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      container.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      container.getVSAssemblyInfo().setPixelSize(new Dimension(300, 360));
      vs.addAssembly(container);
      container.setAssemblies(new String[] { "SelectionList1" });

      resize(vs, "CurrentSelection1", 300, 240);

      assertTrue(container.getVSAssemblyInfo().isUserSize());
      assertFalse(list.getVSAssemblyInfo().isUserSize(), "its re-widened children are not the author's");
   }

   @Test
   void aDraggedListIsFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      list.getVSAssemblyInfo().setPixelSize(new Dimension(132, 202));
      vs.addAssembly(list);

      resize(vs, "SelectionList1", 100, 120);

      assertTrue(list.getVSAssemblyInfo().isUserSize());
   }

   @Test
   void aListResizedToItsOwnSizeIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelectionList1");
      list.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      list.getVSAssemblyInfo().setPixelSize(new Dimension(132, 202));
      vs.addAssembly(list);

      resize(vs, "SelectionList1", 132, 202);

      assertFalse(list.getVSAssemblyInfo().isUserSize(), "a position-only change is not the author's size");
   }

   @Test
   void aDraggedTreeIsFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "SelectionTree1");
      tree.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      tree.getVSAssemblyInfo().setPixelSize(new Dimension(132, 202));
      vs.addAssembly(tree);

      resize(vs, "SelectionTree1", 100, 120);

      assertTrue(tree.getVSAssemblyInfo().isUserSize());
   }

   @Test
   void aDraggedTableIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      table.getVSAssemblyInfo().setPixelSize(new Dimension(400, 200));
      vs.addAssembly(table);

      resize(vs, "Table1", 300, 150);

      assertFalse(table.getVSAssemblyInfo().isUserSize(), "a type without a density size stays clean");
   }

   @Test
   void aDraggedTextIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TextVSAssembly text = new TextVSAssembly();
      text.getVSAssemblyInfo().setName("Text1");
      text.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      text.getVSAssemblyInfo().setPixelSize(new Dimension(100, 20));
      vs.addAssembly(text);

      resize(vs, "Text1", 200, 40);

      assertFalse(text.getVSAssemblyInfo().isUserSize(), "a type without a density size stays clean");
   }

   private void resize(Viewsheet vs, String name, int width, int height) throws Exception {
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      ResizeVSObjectEvent event = new ResizeVSObjectEvent();
      event.setName(name);
      event.setxOffset(0);
      event.setyOffset(0);
      event.setWidth(width);
      event.setHeight(height);
      service.resizeObject(runtimeViewsheetRef.getRuntimeId(), event, principal, dispatcher, "/test");
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
   @Mock VSAssemblyInfoHandler assemblyHandler;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSObjectService vsObjectService;
   @Mock VSCompositionService vsCompositionService;

   private ComposerObjectService service;
}
