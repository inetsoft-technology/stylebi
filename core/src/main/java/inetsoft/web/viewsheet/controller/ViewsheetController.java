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

import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import inetsoft.web.viewsheet.service.VSLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides endpoints to perform top-level functions on a viewsheet
 * instance.
 *
 * @since 12.3
 */
@Controller
public class ViewsheetController {
   /**
    * Creates a new instance of <tt>ViewsheetController</tt>.
    */
   @Autowired
   public ViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                              RuntimeViewsheetManager runtimeViewsheetManager,
                              VSLifecycleService vsLifecycleService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      this.vsLifecycleService = vsLifecycleService;
   }

   /**
    * Closes the viewsheet associated with the current web socket session.
    *
    * @param principal a principal identifying the current user.
    */
   @MessageMapping("/close")
   public void closeViewsheet(Principal principal) {
      if(runtimeViewsheetRef.getRuntimeId() != null) {
         vsLifecycleService.closeViewsheet(
            runtimeViewsheetRef.getRuntimeId(), principal, runtimeViewsheetManager);
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RuntimeViewsheetManager runtimeViewsheetManager;
   private final VSLifecycleService vsLifecycleService;
}
