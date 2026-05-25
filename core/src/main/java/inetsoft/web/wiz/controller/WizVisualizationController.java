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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wiz/visualization")
public class WizVisualizationController {
   public WizVisualizationController(WizVisualizationService wizVisualizationService) {
      this.wizVisualizationService = wizVisualizationService;
   }

   /**
    * Returns the folder tree under "Visualization Components" for the WIZ Save dialog.
    * Both folder nodes and viewsheet leaf nodes are included.
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
   public ResponseEntity<?> saveVisualization(
      @RequestBody WizVisualizationSaveEvent event, Principal principal)
   {
      try {
         WizVisualizationSaveResult result = wizVisualizationService.saveVisualization(event, principal);
         return ResponseEntity.ok(result);
      }
      catch(IllegalArgumentException e) {
         return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
      catch(Exception e) {
         LOG.error("Failed to save visualization", e);
         return ResponseEntity.internalServerError().body(Map.of("error", "An unexpected error occurred. Please try again."));
      }
   }

   @DeleteMapping(
      value = "/delete",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
   )
   public ResponseEntity<?> deleteVisualizations(@RequestBody List<String> identifiers, Principal principal)
   {
      if(identifiers == null || identifiers.isEmpty()) {
         return ResponseEntity.badRequest().body(Map.of("error", "identifiers are required"));
      }

      try {
         wizVisualizationService.deleteVisualizations(identifiers, principal);
         return ResponseEntity.ok(Map.of("deleted", identifiers.size()));
      }
      catch(Exception e) {
         LOG.error("Failed to delete visualizations", e);
         return ResponseEntity.internalServerError().body(Map.of("error", "An unexpected error occurred. Please try again."));
      }
   }

   private final WizVisualizationService wizVisualizationService;
   private static final Logger LOG = LoggerFactory.getLogger(WizVisualizationController.class);
}
