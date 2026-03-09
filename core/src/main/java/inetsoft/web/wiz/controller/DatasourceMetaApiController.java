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

import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.web.composer.model.*;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.model.DatabaseTableMeta;
import inetsoft.web.wiz.model.WorksheetMeta;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.web.wiz.service.MetadataApiService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/wiz")
public class DatasourceMetaApiController {
   public DatasourceMetaApiController(MetadataApiService metadataService) {
      this.metadataService = metadataService;
   }

   @PostMapping("/datasource/table/meta")
   public DatabaseTableMeta getDatabaseTableMeta(
      @RequestBody GetDatabaseTableMetaRequest data)
      throws Exception
   {
      return metadataService.getMetaData(data);
   }

   @GetMapping("/ws/meta/{id}")
   public WorksheetMeta getWorksheetMeta(
      @PathVariable("id") String worksheetId,
      Principal principal)  throws Exception
   {
      return metadataService.getWorksheetMetaData(WizUtil.decodeId(worksheetId), (XPrincipal) principal);
   }

   /**
    * Gets the child nodes of the specified parent node.
    *
    * @return the child nodes.
    */
   @PostMapping("/datasets/asset_tree")
   public TreeNodeModel getNodes(
      @RequestBody(required = false) LoadAssetTreeNodesEvent event,
      Principal principal)
      throws Exception
   {
      if(event == null) {
         event = LoadAssetTreeNodesEvent.builder().build();
      }

      return metadataService.getNodes(event, principal).treeNodeModel();
   }

   private final MetadataApiService metadataService;
}
