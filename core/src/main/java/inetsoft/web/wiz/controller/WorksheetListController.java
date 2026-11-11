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

import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.sheet.ws.WorksheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Read-only worksheet discovery for the composer-chat MCP plugin's {@code list_worksheets} tool.
 *
 * <p>Unlike the admin-ai {@code /v1/admin/worksheets} endpoint this has no site-admin gate: it
 * lists what the calling user can read in their own organization (the shared tree plus their own
 * private tree). Every entry returned is one that {@code runQuery("ws:...")} and the attach path
 * accept for the same caller, so a listed worksheet is never refused afterwards.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class WorksheetListController {
   @Autowired
   public WorksheetListController(
      @Qualifier("adminSheetWorksheetService") WorksheetService worksheetService,
      AssetRepository assetRepository)
   {
      this.worksheetService = worksheetService;
      this.assetRepository = assetRepository;
   }

   @GetMapping(value = "/v1/worksheets", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<WorksheetListEntry> listWorksheets(Principal principal) throws Exception {
      return SheetListSupport.list(worksheetService.getWorksheets(principal).getSheets(), principal,
                                   assetRepository, WorksheetListEntry::new);
   }

   public record WorksheetListEntry(String path, String label, String scope, String owner) {
   }

   private final WorksheetService worksheetService;
   private final AssetRepository assetRepository;
}
