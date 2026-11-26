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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.MessageException;
import inetsoft.web.wiz.viewsheet.AssemblyHyperlinkService;

import java.security.Principal;

/**
 * Resolves the target of a logical model attribute's viewsheet auto drill to the asset
 * identifier the runtime opens it by. The Portal stores the identifier a user picked from the
 * repository tree; a drill whose link is a plain path cannot be opened by the viewer, so the
 * data model bridge resolves a path the same way {@code set_hyperlink} does and stores the
 * identifier. It also answers what the validator needs to resolve the worksheet of a drill's
 * query selection.
 */
final class WizDrillLinks {
   private WizDrillLinks() {
   }

   /**
    * The asset identifier of the viewsheet {@code link} names (a repository path or an asset
    * identifier) that {@code principal} may read, or {@code null} when there is none.
    */
   static String resolve(String link, Principal principal) {
      try {
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         boolean identifier = link.indexOf('^') >= 0;
         return AssemblyHyperlinkService.resolveTargetId(
            identifier ? null : link, identifier ? link : null, repository, principal, false);
      }
      catch(IllegalArgumentException e) {
         return null;
      }
      catch(RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         throw new IllegalStateException("The viewsheet \"" + link + "\" could not be checked: " +
                                            e.getMessage(), e);
      }
   }

   /**
    * What the validator asks of the repository to resolve the worksheet a drill query names: who
    * the caller is (the owner of a user-scope worksheet) and whether a worksheet exists and may
    * be read by the caller.
    */
   interface Worksheets {
      IdentityID caller();

      boolean readable(AssetEntry entry);
   }

   /** The repository's answers for {@code principal}. */
   static Worksheets worksheets(Principal principal) {
      return new Worksheets() {
         @Override
         public IdentityID caller() {
            return IdentityID.getIdentityIDFromKey(principal.getName());
         }

         @Override
         public boolean readable(AssetEntry entry) {
            try {
               AssetRepository repository = AssetUtil.getAssetRepository(false);

               // another user's private worksheet counts as unreadable for every caller, as the
               // viewsheet drill link does
               if(entry.getScope() == AssetRepository.USER_SCOPE &&
                  !caller().equals(entry.getUser()))
               {
                  return false;
               }

               if(!repository.containsEntry(entry)) {
                  return false;
               }

               repository.checkAssetPermission(principal, entry, ResourceAction.READ, true);
               return true;
            }
            catch(MessageException e) {
               return false;
            }
            catch(RuntimeException e) {
               throw e;
            }
            catch(Exception e) {
               throw new IllegalStateException("The worksheet \"" + entry.getPath() +
                                                  "\" could not be checked: " + e.getMessage(), e);
            }
         }
      };
   }
}
