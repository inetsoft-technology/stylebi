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

import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.wiz.model.WizVisualizationSaveEvent;
import inetsoft.web.wiz.model.WizVisualizationSaveResult;
import inetsoft.web.wiz.service.WizVisualizationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/wiz/visualization")
public class WizVisualizationController {
   public WizVisualizationController(WizVisualizationService wizVisualizationService) {
      this.wizVisualizationService = wizVisualizationService;
   }

   /**
    * Returns the folder tree under "Visualization Components" for the WIZ Save dialog.
    * Only folder nodes are returned (no viewsheet leaves).
    */
   @GetMapping(value = "/tree", produces = MediaType.APPLICATION_JSON_VALUE)
   public TreeNodeModel getVisualizationFolderTree(Principal principal) throws Exception {
      return wizVisualizationService.getVisualizationFolderTree(principal);
   }

   /**
    * Saves a single assembly from the chat's shared ViewSheet into a dedicated ViewSheet
    * under the user-selected folder. Stores {@code conversationId} as an AssetEntry property.
    */
   @PostMapping(value = "/save", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<WizVisualizationSaveResult> saveVisualization(
      @RequestBody WizVisualizationSaveEvent event, Principal principal)
   {
      try {
         WizVisualizationSaveResult result = wizVisualizationService.saveVisualization(event, principal);
         return ResponseEntity.ok(result);
      }
      catch(IllegalArgumentException e) {
         return ResponseEntity.badRequest().build();
      }
      catch(Exception e) {
         return ResponseEntity.internalServerError().build();
      }
   }

   private final WizVisualizationService wizVisualizationService;
}
