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
package inetsoft.web.wiz;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;

import java.security.Principal;

public final class AppDomainUtils {
   public static OrganizationDomains getAppDomains(Principal user) {
      String value = SreeEnv.getProperty(getOrgAppDomainPropKey(user), false, true);

      if(Tool.isEmptyString(value)) {
         return null;
      }

      OrganizationDomains domains = new OrganizationDomains();
      domains.parseFromProperty(value);
      return domains;
   }

   public static void setAppDomains(OrganizationDomains appDomains, Principal user) {
      OrganizationManager orgManager = OrganizationManager.getInstance();

      if(!orgManager.isSiteAdmin(user) && !orgManager.isOrgAdmin(user)) {
         throw new SecurityException("Access denied to app domains configuration.");
      }

      SreeEnv.setProperty(getOrgAppDomainPropKey(user), appDomains.toString(), true);
   }

   public static String getAppDomain(Principal user) {
      OrganizationDomains domains = getAppDomains(user);
      return domains == null ? null : domains.getId();
   }

   private static String getOrgAppDomainPropKey(Principal user) {
      if(user instanceof XPrincipal principal && principal.getOrgId() != null) {
         return Tool.buildString(APP_DOMAIN, ".", principal.getOrgId());
      }

      return APP_DOMAIN;
   }

   private AppDomainUtils() {
   }

   private static final String APP_DOMAIN = "app.domains";
}
