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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.AdminIdentityRefs;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * The {@code get_viewsheet_folder} read (01-spec.md section 3) -- a small, additive new method,
 * not on {@code ViewsheetService} itself, backed directly by already-public
 * {@link RepletRegistry#isFolder}/{@link RepletRegistry#getFolderAlias}/
 * {@link RepletRegistry#getFolderDescription}. No change to {@code RepletRegistry} itself.
 *
 * <p><b>Build-time finding, beyond what 01-spec.md's own text traces</b>: {@code
 * ViewsheetService.addFolder} auto-prepends {@code "My Dashboards/"} to an owner-scoped
 * folder's path before it ever reaches the registry (confirmed by reading {@code addFolder}
 * directly, {@code ViewsheetService.java} lines ~868-870) -- but {@code removeFolder}/{@code
 * renameFolder} do NOT perform this same prepend; they only strip a single leading {@code "/"}.
 * A caller that captured a path from {@code addFolder}'s own resulting full path (or from this
 * class's own {@link #getFolder}) and passed it verbatim to {@code removeFolder}/{@code
 * renameFolder} would already work correctly (since that path already carries the prefix) -- but a
 * caller that instead assembled an owner-scoped path by hand, the same way it would for {@code
 * addFolder}'s {@code parentFolder} argument (i.e. WITHOUT the prefix), would silently target a
 * registry key that was never created, because {@code removeFolder}/{@code renameFolder} apply no
 * equivalent normalization of their own. This area's own service normalizes EVERY folder path the
 * same way for all four folder-touching operations (create/delete/rename/get), so a path round-
 * trips correctly regardless of which operation produced or consumes it -- closing a real,
 * source-verified cross-call inconsistency in the wrapped Public API, per repo CLAUDE.md's
 * "tool-misuse is a plugin gap" rule.
 */
@Component
public class ViewsheetFolderService {
   @Autowired
   public ViewsheetFolderService(RepletRegistryManager repletRegistryManager,
                                 RepletRegistryService repletRegistryService)
   {
      this.repletRegistryManager = repletRegistryManager;
      this.repletRegistryService = repletRegistryService;
   }

   /**
    * Normalizes a caller-supplied folder path the same way {@code
    * ViewsheetService.addFolder}/{@code removeFolder}/{@code renameFolder} resolve one between
    * them (see class javadoc): strips one leading {@code "/"}, then -- when {@code owner} is
    * non-null and the path does not already carry it -- prepends {@code "My Dashboards/"}. Applied
    * uniformly by {@link #getFolder} and by {@link ViewsheetChangePlanService}/
    * {@link ViewsheetChangesetApplyService} before every {@code removeFolder}/{@code renameFolder}
    * call, so a path captured from one operation always resolves correctly in another.
    */
   public static String normalizeFolderPath(String rawPath, IdentityID owner) {
      String path = rawPath == null ? "" : rawPath;

      if(path.startsWith("/")) {
         path = path.substring(1);
      }

      if(owner != null && !path.equals(Tool.MY_DASHBOARD) &&
         !path.startsWith(Tool.MY_DASHBOARD + "/"))
      {
         path = Tool.MY_DASHBOARD + "/" + path;
      }

      return path;
   }

   /**
    * Replicates {@code ViewsheetService.addFolder}'s own parent-path computation (trailing
    * slash normalization, then the same {@code "My Dashboards/"} prepend for an owner-scoped
    * folder) so the FULL resulting path can be predicted and hashed/displayed at preview time,
    * before the folder is actually created. Package-visible so {@link ViewsheetChangePlanService}/
    * {@link ViewsheetChangesetApplyService} share this one computation.
    */
   static String computeFolderFullPath(String parentFolder, String folderName, IdentityID owner) {
      String parentPath;

      if(parentFolder == null || parentFolder.isEmpty() || "/".equals(parentFolder)) {
         parentPath = "";
      }
      else if(parentFolder.endsWith("/")) {
         parentPath = parentFolder;
      }
      else {
         parentPath = parentFolder + "/";
      }

      if(owner != null && !parentPath.startsWith(Tool.MY_DASHBOARD + "/")) {
         parentPath = Tool.MY_DASHBOARD + "/" + parentPath;
      }

      return parentPath + folderName;
   }

