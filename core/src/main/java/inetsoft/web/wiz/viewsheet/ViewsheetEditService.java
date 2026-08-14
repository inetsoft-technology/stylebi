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

import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.vs.event.CopyVSObjectsEvent;
import inetsoft.web.composer.vs.objects.controller.ClipboardControllerService;
import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.event.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

/**
 * Applies structural edits by delegating to the Composer's own services.
 *
 * <p>Each {@code apply} call is exactly one {@code mutate}, so it is one undo checkpoint in
 * the human's Composer — matching what a single Composer action does.
 */
@Service
public class ViewsheetEditService {
   @Autowired
   public ViewsheetEditService(ViewsheetSessionService sessions,
                               ComposerObjectService objects,
                               ClipboardControllerService clipboard,
                               VSObjectPropertyService propertyService)
   {
      this.sessions = sessions;
      this.objects = objects;
      this.clipboard = clipboard;
      this.propertyService = propertyService;
   }

   /** Ops this service understands, named in the error when an unknown one arrives. */
   static final List<String> OPS = List.of(
      "move", "resize", "resize_title", "add", "remove", "rename", "copy", "paste");

   public void apply(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      String op = request.op() == null ? "" : request.op().trim().toLowerCase();

      switch(op) {
      case "move" -> move(sessionToken, user, request, linkUri);
      case "resize" -> resize(sessionToken, user, request, linkUri);
      case "resize_title" -> resizeTitle(sessionToken, user, request, linkUri);
      case "add" -> add(sessionToken, user, request, linkUri);
      case "remove" -> remove(sessionToken, user, request, linkUri);
      case "rename" -> rename(sessionToken, user, request, linkUri);
      case "copy", "cut" -> copyOrCut(sessionToken, user, request, linkUri, "cut".equals(op));
      case "paste" -> paste(sessionToken, user, request, linkUri);
      default -> throw new IllegalArgumentException(
         "Unknown edit op '" + request.op() + "'. Supported ops: " + String.join(", ", OPS) + ".");
      }
   }

   private void move(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      requireAssembly(request);
      requireValues(request.op(), "x", request.x(), "y", request.y());

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         MoveVSObjectEvent move = new MoveVSObjectEvent();
         move.setName(request.assembly());
         move.setxOffset(request.x());
         move.setyOffset(request.y());

         MultiMoveVsObjectEvent event = new MultiMoveVsObjectEvent();
         event.setEvents(new MoveVSObjectEvent[]{ move });
         objects.moveObjects(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private void resize(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      requireAssembly(request);
      requireValues(request.op(), "width", request.width(), "height", request.height());

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ResizeVSObjectEvent event = new ResizeVSObjectEvent();
         event.setName(request.assembly());
         event.setWidth(request.width());
         event.setHeight(request.height());
         objects.resizeObject(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   /**
    * Resizes an assembly's title bar. {@code ResizeVSObjectTitleEvent} carries a title height
    * rather than a width/height pair, so this op reads {@code height} alone.
    */
   private void resizeTitle(String sessionToken, Principal user, EditRequest request,
                            String linkUri) throws Exception
   {
      requireAssembly(request);

      if(request.height() == null) {
         throw new IllegalArgumentException(
            "Edit op 'resize_title' requires 'height' — the new title-bar height in pixels.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ResizeVSObjectTitleEvent event = new ResizeVSObjectTitleEvent();
         event.setName(request.assembly());
         event.setTitleHeight(request.height());
         objects.resizeObjectTitle(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private void add(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      if(request.type() == null) {
         throw new IllegalArgumentException(
            "Edit op 'add' requires 'type' — the AbstractSheet asset code for the assembly " +
            "(e.g. 111 text, 112 image, 125 line, 126 rectangle, 127 oval).");
      }

      requireValues(request.op(), "x", request.x(), "y", request.y());

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         AddNewVSObjectEvent event = new AddNewVSObjectEvent();
         event.setType(request.type());
         event.setxOffset(request.x());
         event.setyOffset(request.y());
         objects.addNewObject(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private void remove(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      requireAssembly(request);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         objects.removeObject(runtimeId, request.assembly(), linkUri, user, dispatcher));
   }

   /**
    * Renames through the Composer's own property path, which also requalifies references to
    * the old name. The capturing dispatcher matters most here: a rename that the service
    * refuses — a dependency cycle, say — is reported as an ERROR command and a normal return,
    * so without capture it would look like success.
    */
   private void rename(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      requireAssembly(request);

      if(request.newName() == null || request.newName().isBlank()) {
         throw new IllegalArgumentException(
            "Edit op 'rename' requires 'newName' — the new assembly name.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs == null ? null : (VSAssembly) vs.getAssembly(request.assembly());

         if(assembly == null) {
            throw new IllegalArgumentException(
               "Unknown assembly '" + request.assembly() + "'.");
         }

         propertyService.editObjectProperty(rvs, assembly.getVSAssemblyInfo(),
                                            request.assembly(), request.newName(),
                                            linkUri, user, dispatcher);
      });
   }

   private void copyOrCut(String sessionToken, Principal user, EditRequest request,
                          String linkUri, boolean cut) throws Exception
   {
      List<String> names = request.assemblies();

      if(names == null || names.isEmpty()) {
         throw new IllegalArgumentException(
            "Edit op '" + request.op() + "' requires 'assemblies' with at least one name.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         CopyVSObjectsEvent event = new CopyVSObjectsEvent();
         event.setObjects(names.toArray(new String[0]));
         event.setCut(cut);
         clipboard.copyOrCut(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private void paste(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      requireValues(request.op(), "x", request.x(), "y", request.y());

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         clipboard.pasteObject(runtimeId, request.x(), request.y(), user, dispatcher, linkUri));
   }

   private static void requireAssembly(EditRequest request) {
      if(request.assembly() == null || request.assembly().isBlank()) {
         throw new IllegalArgumentException(
            "Edit op '" + request.op() + "' requires 'assembly' — the assembly name.");
      }
   }

   private static void requireValues(String op, String firstName, Object first,
                                     String secondName, Object second)
   {
      if(first == null || second == null) {
         throw new IllegalArgumentException(
            "Edit op '" + op + "' requires both '" + firstName + "' and '" + secondName + "'.");
      }
   }

   private final ViewsheetSessionService sessions;
   private final ComposerObjectService objects;
   private final ClipboardControllerService clipboard;
   private final VSObjectPropertyService propertyService;
}
