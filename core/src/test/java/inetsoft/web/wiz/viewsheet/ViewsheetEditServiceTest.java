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
import inetsoft.web.composer.vs.objects.controller.ComposerGroupService;
import inetsoft.web.composer.vs.objects.event.AddNewVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectLayerEvent;
import inetsoft.web.composer.vs.objects.event.LockVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.MoveVSObjectEvent;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
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

   @Test
   void alignLeftMovesEveryAssemblyToTheLeftmostEdge() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetReadService reader = readerReturning(
         new AssemblyNode("A", "Text", 50, 10, 100, 20, 0, null, true),
         new AssemblyNode("B", "Text", 20, 60, 100, 20, 0, null, true));
      ViewsheetEditService service = serviceWith(objects, reader);

      service.apply("tok", principal(), arrange("align", List.of("A", "B"), "left"), "");

      MoveVSObjectEvent[] moves = capturedMoves(objects);
      assertEquals(2, moves.length);
      assertEquals(20, moves[0].getxOffset());
      assertEquals(20, moves[1].getxOffset());
      assertEquals(10, moves[0].getyOffset(), "align left must not change y");
   }

   @Test
   void alignRightUsesTheRightmostEdgeAndAccountsForWidth() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetReadService reader = readerReturning(
         new AssemblyNode("A", "Text", 50, 10, 100, 20, 0, null, true),
         new AssemblyNode("B", "Text", 20, 60, 40, 20, 0, null, true));
      ViewsheetEditService service = serviceWith(objects, reader);

      service.apply("tok", principal(), arrange("align", List.of("A", "B"), "right"), "");

      MoveVSObjectEvent[] moves = capturedMoves(objects);
      assertEquals(50, moves[0].getxOffset(), "A already ends at the rightmost edge 150");
      assertEquals(110, moves[1].getxOffset(), "B must end at 150, so x = 150 - 40");
   }

   @Test
   void distributeHorizontallySpreadsTheMiddleAssembliesEvenly() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetReadService reader = readerReturning(
         new AssemblyNode("A", "Text", 0, 0, 10, 10, 0, null, true),
         new AssemblyNode("B", "Text", 5, 0, 10, 10, 0, null, true),
         new AssemblyNode("C", "Text", 100, 0, 10, 10, 0, null, true));
      ViewsheetEditService service = serviceWith(objects, reader);

      service.apply("tok", principal(),
                    arrange("distribute", List.of("A", "B", "C"), "horizontal"), "");

      MoveVSObjectEvent[] moves = capturedMoves(objects);
      assertEquals(0, moves[0].getxOffset(), "the first assembly anchors");
      assertEquals(50, moves[1].getxOffset(), "the middle one sits halfway");
      assertEquals(100, moves[2].getxOffset(), "the last assembly anchors");
   }

   @Test
   void alignRejectsAnUnknownAxis() {
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.apply("tok", principal(),
                             arrange("align", List.of("A", "B"), "sideways"), ""));
      assertTrue(thrown.getMessage().contains("sideways"));
   }

   @Test
   void alignRejectsFewerThanTwoAssemblies() {
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.apply("tok", principal(), arrange("align", List.of("A"), "left"), ""));
      assertTrue(thrown.getMessage().contains("assemblies"));
   }

   @Test
   void alignNamesTheUnknownAssemblyRatherThanSkippingIt() {
      ViewsheetReadService reader = readerReturning(
         new AssemblyNode("A", "Text", 0, 0, 10, 10, 0, null, true));
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class), reader);

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.apply("tok", principal(), arrange("align", List.of("A", "Ghost"), "left"), ""));
      assertTrue(thrown.getMessage().contains("Ghost"));
   }

   @Test
   void setZIndexDelegatesTheLayerChange() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(objects);
      EditRequest request = new EditRequest("set_z_index", "Gauge1", null, null, null, null,
                                            7, null, null, null, null, null, null, null);

      service.apply("tok", principal(), request, "");

      ArgumentCaptor<ChangeVSObjectLayerEvent> captor =
         ArgumentCaptor.forClass(ChangeVSObjectLayerEvent.class);
      verify(objects).changeZIndex(eq("rt1"), captor.capture(), any(Principal.class), any());
      assertEquals(7, captor.getValue().getzIndex());
   }

   @Test
   void setLockDelegatesTheLockState() throws Exception {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith(objects);
      EditRequest request = new EditRequest("set_lock", "Rect1", null, null, null, null,
                                            null, null, Boolean.TRUE, null, null, null,
                                            null, null);

      service.apply("tok", principal(), request, "");

      ArgumentCaptor<LockVSObjectEvent> captor = ArgumentCaptor.forClass(LockVSObjectEvent.class);
      verify(objects).changeLockState(eq("rt1"), captor.capture(), any(Principal.class), any());
      assertTrue(captor.getValue().isLocked());
   }

   @Test
   void ungroupDelegatesTheContainerName() throws Exception {
      ComposerGroupService groups = mock(ComposerGroupService.class);
      ViewsheetEditService service = serviceWith(mock(ComposerObjectService.class),
                                                 mock(ClipboardControllerService.class),
                                                 mock(ViewsheetReadService.class), groups);

      service.apply("tok", principal(), request("ungroup", "Group1"), "");

      verify(groups).ungroup(eq("rt1"), eq("Group1"), anyString(), any(Principal.class), any());
   }

   private static MoveVSObjectEvent[] capturedMoves(ComposerObjectService objects)
      throws Exception
   {
      ArgumentCaptor<MultiMoveVsObjectEvent> captor =
         ArgumentCaptor.forClass(MultiMoveVsObjectEvent.class);
      verify(objects).moveObjects(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                  anyString());
      return captor.getValue().getEvents();
   }

   private static ViewsheetReadService readerReturning(AssemblyNode... nodes) {
      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      when(reader.read(any())).thenReturn(new ViewsheetModel("vs", List.of(nodes)));
      return reader;
   }

   private static EditRequest arrange(String op, List<String> assemblies, String axis) {
      return new EditRequest(op, null, null, null, null, null, null, null, null, null,
                             assemblies, null, null, axis);
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
                                                   ViewsheetReadService reader)
   {
      return serviceWith(objects, mock(ClipboardControllerService.class), reader,
                         mock(ComposerGroupService.class));
   }

   private static ViewsheetEditService serviceWith(ComposerObjectService objects,
                                                   ClipboardControllerService clipboard)
   {
      return serviceWith(objects, clipboard, mock(ViewsheetReadService.class),
                         mock(ComposerGroupService.class));
   }

   private static ViewsheetEditService serviceWith(ComposerObjectService objects,
                                                   ClipboardControllerService clipboard,
                                                   ViewsheetReadService reader,
                                                   ComposerGroupService groups)
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
                                     mock(VSObjectPropertyService.class), reader, groups);
   }
}
