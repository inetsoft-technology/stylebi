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
package inetsoft.web.admin.content.repository;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;

import java.security.Principal;

/**
 * Guards the EM content-repository entry points that take a client-supplied owner.
 * {@code RepletRegistryManager.getRegistry(IdentityID)} and
 * {@code DashboardRegistryManager.getRegistry(IdentityID)} load the registry stored under the
 * owner's organization, and the resource permission checks on these paths do not stop a foreign
 * owner (any REPORT/ASSET check on a "My Dashboards/..." path only requires MY_DASHBOARDS READ).
 * Without this check a user of one organization could read or change another organization's
 * user folders and dashboards.
 * <p>
 * The check belongs at the entry points and not in the registry managers, which are legitimately
 * called with owners of other organizations by organization rename and clone. It is public for
 * the repository export and the schedule backup action, which build assets from a client-supplied
 * owner in {@code inetsoft.web.admin.deploy.DeployService}.
 */
public final class RepositoryOwnerOrgCheck {
   private RepositoryOwnerOrgCheck() {
   }

   /**
    * Rejects an owner from another organization unless the caller is a site administrator.
    * The caller's own identity and an owner in the caller's organization (compared ignoring case)
    * are allowed. An owner without an organization is treated as belonging to the caller's
    * organization, so it is still subject to the other checks here.
    *
    * @param owner     the client-supplied owner, may be {@code null}.
    * @param principal the caller.
    *
    * @throws MessageException if the owner belongs to another organization or its name or
    *                          organization contains a parent path segment.
    */
   public static void checkOwnerOrg(IdentityID owner, Principal principal) {
      if(owner == null) {
         return;
      }

      // the owner name and org are used as path segments of the registry storage location
      if(hasParentSegment(owner.name) || hasParentSegment(owner.orgID)) {
         throw noPermission(owner);
      }

      if(principal != null && owner.equals(IdentityID.getIdentityIDFromKey(principal.getName()))) {
         return;
      }

      String callerOrg = principal instanceof XPrincipal xp ? xp.getOrgId() : null;
      // an owner without an organization is treated as belonging to the caller's organization
      String ownerOrg = Tool.isEmptyString(owner.orgID) ? callerOrg : owner.orgID;

      if(ownerOrg == null ? callerOrg == null : ownerOrg.equalsIgnoreCase(callerOrg)) {
         return;
      }

      if(principal instanceof XPrincipal && OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return;
      }

      throw noPermission(owner);
   }

   private static boolean hasParentSegment(String value) {
      if(value == null) {
         return false;
      }

      for(String segment : value.split("[/\\\\]", -1)) {
         if("..".equals(segment)) {
            return true;
         }
      }

      return false;
   }

   private static MessageException noPermission(IdentityID owner) {
      return new MessageException(Catalog.getCatalog().getString(
         "em.common.security.no.permission", owner.getName()));
   }
}
