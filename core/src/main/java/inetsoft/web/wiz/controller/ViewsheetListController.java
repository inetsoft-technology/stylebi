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
import inetsoft.web.admin.sheet.vs.ViewsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Read-only viewsheet (dashboard) discovery for the composer-chat MCP plugin's
 * {@code list_viewsheets} tool, the counterpart of {@link WorksheetListController}.
 *
 * <p>It has no site-admin gate: it lists what the calling user can read in their own organization
 * (the shared tree plus their own private tree), with the same per-asset READ walk, so a listed
 * viewsheet is one the {@code open_viewsheet} tool will accept for the same caller.</p>
 */
@RestController
@RequestMapping("/api/wiz")
public class ViewsheetListController {
   @Autowired
   public ViewsheetListController(
      @Qualifier("adminSheetViewsheetService") ViewsheetService viewsheetService,
      AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
   }

   @GetMapping(value = "/v1/viewsheets", produces = MediaType.APPLICATION_JSON_VALUE)
   public List<ViewsheetListEntry> listViewsheets(Principal principal) throws Exception {
      return SheetListSupport.list(viewsheetService.getViewsheets(principal).getSheets(), principal,
                                   assetRepository, ViewsheetListEntry::new);
   }

   public record ViewsheetListEntry(String path, String label, String scope, String owner) {
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
}
