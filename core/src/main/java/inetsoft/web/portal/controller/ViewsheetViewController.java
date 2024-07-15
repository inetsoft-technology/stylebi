/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.security.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;

/**
 * Controller that serves viewsheets using RESTful paths instead of query parameters.
 *
 * @since 12.3
 */
@Controller
public class ViewsheetViewController {
   /**
    * Shows a global viewsheet.
    *
    * @param path the path to the viewsheet.
    *
    * @return the view name for the viewsheet.
    */
   @RequestMapping(value = "/viewsheets/global/**", method = RequestMethod.GET)
   @Secured(@RequiredPermission(
      type = AssetEntry.Type.VIEWSHEET,
      scope = AssetRepository.GLOBAL_SCOPE
   ))
   public String showGlobalViewsheet(@RemainingPath @PermissionPath String path)
      throws Exception
   {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
      return "forward:/app/viewer/view?identifier=" +
         URLEncoder.encode(entry.toIdentifier(), "UTF-8");
   }

   /**
    * Shows a user's viewsheet.
    *
    * @param owner the owner of the viewsheet.
    * @param path  the path to the viewsheet.
    *
    * @return the view name for the viewsheet.
    */
   @RequestMapping(value = "/viewsheets/user/{owner}/**", method = RequestMethod.GET)
   @Secured(@RequiredPermission(
      type = AssetEntry.Type.VIEWSHEET,
      scope = AssetRepository.USER_SCOPE
   ))
   public String showUserViewsheet(@PathVariable("owner") @PermissionOwner String owner,
                                   @RemainingPath @PermissionPath String path)
      throws Exception
   {
      IdentityID ownerId = IdentityID.getIdentityIDFromKey(owner);
      AssetEntry entry = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, path, ownerId);
      return "forward:/app/viewer/view?identifier=" +
         URLEncoder.encode(entry.toIdentifier(), "UTF-8");
   }
}
