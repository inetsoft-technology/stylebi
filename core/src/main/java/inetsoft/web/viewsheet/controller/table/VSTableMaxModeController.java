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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.table.MaxTableEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * Controller that processes vs form events.
 */
@Controller
public class VSTableMaxModeController {
   @Autowired
   public VSTableMaxModeController(VSTableMaxModeServiceProxy vsTableMaxModeServiceProxy,
                                RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsTableMaxModeServiceProxy = vsTableMaxModeServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vstable/toggle-max-mode")
   public void toggleMaxMode(MaxTableEvent event, Principal principal, @LinkUri String linkUri,
                             CommandDispatcher dispatcher)
      throws Exception
   {
      vsTableMaxModeServiceProxy.toggleMaxMode(getRuntimeId(), event, principal, linkUri, dispatcher);
   }

   /**
    * @return the runtime ID from the injected RuntimeViewsheetRef
    */
   protected String getRuntimeId() {
      return runtimeViewsheetRef.getRuntimeId();
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private VSTableMaxModeServiceProxy vsTableMaxModeServiceProxy;

}
