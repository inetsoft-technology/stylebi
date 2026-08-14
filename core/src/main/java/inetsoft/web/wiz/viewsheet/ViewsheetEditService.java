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

import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
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
   public ViewsheetEditService(ViewsheetSessionService sessions, ComposerObjectService objects) {
      this.sessions = sessions;
      this.objects = objects;
   }

   /** Ops this service understands, named in the error when an unknown one arrives. */
   static final List<String> OPS = List.of("move", "resize", "resize_title");

   public void apply(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      String op = request.op() == null ? "" : request.op().trim().toLowerCase();

      switch(op) {
      case "move" -> move(sessionToken, user, request, linkUri);
      case "resize" -> resize(sessionToken, user, request, linkUri);
      case "resize_title" -> resizeTitle(sessionToken, user, request, linkUri);
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
}
