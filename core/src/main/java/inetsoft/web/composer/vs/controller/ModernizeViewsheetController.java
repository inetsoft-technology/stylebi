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
package inetsoft.web.composer.vs.controller;

import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Modernize the focused dashboard. One undo step, per decision 5 - @Undoable snapshots after the
 * action returns, so Ctrl+Z restores the unmarked state and, with it, the offer to modernize.
 */
@Controller
public class ModernizeViewsheetController {
   @Autowired
   public ModernizeViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       ModernizeViewsheetServiceProxy modernizeViewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.modernizeViewsheetService = modernizeViewsheetService;
   }

   @Undoable
   @LoadingMask
   @HandleAssetExceptions
   @MessageMapping("composer/viewsheet/modernize")
   public void modernize(Principal principal, CommandDispatcher commandDispatcher,
                         @LinkUri String linkUri)
      throws Exception
   {
      modernizeViewsheetService.modernize(runtimeViewsheetRef.getRuntimeId(), principal,
                                         commandDispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ModernizeViewsheetServiceProxy modernizeViewsheetService;
}
