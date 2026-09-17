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
package inetsoft.web.admin.sheet;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.web.security.auth.UnauthorizedAccessException;

import java.security.Principal;

/**
 * Validates that the caller may act on behalf of the given organization id. Only a site
 * administrator may specify an organization other than their own; in a single-organization
 * (community) deployment this is always a no-op since every identity belongs to the same
 * default organization.
 */
public final class OrganizationAccess {
   private OrganizationAccess() {
   }

   public static void check(String organizationid, Principal principal)
      throws UnauthorizedAccessException
   {
      if(organizationid != null && !organizationid.isEmpty() &&
         principal instanceof XPrincipal xp &&
         !OrganizationManager.getInstance().isSiteAdmin(principal) &&
         !organizationid.equals(xp.getOrgId()))
      {
         throw new UnauthorizedAccessException(organizationid);
      }
   }
}
