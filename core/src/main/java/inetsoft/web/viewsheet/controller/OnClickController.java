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

import inetsoft.web.binding.event.VSOnClickEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.VSSubmitEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class OnClickController {
   @Autowired
   public OnClickController(RuntimeViewsheetRef runtimeViewsheetRef,
                            OnClickServiceProxy onClickServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.onClickServiceProxy = onClickServiceProxy;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/onclick/{name}/{x}/{y}/{isConfirm}")
   public void onConfirm(@DestinationVariable("name") String name,
                       @DestinationVariable("x") String x,
                       @DestinationVariable("y") String y,
                       @DestinationVariable("isConfirm") boolean isConfirm,
                       @Payload VSOnClickEvent confirmEvent,
                       @LinkUri String linkUri, Principal principal,
                         CommandDispatcher dispatcher) throws Exception
   {
      onClickServiceProxy.onConfirm(runtimeViewsheetRef.getRuntimeId(), name, x, y, isConfirm,
                                    confirmEvent, linkUri, principal, dispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/onclick/{name}/{x}/{y}")
   public void onClick(@DestinationVariable("name") String name,
                       @DestinationVariable("x") String x,
                       @DestinationVariable("y") String y,
                       @Payload VSSubmitEvent submitEvent,
                       @LinkUri String linkUri, Principal principal,
                       CommandDispatcher dispatcher) throws Exception
   {
      onClickServiceProxy.onClick(runtimeViewsheetRef.getRuntimeId(), name, x, y, submitEvent,
                                  linkUri, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private OnClickServiceProxy onClickServiceProxy;
   }
