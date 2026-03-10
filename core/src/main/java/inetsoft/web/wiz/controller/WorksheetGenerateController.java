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

package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.model.GenerateWsResponse;
import inetsoft.web.wiz.model.WorksheetConstructionModel;
import inetsoft.web.wiz.service.GenerateWsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/wiz")
public class WorksheetGenerateController {
   public WorksheetGenerateController(GenerateWsService generateWsService) {
      this.generateWsService = generateWsService;
   }

   /**
    * Generate worksheet
    *
    * @param user a principal that identifies the remote user.
    * @return the new worksheet id.
    *
    * @since 2025
    */
   @PostMapping(value = "/ws/generate", produces = MediaType.APPLICATION_JSON_VALUE)
   public GenerateWsResponse generateWs(@RequestBody WorksheetConstructionModel model,
                                        Principal user)
   {
      try {
         return generateWsService.generateWs(model, user);
      }
      catch(Exception e) {
         GenerateWsResponse generateWsResponse = new GenerateWsResponse();
         generateWsResponse.setErrorMessage(e.getMessage());
         LOG.error("Failed to generate worksheet", e);
         return generateWsResponse;
      }
   }

   private final GenerateWsService generateWsService;
   private static final Logger LOG =
      LoggerFactory.getLogger(WorksheetGenerateController.class);
}
