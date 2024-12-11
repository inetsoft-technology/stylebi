/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.analytic.composition;

import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.security.Principal;

public class SheetLibraryEngine implements SheetLibraryService {
   @Override
   public AssetEntry getTemporaryAssetEntry(Principal user, AssetEntry.Type type) {
      Catalog catalog = Catalog.getCatalog(user);
      String prefix = catalog.getString("Untitled");

      synchronized(this) {
         prefix = prefix + "-" + (counter++);
      }

      IdentityID identityID = null;
      int scope = AssetRepository.TEMPORARY_SCOPE;

      if(Tool.equals(OrganizationManager.getInstance().getCurrentOrgID(),
                     Organization.getSelfOrganizationID()))
      {
         identityID = IdentityID.getIdentityIDFromKey(user.getName());
         scope = AssetRepository.USER_SCOPE;
      }

      return new AssetEntry(scope, type, prefix, identityID);
   }

   private static int counter = 1;
}