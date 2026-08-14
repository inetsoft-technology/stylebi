/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet;

import inetsoft.web.composer.vs.event.CopyVSObjectsEvent;
import inetsoft.web.composer.vs.objects.controller.ClipboardControllerService;
import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.event.AddNewVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.MultiMoveVsObjectEvent;
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectTitleEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ViewsheetEditServiceTest {
   @Test
   void moveDelegatesTheNewPositionToComposerObjectService() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(objects);

      service.apply("tok", principal(), edit("move", "Gauge1", 120, 240), "");

      ArgumentCaptor<MultiMoveVsObjectEvent> captor =
         ArgumentCaptor.forClass(MultiMoveVsObjectEvent.class);
      verify(objects).moveObjects(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      assertEquals("Gauge1", captor.getValue().getEvents()[0].getName());
      assertEquals(120, captor.getValue().getEvents()[0].getxOffset());
      assertEquals(240, captor.getValue().getEvents()[0].getyOffset());
   }

   @Test
   void resizeDelegatesTheNewSize() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(objects);

      EditRequest request = request("resize", "Gauge1");
      request = new EditRequest(request.op(), request.assembly(), null, null, 300, 150,
                                null, null, null, null, null, null, null, null);
      service.apply("tok", principal(), request, "");

      ArgumentCaptor<ResizeVSObjectEvent> captor =
         ArgumentCaptor.forClass(ResizeVSObjectEvent.class);
      verify(objects).resizeObject(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                   anyString());
      assertEquals(300, captor.getValue().getWidth());
      assertEquals(150, captor.getValue().getHeight());
   }

   @Test
   void resizeTitleDelegatesTheTitleHeight() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(objects);

      EditRequest request = new EditRequest("resize_title", "Table1", null, null, null, 28,
                                            null, null, null, null, null, null, null, null);
      service.apply("tok", principal(), request, "");

      ArgumentCaptor<ResizeVSObjectTitleEvent> captor =
         ArgumentCaptor.forClass(ResizeVSObjectTitleEvent.class);
      verify(objects).resizeObjectTitle(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                        anyString());
      assertEquals(28, captor.getValue().getTitleHeight());
   }

   @Test
   void unknownOpFailsLoudNamingTheOp() {
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.apply("tok", principal(), request("teleport", "Gauge1"), ""));
      assertTrue(thrown.getMessage().contains("teleport"),
                 "the error should name the rejected op, got: " + thrown.getMessage());
   }

   @Test
   void moveWithoutCoordinatesFailsLoudRatherThanMovingToTheOrigin() {
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.apply("tok", principal(), request("move", "Gauge1"), ""));
      assertTrue(thrown.getMessage().contains("x"));
   }

   @Test
   void removeDelegatesTheAssemblyNameToComposerObjectService() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(objects);

      service.apply("tok", principal(), request("remove", "Gauge1"), "");

      verify(objects).removeObject(eq("rt1"), eq("Gauge1"), anyString(), any(Principal.class),
                                   any());
   }

   @Test
   void addRequiresATypeAndFailsLoudWithoutOne() {
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class));
      EditRequest request = new EditRequest("add", null, 10, 10, null, null, null, null,
                                            null, null, null, null, null, null);

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> service.apply("tok", principal(), request, ""));
      assertTrue(thrown.getMessage().contains("type"));
   }

   @Test
   void addDelegatesTheAssetTypeAndPosition() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(objects);
      EditRequest request = new EditRequest("add", null, 40, 60, null, null, null, null,
                                            null, null, null, null, 111, null);

      service.apply("tok", principal(), request, "");

      ArgumentCaptor<AddNewVSObjectEvent> captor =
         ArgumentCaptor.forClass(AddNewVSObjectEvent.class);
      verify(objects).addNewObject(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                   anyString());
      assertEquals(111, captor.getValue().getType());
      assertEquals(40, captor.getValue().getxOffset());
   }

   @Test
   void renameRequiresANewNameAndFailsLoudWithoutOne() {
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.apply("tok", principal(), request("rename", "Gauge1"), ""));
      assertTrue(thrown.getMessage().contains("newName"));
   }

   @Test
   void copyDelegatesTheAssemblyNames() throws Exception {
      ClipboardControllerService clipboard = mock(ClipboardControllerService.class);
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class), clipboard);
      EditRequest request = new EditRequest("copy", null, null, null, null, null, null, null,
                                            null, null, List.of("Gauge1", "Text1"), null,
                                            null, null);

      service.apply("tok", principal(), request, "");

      ArgumentCaptor<CopyVSObjectsEvent> captor =
         ArgumentCaptor.forClass(CopyVSObjectsEvent.class);
      verify(clipboard).copyOrCut(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      assertArrayEquals(new String[]{ "Gauge1", "Text1" }, captor.getValue().getObjects());
   }

   @Test
   void pasteDelegatesTheTargetPosition() throws Exception {
      ClipboardControllerService clipboard = mock(ClipboardControllerService.class);
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class), clipboard);

      service.apply("tok", principal(), edit("paste", null, 15, 25), "");

      verify(clipboard).pasteObject(eq("rt1"), eq(15), eq(25), any(Principal.class), any(),
                                    anyString());
   }

   private static EditRequest request(String op, String assembly) {
      return new EditRequest(op, assembly, null, null, null, null, null, null, null, null,
                             null, null, null, null);
   }

   private static EditRequest edit(String op, String assembly, Integer x, Integer y) {
      return new EditRequest(op, assembly, x, y, null, null, null, null, null, null,
                             null, null, null, null);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   /**
    * A session service whose mutate() runs the mutation immediately against runtime "rt1",
    * so these tests exercise op dispatch and validation without a live runtime.
    */
   private static ViewsheetEditService serviceWith(ComposerObjectService objects) {
      return serviceWith(objects, mock(ClipboardControllerService.class));
   }

   private static ViewsheetEditService serviceWith(ComposerObjectService objects,
                                                   ClipboardControllerService clipboard)
   {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(null, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new ViewsheetEditService(sessions, objects, clipboard,
                                     mock(VSObjectPropertyService.class));
   }
}
