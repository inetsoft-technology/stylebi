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

package inetsoft.web.composer.wiz.controller;

import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.wiz.service.VisualizationService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class VisualizationController {
   public VisualizationController(VisualizationService visualizationService) {
      this.visualizationService = visualizationService;
   }

   @GetMapping(value = "/api/composer/wiz/visualizations")
   public TreeNodeModel getVisualizations(
      @RequestParam("runtimeId")
      @Parameter(
         name = "runtimeId",
         description = "The runtime ID of the viewsheet.",
         in = ParameterIn.QUERY,
         required = true
      )
      String runtimeId,
      Principal principal) throws Exception
   {
      return visualizationService.getVisualizations(runtimeId, principal);
   }

   private final VisualizationService visualizationService;
}
