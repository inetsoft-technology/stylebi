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
package inetsoft.web.viewsheet.controller;

import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for undo and redo actions.
 */
@Controller
public class UndoRedoController {
   /**
    * Creates a new instance of <tt>ComposerUndoRedoController</tt>.
    */
   @Autowired
   public UndoRedoController(RuntimeViewsheetRef runtimeViewsheetRef,
                             UndoRedoServiceProxy undoRedoServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.undoRedoServiceProxy = undoRedoServiceProxy;
   }

   /**
    * Undo/revert to a previous viewsheet state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask(true)
   @MessageMapping("undo")
   @HandleAssetExceptions
   public void undo(Principal principal, @LinkUri String linkUri,
                    CommandDispatcher dispatcher) throws Exception
   {
      undoRedoServiceProxy.undo(runtimeViewsheetRef.getRuntimeId(), principal, linkUri, dispatcher);
   }

   /**
    * Redo/change to a future viewsheet state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask(true)
   @MessageMapping("redo")
   @HandleAssetExceptions
   public void redo(Principal principal, @LinkUri String linkUri,
                    CommandDispatcher dispatcher) throws Exception
   {
      undoRedoServiceProxy.redo(runtimeViewsheetRef.getRuntimeId(), principal, linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private UndoRedoServiceProxy undoRedoServiceProxy;
}
