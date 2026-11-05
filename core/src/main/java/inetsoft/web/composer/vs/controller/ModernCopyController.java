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

import inetsoft.web.composer.model.vs.ModernCopyRequest;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Creates a modern copy; not @Undoable, as the service adds the undo step under the save point.
 */
@Controller
public class ModernCopyController {
   @Autowired
   public ModernCopyController(RuntimeViewsheetRef runtimeViewsheetRef,
                               ModernCopyServiceProxy modernCopyService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.modernCopyService = modernCopyService;
   }

   @LoadingMask
   @HandleAssetExceptions
   @MessageMapping("composer/viewsheet/modernize-copy")
   public void createModernCopy(@Payload ModernCopyRequest request, Principal principal,
                                CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      modernCopyService.createModernCopy(runtimeViewsheetRef.getRuntimeId(),
                                         request == null ? null : request.getName(),
                                         principal, commandDispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ModernCopyServiceProxy modernCopyService;
}
