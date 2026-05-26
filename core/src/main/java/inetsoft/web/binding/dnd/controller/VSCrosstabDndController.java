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
package inetsoft.web.binding.dnd.controller;

import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class VSCrosstabDndController {
   /**
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSCrosstabDndController(RuntimeViewsheetRef runtimeViewsheetRef,
                                  VSCrosstabDndServiceProxy vsCrosstabDndServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsCrosstabDndServiceProxy = vsCrosstabDndServiceProxy;
   }

   /**
    * process adjust binding order by dnd.
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscrosstab/dnd/addRemoveColumns")
   public void dnd(@Payload VSDndEvent event, Principal principal,
                   @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      vsCrosstabDndServiceProxy.dnd(runtimeViewsheetRef.getRuntimeId(), event,
                                    principal, linkUri, dispatcher);
   }

   @PutMapping("/api/vscrosstab/dnd/checktrap")
   @ResponseBody
   public boolean checktrap(@DecodeParam("vsId") String vsId, @RequestBody VSDndEvent event,
                            Principal principal) throws Exception
   {
      return vsCrosstabDndServiceProxy.checktrap(vsId, event, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscrosstab/dnd/addColumns")
   public void dndFromTree(@Payload VSDndEvent event, Principal principal,
                           @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
     vsCrosstabDndServiceProxy.dndFromTree(runtimeViewsheetRef.getRuntimeId(), event, principal,
                                           linkUri, dispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping(value="/vscrosstab/dnd/removeColumns")
   public void dndTotree(@Payload VSDndEvent event, Principal principal,
                         @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      vsCrosstabDndServiceProxy.dndTotree(runtimeViewsheetRef.getRuntimeId(), event, principal, linkUri, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSCrosstabDndServiceProxy vsCrosstabDndServiceProxy;
}
