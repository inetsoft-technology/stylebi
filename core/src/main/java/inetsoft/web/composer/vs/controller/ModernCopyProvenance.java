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
package inetsoft.web.composer.vs.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;

import java.security.Principal;

/**
 * Resolves the original dashboard a modern copy was made from.
 */
public final class ModernCopyProvenance {
   private ModernCopyProvenance() {
   }

   /**
    * The original's path when it still exists and the principal can read it, else null.
    */
   public static String displayPath(String identifier, AssetRepository repository,
                                    Principal principal)
   {
      if(identifier == null || repository == null || principal == null) {
         return null;
      }

      try {
         AssetEntry entry = AssetEntry.createAssetEntry(identifier);

         if(entry == null || !repository.containsEntry(entry)) {
            return null;
         }

         repository.checkAssetPermission(principal, entry, ResourceAction.READ);
         return entry.getPath();
      }
      catch(Exception ex) {
         return null;
      }
   }
}
