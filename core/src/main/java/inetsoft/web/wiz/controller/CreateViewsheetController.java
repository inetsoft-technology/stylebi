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

import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.service.CreateVsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class CreateViewsheetController {
   public CreateViewsheetController(CreateVsService createVsService) {
      this.createVsService = createVsService;
   }

   @PostMapping(value = "/api/public/viewsheet/create/vs", produces = MediaType.APPLICATION_JSON_VALUE)
   public void createViewsheet(@RequestBody CreateVisualizationModel model, Principal user)
      throws Exception
   {
      createVsService.createViewsheet(model, user);
   }

   private final CreateVsService createVsService;
}
