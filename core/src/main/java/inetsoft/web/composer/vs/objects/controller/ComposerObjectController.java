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

import inetsoft.web.composer.vs.event.CopyVSObjectsEvent;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;

/**
 * Controller that provides a REST endpoint for object actions.
 */
@Controller
public class ComposerObjectController {
   /**
    * Creates a new instance of <tt>ComposerObjectController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public ComposerObjectController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   ComposerObjectServiceProxy composerObjectServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.composerObjectServiceProxy = composerObjectServiceProxy;
   }

   /**
    * Add new object to the composer.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/addNew")
   public void addNewObject(@Payload AddNewVSObjectEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      composerObjectServiceProxy.addNewObject(runtimeViewsheetRef.getRuntimeId(), event,
                                              principal, dispatcher, linkUri);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/multiresize")
   public void resizeObjects(@Payload MultiResizeVsObjectEvent multiEvent,
                          Principal principal, CommandDispatcher dispatcher,
                          @LinkUri String linkUri)
      throws Exception
   {
      if(multiEvent == null || multiEvent.getEvents() == null) {
         return;
      }

      for(ResizeVSObjectEvent event : multiEvent.getEvents()) {
         this.resizeObject(event, principal, dispatcher, linkUri);
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/multimove")
   public void moveObjects(@Payload MultiMoveVsObjectEvent multiEvent,
                          Principal principal, CommandDispatcher dispatcher,
                          @LinkUri String linkUri)
      throws Exception
   {
      if(multiEvent == null || multiEvent.getEvents() == null) {
         return;
      }

      for(MoveVSObjectEvent event : multiEvent.getEvents()) {
         this.moveObject(event, principal, dispatcher, linkUri);
      }

      composerObjectServiceProxy.moveObjects(runtimeViewsheetRef.getRuntimeId(), multiEvent,
                                             principal, dispatcher, linkUri);
   }

   /**
    * Move object in the composer.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/move")
   public void moveObject(@Payload MoveVSObjectEvent event,
                          Principal principal, CommandDispatcher dispatcher,
                          @LinkUri String linkUri)
      throws Exception
   {
      composerObjectServiceProxy.moveObject(runtimeViewsheetRef.getRuntimeId(), event,
                                            principal, dispatcher, linkUri);
   }

   /**
    * Copy object in the composer.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/copymove")
   public void copyObject(@Payload CopyVSObjectsEvent event,
                          Principal principal, CommandDispatcher dispatcher,
                          @LinkUri String linkUri)
      throws Exception
   {
      composerObjectServiceProxy.copyObject(runtimeViewsheetRef.getRuntimeId(), event,
                                            principal, dispatcher, linkUri);
   }

   /**
    * Resize object in the composer.
    *
    * @param event     the event parameters.
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/resize")
   public void resizeObject(@Payload ResizeVSObjectEvent event,
                            Principal principal, CommandDispatcher dispatcher,
                            @LinkUri String linkUri) throws Exception
   {
      composerObjectServiceProxy.resizeObject(runtimeViewsheetRef.getRuntimeId(), event,
                                              principal, dispatcher, linkUri);
   }


   /**
    * Resize object title in the composer.
    *
    * @param event     the event parameters.
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/resizeTitle")
   public void resizeObjectTitle(@Payload ResizeVSObjectTitleEvent event,
                            Principal principal, CommandDispatcher dispatcher,
                            @LinkUri String linkUri) throws Exception
   {
      composerObjectServiceProxy.resizeObjectTitle(runtimeViewsheetRef.getRuntimeId(), event,
                                                   principal, dispatcher, linkUri);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/removeAll")
   public void removeSelectedObjects(@Payload RemoveVSObjectsEvent event,
                                     @LinkUri String linkUri, Principal principal,
                                     CommandDispatcher dispatcher) throws Exception
   {
      composerObjectServiceProxy.removeSelectedObjects(runtimeViewsheetRef.getRuntimeId(), event,
                                                       linkUri, principal, dispatcher);
   }

   /**
    * Remove object in the composer.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/remove")
   public void removeObject(@Payload RemoveVSObjectEvent event,
                            @LinkUri String linkUri,
                            Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      composerObjectServiceProxy.removeObject(runtimeViewsheetRef.getRuntimeId(), event.getName(),
                                              linkUri, principal, dispatcher);
   }

   /**
    * Check assembly dependency before removing
    *
    * @param runtimeId         the runtime identifier of the viewsheet.
    * @param objectNames       the deleted object names
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to check dependencies
    */
   @PostMapping(
      value="/api/composer/viewsheet/objects/checkAssemblyInUse/**"
   )
   @ResponseBody
   public DependentAssemblies checkAssemblyInUse(@RemainingPath String runtimeId,
                                                 @RequestBody String[] objectNames,
                                                 Principal principal)
      throws Exception
   {
      return composerObjectServiceProxy.checkAssemblyInUse(runtimeId, objectNames, principal);
   }

   /**
    * Change z index of object in the composer.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/changeZIndex")
   public void changeZIndex(@Payload ChangeVSObjectLayerEvent event, Principal principal,
                            CommandDispatcher dispatcher)
      throws Exception
   {
      composerObjectServiceProxy.changeZIndex(runtimeViewsheetRef.getRuntimeId(), event,
                                              principal, dispatcher);
   }

   /**
    * Change the title of the object's component.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/changeTitle")
   public void changeTitle(@Payload ChangeVSObjectTextEvent event, Principal principal,
                          CommandDispatcher dispatcher) throws Exception
   {
      composerObjectServiceProxy.changeTitle(runtimeViewsheetRef.getRuntimeId(), event,
                                             principal, dispatcher);
   }

   /**
    * Change the lock state of the object.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/lock")
   public void changeLockState(@Payload LockVSObjectEvent event, Principal principal,
                               CommandDispatcher dispatcher)
      throws Exception
   {
      composerObjectServiceProxy.changeLockState(runtimeViewsheetRef.getRuntimeId(), event,
                                                 principal, dispatcher);
   }

   /**
    * Remove object from container in the composer.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/objects/moveFromContainer")
   public void moveFromContainer(@Payload MoveVSObjectEvent event, Principal principal,
                                 CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      composerObjectServiceProxy.moveFromContainer(runtimeViewsheetRef.getRuntimeId(), event,
                                                   principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ComposerObjectServiceProxy composerObjectServiceProxy;


   public static final class DependentAssemblies implements Serializable {
      public DependentAssemblies(Map<String, String> assemblies) {
         this.assemblies = assemblies;
      }

      public Map<String, String> getAssemblies() {
         return assemblies;
      }

      private final Map<String, String> assemblies;
   }
}
