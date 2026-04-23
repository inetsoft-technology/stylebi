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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.wiz.model.CloseViewsheetRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/wiz")
public class ViewsheetRuntimeController {

   public ViewsheetRuntimeController(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   /**
    * Opens a runtime viewsheet for the given asset entry identifier.
    *
    * @param identifier the asset entry identifier of the viewsheet
    * @param user       the current principal
    * @return the runtime ID of the opened viewsheet
    */
   @PostMapping(value = "/viewsheet/open", produces = MediaType.TEXT_PLAIN_VALUE)
   public String openViewsheet(@RequestParam("identifier") String identifier,
                               Principal user) throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      if(entry == null) {
         throw new IllegalArgumentException("Invalid viewsheet identifier: " + identifier);
      }

      return viewsheetService.openViewsheet(entry, user, true);
   }

   /**
    * Closes the runtime viewsheet identified by the given runtime ID.
    *
    * @param request body containing the runtimeId returned by {@link #openViewsheet}
    * @param user the current principal
    */
   @PostMapping("/viewsheet/close")
   public void closeViewsheet(@RequestBody CloseViewsheetRequest request,
                              Principal user) throws Exception
   {
      viewsheetService.closeViewsheet(request.getRuntimeId(), user);
   }

   private final ViewsheetService viewsheetService;
}
