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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

/**
 * Read/permission/dependency wrapper over the product's own {@code LibManager} Script Library
 * primitive -- talks to {@code LibManager} directly, never to {@code RepositoryScriptController}
 * (session-cookie/CSRF EM SPA endpoint) or {@code inetsoft.web.wiz.controller.ScriptLibraryController}
 * (composer-chat's own READ/WRITE-permission-scoped surface, wrong permission model for a
 * site-administrator caller) -- the same "reuse the real service, not the internal controller"
 * rule every other wiz admin-chat area follows.
 *
 * <p>{@link #isVisible}/{@link #requirePermission} check {@code ADMIN} on {@code
 * ResourceType.SCRIPT}, the SAME resource type/action pair {@code RepositoryScriptController}
 * itself uses for its own rename/description edit -- an admin-chat caller never sees or touches a
 * script it could not already administer through Enterprise Manager's own Script Library settings
 * page.
 */
@Component
public class ScriptLibraryService {
   @Autowired
   public ScriptLibraryService(LibManagerProvider libManagerProvider, SecurityEngine securityEngine) {
      this.libManagerProvider = libManagerProvider;
      this.securityEngine = securityEngine;
   }

   /** Every Script Library entry the caller holds {@code ADMIN} on -- audit scripts
    * ({@code LibManager.isAuditScript}) are never listed, matching composer-chat's own
    * {@code ScriptLibraryController.list} precedent: they are internal, not user-authored
    * functions. */
   public List<ScriptLibraryEntryProjection> listEntries(Principal user) {
      LibManager lib = libManagerProvider.getManager(user);
      List<ScriptLibraryEntryProjection> result = new ArrayList<>();
      Enumeration<String> names = lib.getScripts();

      while(names.hasMoreElements()) {
         String name = names.nextElement();

         if(lib.isAuditScript(name) || !isVisible(name, user)) {
            continue;
         }

         result.add(new ScriptLibraryEntryProjection(name, lib.getScriptComment(name)));
      }

      result.sort(Comparator.comparing(ScriptLibraryEntryProjection::name));
      return result;
   }

   /** {@code Optional.empty()} both when {@code name} does not exist AND when it exists but is
    * not visible to {@code user} -- same posture {@code RecycleBinService.getEntry} documents for
    * its own area. */
   public Optional<ScriptLibraryEntryDetail> getEntry(String name, Principal user) {
      LibManager lib = libManagerProvider.getManager(user);

      if(name == null || lib.getScript(name) == null || !isVisible(name, user)) {
         return Optional.empty();
      }

      return Optional.of(project(lib, name));
   }

   /**
    * @throws MissingResourceException if {@code name} does not exist, or exists but is not
    *         visible to {@code user}.
    */
   public ScriptLibraryEntryDetail requireEntry(String name, Principal user)
      throws MissingResourceException
   {
      return getEntry(name, user).orElseThrow(() -> new MissingResourceException(
         "name: no script library entry named \"" + name + "\" -- it may not exist, may already " +
         "have been deleted/renamed, or you may not have permission to see it"));
   }

   /** Whether {@code name} already exists, regardless of visibility -- used by {@code create}'s
    * own collision check, which must refuse a name collision even against a script the caller
    * cannot see, the same "collision is checked before visibility" discipline schedule-task-folder
    * create/rename already uses. */
   public boolean exists(String name, Principal user) {
      return libManagerProvider.getManager(user).getScript(name) != null;
   }

   /**
    * Names of every asset that would break if {@code name} were deleted right now -- mirrors
    * {@code inetsoft.web.wiz.controller.ScriptLibraryController.delete}'s own dependency check
    * exactly (same {@code COMPONENT_SCOPE} asset entry, same {@code DependencyTransformer} call),
    * replicated here because this area calls {@code LibManager} directly rather than through that
    * controller.
    */
   public List<String> dependents(String name) {
      List<AssetObject> deps = DependencyTransformer.getDependencies(scriptEntry(name).toIdentifier());
      List<String> names = new ArrayList<>();

      for(AssetObject dep : deps) {
         names.add(dep instanceof AssetEntry entry ? entry.getDescription() : dep.toString());
      }

      return names;
   }

   /**
    * {@code COMPONENT_SCOPE}, not {@code GLOBAL_SCOPE} -- see
    * {@code inetsoft.web.wiz.controller.ScriptLibraryController.scriptEntry}'s own javadoc for why
    * ({@code LocalDependencyHandler}/{@code RemoveAssetController} both key a script's dependency
    * records under {@code COMPONENT_SCOPE}).
    */
   static AssetEntry scriptEntry(String name) {
      return new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT, name, null);
   }

   ScriptLibraryEntryDetail project(LibManager lib, String name) {
      return new ScriptLibraryEntryDetail(name, lib.getScriptComment(name), lib.getScript(name));
   }

   boolean isVisible(String name, Principal user) {
      return hasPermission(name, user, ResourceAction.ADMIN);
   }

   void requirePermission(String name, Principal user, ResourceAction action) {
      if(!hasPermission(name, user, action)) {
         throw new SecurityException("No " + action + " permission on script library entry \"" +
            name + "\".");
      }
   }

   private boolean hasPermission(String name, Principal user, ResourceAction action) {
      try {
         return securityEngine.checkPermission(user, ResourceType.SCRIPT, name, action);
      }
      catch(Exception e) {
         return false;
      }
   }

   final SecurityEngine securityEngine;
   private final LibManagerProvider libManagerProvider;
}
