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
import inetsoft.web.composer.vs.objects.controller.ComposerGroupService;
import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                               VSObjectPropertyService propertyService,
                               ViewsheetReadService reader,
                               ComposerGroupService groups)
   {
      this.sessions = sessions;
      this.objects = objects;
      this.clipboard = clipboard;
      this.propertyService = propertyService;
      this.reader = reader;
      this.groups = groups;
   }

   /** Ops this service understands, named in the error when an unknown one arrives. */
   static final List<String> OPS = List.of(
      "move", "resize", "resize_title", "add", "remove", "rename", "copy", "cut", "paste",
      "set_z_index", "set_lock", "set_title", "group", "ungroup", "move_from_container",
      "align", "distribute");

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
      case "set_z_index" -> setZIndex(sessionToken, user, request);
      case "set_lock" -> setLock(sessionToken, user, request);
      case "set_title" -> setTitle(sessionToken, user, request);
      case "group" -> group(sessionToken, user, request, linkUri);
      case "ungroup" -> ungroup(sessionToken, user, request, linkUri);
      case "move_from_container" -> moveFromContainer(sessionToken, user, request, linkUri);
      case "align", "distribute" -> arrange(sessionToken, user, request, linkUri, op);
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
         requireExisting(rvs, request.assembly());

         MoveVSObjectEvent move = new MoveVSObjectEvent();
         move.setName(request.assembly());
         move.setxOffset(request.x());
         move.setyOffset(request.y());

         // moveObject is what actually repositions the assembly. moveObjects (plural) only calls
         // updateAnchoredLines — it is the post-move fix-up hook, not the move itself, so calling
         // it alone returns cleanly and changes nothing. ComposerObjectController does both, in
         // this order; so must we.
         objects.moveObject(runtimeId, move, user, dispatcher, linkUri);

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
      requirePositive(request.op(), "width", request.width(), "height", request.height());

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         AssemblyNode current = requireExisting(rvs, request.assembly());

         ResizeVSObjectEvent event = new ResizeVSObjectEvent();
         event.setName(request.assembly());
         event.setWidth(request.width());
         event.setHeight(request.height());
         // resizeObject also *moves* the assembly to the event's offset
         // (`new Point(max(0, xOffset), max(0, yOffset))` then `move(...)`), so leaving these unset
         // silently teleports it to 0,0. Seed them from the assembly's current position.
         event.setxOffset(current.x());
         event.setyOffset(current.y());
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
         AssemblyNode current = requireExisting(rvs, request.assembly());

         ResizeVSObjectTitleEvent event = new ResizeVSObjectTitleEvent();
         event.setName(request.assembly());
         event.setTitleHeight(request.height());
         // resizeObjectTitle delegates to resizeObject, which reads width/height/offset off this
         // same event. Setting only the title height left them at 0, so a "make the title taller"
         // call collapsed the whole assembly to 1x1 at 0,0. Seed the current geometry.
         event.setWidth(current.width());
         event.setHeight(current.height());
         event.setxOffset(current.x());
         event.setyOffset(current.y());
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

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         // An annotation is three linked assemblies. Removing one leaves the survivors
         // pointing at a name that no longer resolves, and nothing reports the orphaning.
         Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
         VSAssembly target = vs == null ? null : vs.getAssembly(request.assembly());

         if(target != null && AnnotationFamily.isPart(target)) {
            throw AnnotationFamily.removeRefusal(request.assembly());
         }

         objects.removeObject(runtimeId, request.assembly(), linkUri, user, dispatcher);
      });
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

   private void setZIndex(String sessionToken, Principal user, EditRequest request)
      throws Exception
   {
      requireAssembly(request);

      if(request.zIndex() == null) {
         throw new IllegalArgumentException("Edit op 'set_z_index' requires 'zIndex'.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChangeVSObjectLayerEvent event = new ChangeVSObjectLayerEvent();
         event.setName(request.assembly());
         event.setzIndex(request.zIndex());
         objects.changeZIndex(runtimeId, event, user, dispatcher);
      });
   }

   private void setLock(String sessionToken, Principal user, EditRequest request)
      throws Exception
   {
      requireAssembly(request);

      if(request.locked() == null) {
         throw new IllegalArgumentException(
            "Edit op 'set_lock' requires 'locked' (true to lock, false to unlock).");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         LockVSObjectEvent event = new LockVSObjectEvent();
         event.setName(request.assembly());
         event.setLocked(request.locked());
         objects.changeLockState(runtimeId, event, user, dispatcher);
      });
   }

   private void setTitle(String sessionToken, Principal user, EditRequest request)
      throws Exception
   {
      requireAssembly(request);

      if(request.title() == null) {
         throw new IllegalArgumentException("Edit op 'set_title' requires 'title'.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         ChangeVSObjectTextEvent event = new ChangeVSObjectTextEvent();
         event.setName(request.assembly());
         event.setText(request.title());
         objects.changeTitle(runtimeId, event, user, dispatcher);
      });
   }

   private void group(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      List<String> names = request.assemblies();

      if(names == null || names.size() < 2) {
         throw new IllegalArgumentException(
            "Edit op 'group' requires 'assemblies' with at least two assembly names.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         groups.groupComponents(runtimeId,
                                GroupVSObjectsEvent.builder().objects(names).build(),
                                linkUri, user, dispatcher));
   }

   private void ungroup(String sessionToken, Principal user, EditRequest request, String linkUri)
      throws Exception
   {
      requireAssembly(request);

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) ->
         groups.ungroup(runtimeId, request.assembly(), linkUri, user, dispatcher));
   }

   private void moveFromContainer(String sessionToken, Principal user, EditRequest request,
                                  String linkUri) throws Exception
   {
      requireAssembly(request);
      requireValues(request.op(), "x", request.x(), "y", request.y());

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         MoveVSObjectEvent event = new MoveVSObjectEvent();
         event.setName(request.assembly());
         event.setxOffset(request.x());
         event.setyOffset(request.y());
         objects.moveFromContainer(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   /**
    * Align and distribute have no Composer service — they are position arithmetic. Compute the
    * targets from the current layout, then issue ONE move event so the whole arrangement is a
    * single undo checkpoint rather than one per assembly.
    */
   private void arrange(String sessionToken, Principal user, EditRequest request,
                        String linkUri, String op) throws Exception
   {
      List<String> names = request.assemblies();

      if(names == null || names.size() < 2) {
         throw new IllegalArgumentException(
            "Edit op '" + op + "' requires 'assemblies' with at least two assembly names.");
      }

      String axis = request.axis() == null ? "" : request.axis().trim().toLowerCase();
      List<String> valid = "align".equals(op)
         ? List.of("left", "right", "top", "bottom")
         : List.of("horizontal", "vertical");

      if(!valid.contains(axis)) {
         throw new IllegalArgumentException(
            "Edit op '" + op + "' requires 'axis' to be one of: " + String.join(", ", valid) +
            ". Got '" + request.axis() + "'.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         Map<String, AssemblyNode> byName = new LinkedHashMap<>();

         for(AssemblyNode node : reader.read(rvs).assemblies()) {
            byName.put(node.name(), node);
         }

         List<AssemblyNode> targets = new ArrayList<>();

         for(String name : names) {
            AssemblyNode node = byName.get(name);

            if(node == null) {
               throw new IllegalArgumentException(
                  "Unknown assembly '" + name + "'. Available: " + byName.keySet() + ".");
            }

            targets.add(node);
         }

         MoveVSObjectEvent[] moves = "align".equals(op)
            ? alignMoves(targets, axis)
            : distributeMoves(targets, axis);

         // Same two-step contract as move(): moveObject actually repositions each assembly,
         // moveObjects afterwards only fixes up anchored lines. Calling the fix-up alone made
         // align and distribute no-ops that reported success.
         for(MoveVSObjectEvent each : moves) {
            objects.moveObject(runtimeId, each, user, dispatcher, linkUri);
         }

         MultiMoveVsObjectEvent event = new MultiMoveVsObjectEvent();
         event.setEvents(moves);
         objects.moveObjects(runtimeId, event, user, dispatcher, linkUri);
      });
   }

   private static MoveVSObjectEvent[] alignMoves(List<AssemblyNode> targets, String axis) {
      int edge = switch(axis) {
         case "left" -> targets.stream().mapToInt(AssemblyNode::x).min().orElseThrow();
         case "right" -> targets.stream().mapToInt(n -> n.x() + n.width()).max().orElseThrow();
         case "top" -> targets.stream().mapToInt(AssemblyNode::y).min().orElseThrow();
         default -> targets.stream().mapToInt(n -> n.y() + n.height()).max().orElseThrow();
      };

      MoveVSObjectEvent[] moves = new MoveVSObjectEvent[targets.size()];

      for(int i = 0; i < targets.size(); i++) {
         AssemblyNode node = targets.get(i);
         moves[i] = move(node.name(),
                         switch(axis) {
                            case "left" -> edge;
                            case "right" -> edge - node.width();
                            default -> node.x();
                         },
                         switch(axis) {
                            case "top" -> edge;
                            case "bottom" -> edge - node.height();
                            default -> node.y();
                         });
      }

      return moves;
   }

   /**
    * Spreads the assemblies evenly between the first and last along {@code axis}, ordered by
    * their current position so the two outermost stay put and only the middle ones move.
    */
   private static MoveVSObjectEvent[] distributeMoves(List<AssemblyNode> targets, String axis) {
      boolean horizontal = "horizontal".equals(axis);
      List<AssemblyNode> ordered = new ArrayList<>(targets);
      ordered.sort(Comparator.comparingInt(horizontal ? AssemblyNode::x : AssemblyNode::y));

      int first = horizontal ? ordered.get(0).x() : ordered.get(0).y();
      AssemblyNode lastNode = ordered.get(ordered.size() - 1);
      int last = horizontal ? lastNode.x() : lastNode.y();
      int gaps = ordered.size() - 1;
      MoveVSObjectEvent[] moves = new MoveVSObjectEvent[ordered.size()];

      for(int i = 0; i < ordered.size(); i++) {
         AssemblyNode node = ordered.get(i);
         int position = first + Math.round((float) (last - first) * i / gaps);
         moves[i] = horizontal
            ? move(node.name(), position, node.y())
            : move(node.name(), node.x(), position);
      }

      return moves;
   }

   private static MoveVSObjectEvent move(String name, int x, int y) {
      MoveVSObjectEvent event = new MoveVSObjectEvent();
      event.setName(name);
      event.setxOffset(x);
      event.setyOffset(y);
      return event;
   }

   private static void requireAssembly(EditRequest request) {
      if(request.assembly() == null || request.assembly().isBlank()) {
         throw new IllegalArgumentException(
            "Edit op '" + request.op() + "' requires 'assembly' — the assembly name.");
      }
   }

   /**
    * Resolves an assembly by name, failing loud if it does not exist, and returns its current
    * geometry so callers can seed Composer events that read position/size.
    *
    * <p>This check cannot be delegated to the composer layer. {@code ComposerObjectService} looks
    * the assembly up with {@code viewsheet.getAssembly(name)}, gets null, falls through every
    * {@code instanceof} guard and returns normally — so a misspelled name produced a cheerful
    * "ok" on every op. Nothing downstream will ever complain, so we complain here.
    */
   private AssemblyNode requireExisting(RuntimeViewsheet rvs, String name) {
      Map<String, AssemblyNode> byName = new LinkedHashMap<>();

      for(AssemblyNode node : reader.read(rvs).assemblies()) {
         byName.put(node.name(), node);
      }

      AssemblyNode node = byName.get(name);

      if(node == null) {
         throw new IllegalArgumentException(
            "Unknown assembly '" + name + "'. Available: " + byName.keySet() + ".");
      }

      return node;
   }

   /**
    * Rejects non-positive dimensions. StyleBI clamps them to 1x1 rather than refusing, so
    * {@code resize -50 -20} silently destroyed the assembly's geometry and reported success.
    */
   private static void requirePositive(String op, String firstName, Integer first,
                                       String secondName, Integer second)
   {
      if(first != null && first <= 0 || second != null && second <= 0) {
         throw new IllegalArgumentException(
            "Edit op '" + op + "' requires '" + firstName + "' and '" + secondName +
            "' to be greater than zero — got " + first + " and " + second +
            ". Non-positive sizes are clamped to 1x1 rather than refused.");
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
   private final ViewsheetReadService reader;
   private final ComposerGroupService groups;
}
