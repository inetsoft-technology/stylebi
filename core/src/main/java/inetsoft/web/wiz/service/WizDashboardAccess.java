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
package inetsoft.web.wiz.service;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.pairing.PairingException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Locale;

/**
 * Permission re-checks and dashboard lookup shared by the wiz email, share and schedule
 * endpoints that work without a paired Composer session (Redmine #82413).
 *
 * <p>{@code WizDelegatingCheckPermissionStrategy} approves every check for a wiz principal, so a
 * service that is handed the wiz principal approves the Email action, the Schedule tab, the share
 * targets and a read of any dashboard for any user. Every check here therefore runs on the copy
 * {@link WizUtil#toolbarCheckPrincipal} returns (same user, roles, groups and organization, "wiz"
 * tag removed), so the real user's grants decide. The work itself still runs as the wiz
 * principal.</p>
 */
@Component
public class WizDashboardAccess {
   public WizDashboardAccess(AssetRepository assetRepository, SecurityEngine securityEngine) {
      this.assetRepository = assetRepository;
      this.securityEngine = securityEngine;
   }

   /**
    * Resolves a dashboard the caller can read.
    *
    * @param path  the dashboard path, e.g. {@code "Sales/Overview"}.
    * @param scope {@code "global"} (the default) or {@code "user"} (the caller's own tree).
    *
    * @throws IllegalArgumentException if the path is malformed, or the dashboard does not exist or
    *                                  the caller cannot read it (the two are not told apart).
    */
   public AssetEntry requireReadableDashboard(String path, String scope, Principal user)
      throws Exception
   {
      if(path == null || path.isBlank()) {
         throw new IllegalArgumentException(
            "'path' is required: name the saved dashboard (for example \"Sales/Overview\"); " +
            "list_viewsheets returns the available paths.");
      }

      try {
         WizUtil.requireNoCaret(path, "path");
      }
      catch(PairingException e) {
         throw new IllegalArgumentException(e.getMessage(), e);
      }

      int assetScope = resolveScope(scope);
      String assetPath = normalizePath(path);
      IdentityID caller = caller(user);
      AssetEntry entry = new AssetEntry(
         assetScope, AssetEntry.Type.VIEWSHEET, assetPath,
         assetScope == AssetRepository.USER_SCOPE ? caller : null, caller.orgID);
      String notFound = "No dashboard \"" + assetPath + "\" (scope " + scopeName(assetScope) +
         ") that you can read; list_viewsheets returns the available paths and scopes.";

      try {
         if(!assetRepository.containsEntry(entry)) {
            throw new IllegalArgumentException(notFound);
         }

         assetRepository.checkAssetPermission(
            WizUtil.toolbarCheckPrincipal(user), entry, ResourceAction.READ);
      }
      catch(IllegalArgumentException e) {
         throw e;
      }
      catch(Exception e) {
         // an unreadable dashboard is reported like a missing one
         throw new IllegalArgumentException(notFound, e);
      }

      return entry;
   }

   /** Requires a Viewer toolbar action (e.g. {@code "Email"}) for the real user. */
   public void requireToolbarAction(Principal user, String action) {
      if(!allowed(WizUtil.toolbarCheckPrincipal(user),
                                         ResourceType.VIEWSHEET_TOOLBAR_ACTION, action,
                                         ResourceAction.READ))
      {
         throw new SecurityException(
            "You do not have permission for the Viewer \"" + action + "\" action.");
      }
   }

   /** Whether the real user holds a Viewer toolbar action. */
   public boolean hasToolbarAction(Principal user, String action) {
      return allowed(WizUtil.toolbarCheckPrincipal(user),
                                            ResourceType.VIEWSHEET_TOOLBAR_ACTION, action,
                                            ResourceAction.READ);
   }

   /** Requires the Schedule portal tab and the scheduler access right for the real user. */
   public void requireScheduleAccess(Principal user) {
      Principal real = WizUtil.toolbarCheckPrincipal(user);

      if(!allowed(real, ResourceType.PORTAL_TAB, "Schedule",
                                         ResourceAction.READ) ||
         !allowed(real, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS))
      {
         throw new SecurityException("You do not have permission to use the Schedule tab.");
      }
   }

   /** Requires the Share right of the given target for the real user. */
   public void requireShareTarget(Principal user, String target) {
      if(!allowed(WizUtil.toolbarCheckPrincipal(user), ResourceType.SHARE,
                                         target, ResourceAction.ACCESS))
      {
         throw new SecurityException(
            "You do not have permission to share a dashboard with \"" + target + "\".");
      }
   }

   private boolean allowed(Principal principal, ResourceType type, String resource,
                           ResourceAction action)
   {
      try {
         return securityEngine.checkPermission(principal, type, resource, action);
      }
      catch(inetsoft.sree.security.SecurityException e) {
         // a check that cannot be evaluated is a refusal
         return false;
      }
   }

   /** The real user's identity; never read from the request. */
   public static IdentityID caller(Principal user) {
      IdentityID id = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      if(id == null) {
         throw new IllegalArgumentException("Cannot resolve the calling user's identity.");
      }

      return id;
   }

   /**
    * The principal the real user's grants are evaluated for, with the wiz tag removed and the
    * login check skipped, for code that checks permissions itself (see {@link
    * WizUtil#toolbarCheckPrincipal}). Never use it for the work, only for checks.
    */
   public static Principal realUser(Principal user) {
      return WizUtil.toolbarCheckPrincipal(user);
   }

   private static int resolveScope(String scope) {
      if(scope == null || scope.isBlank()) {
         return AssetRepository.GLOBAL_SCOPE;
      }

      try {
         return WizUtil.resolveAssetScope(scope);
      }
      catch(Exception e) {
         throw new IllegalArgumentException(e.getMessage(), e);
      }
   }

   private static String scopeName(int scope) {
      return scope == AssetRepository.USER_SCOPE ? "user" : "global";
   }

   static String normalizePath(String path) {
      String normalized = path.trim().replace('\\', '/');

      while(normalized.startsWith("/")) {
         normalized = normalized.substring(1);
      }

      for(String segment : normalized.split("/")) {
         if("..".equals(segment)) {
            throw new IllegalArgumentException("'path' must not contain '..' segments.");
         }
      }

      if(normalized.isEmpty()) {
         throw new IllegalArgumentException("'path' must name a dashboard, not the root.");
      }

      return normalized;
   }

   static String lower(String value) {
      return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
   }

   private final AssetRepository assetRepository;
   private final SecurityEngine securityEngine;
}