   /** {@code null} when {@code owner} is not supplied (global scope), otherwise {@code
    * IdentityID.convertToKey()} -- the stable, parseable string used in the {@code path|owner}
    * composite key (01-spec.md section 5). */
   public static String ownerKey(IdentityID owner) {
      return owner == null ? null : owner.convertToKey();
   }

   /**
    * Parses an {@code owner} through {@link AdminIdentityRefs#parse}: a bare name, {@code
    * name:orgId}, the storage key {@code name~;~orgId} (which plans and reads echo back), or the
    * caret label {@code name^orgId} (Bug #78866). A blank owner is {@code null} (global scope).
    * <b>Not {@code IdentityID.getIdentityIDFromKey} for a bare name</b>: that defaults the org from
    * {@code ThreadContext.getPrincipal()} (unset outside a live request thread). Unlike {@code
    * PermissionChangePlanService.requireIdentityId}, an explicit org here is NOT restricted to the
    * caller's own org -- {@code OrganizationAccess.check(owner.getOrgID(), principal)} already
    * exists at the wrapped {@code ViewsheetService} tier for exactly this case (section 4a), and
    * this area's only caller population is always a site administrator -- but it must be an
    * existing org. The owner itself is not required to exist in the security provider (an
    * SSO-only user has a real per-user registry without a provider record); plans warn instead,
    * see {@code AdminIdentityRefs#missingOwnerWarning}. Apply re-parses through this same method.
    */
   public static IdentityID parseOwner(String ownerRaw) {
      return AdminIdentityRefs.parseOwner(ownerRaw);
   }

   /**
    * Resolves {@code found}/{@code alias}/{@code description} for a (normalized) folder path.
    * {@code found: false} is a normal answer, not an exception.
    */
   public GetViewsheetFolderResult getFolder(String normalizedPath, IdentityID owner)
      throws Exception
   {
      RepletRegistry registry = repletRegistryManager.getRegistry(owner);

      if(!registry.isFolder(normalizedPath)) {
         return new GetViewsheetFolderResult(false, normalizedPath, ownerKey(owner), null, null);
      }

      return new GetViewsheetFolderResult(true, normalizedPath, ownerKey(owner),
         registry.getFolderAlias(normalizedPath), registry.getFolderDescription(normalizedPath));
   }

   /**
    * Updates a folder's alias/description without moving it -- {@code RepletRegistryService
    * .updateRepositoryFolder}'s own {@code oldPath.equals(newPath)} short-circuit (confirmed by
    * reading its source, `RepletRegistryService.java:137`) skips the entire path-changing block
    * when the same (normalized) path is passed for both old and new, making this a zero-move-
    * risk, field-level write (Track B/03-reconcile.md's central finding) -- the same combined
    * call EM's own {@code RepositoryFolderService.applySettings} already uses.
    *
    * @param normalizedPath the folder's (already-normalized) registry path.
    * @param owner          the owner of the folder for user-scoped folders or {@code null} for
    *                       global folders.
    * @param alias          the new display alias, or {@code null}/empty to clear it.
    * @param description    the new description, or {@code null}/empty to clear it.
    * @param principal      a principal that identifies the remote user.
    *
    * @throws Exception if the folder's metadata could not be updated.
    */
   public void updateFolderMetadata(String normalizedPath, IdentityID owner, String alias,
                                    String description, Principal principal) throws Exception
   {
      repletRegistryService.updateRepositoryFolder(
         normalizedPath, owner, normalizedPath, owner, alias, description, false, null, null,
         principal);
   }

   private final RepletRegistryManager repletRegistryManager;
   private final RepletRegistryService repletRegistryService;
}
