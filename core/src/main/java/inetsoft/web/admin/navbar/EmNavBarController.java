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
package inetsoft.web.admin.navbar;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.web.admin.security.SSOType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class EmNavBarController {
   @GetMapping("/api/em/navbar/get-navbar-model")
   public EmNavBarModel getNavBarModel(Principal principal) throws Exception {
      String logoutUri = SreeEnv.getProperty("sso.logout.url");
      PortalThemesManager manager = PortalThemesManager.getManager();
      boolean enterprise = LicenseManager.getInstance().isEnterprise();

      if(!SSOType.NONE.getName().equals(SreeEnv.getProperty("sso.protocol.type")) &&
         !StringUtils.isEmpty(logoutUri))
      {
         if(logoutUri.contains("?")) {
            logoutUri +=  "&fromEm=true";
         }
         else {
            logoutUri +=  "?fromEm=true";
         }
      }
      else {
         logoutUri = DEFAULT_LOGOUT_URI;
      }

      boolean ssoUser = principal instanceof XPrincipal &&
      !"true".equals(((XPrincipal) principal).getProperty("__internal__"));

      return new EmNavBarModel(logoutUri,
                               PortalThemesManager.CUSTOM_LOGO.equals(manager.getLogoStyle()),
                               enterprise, ssoUser);
   }

   @GetMapping("/api/em/navbar/organization")
   public String getCurrOrg(Principal principal) {
      return OrganizationManager.getCurrentOrgName();
   }

   @GetMapping("/api/em/navbar/isMultiTenant")
   public boolean isMultiTenant(Principal principal) {
      return SUtil.isMultiTenant();
   }

   @GetMapping("/api/em/navbar/isOrgAdminOnly")
   public boolean isOrgAdminOnly(Principal principal) {
      return OrganizationManager.getInstance().isOrgAdmin(principal) && !OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   @GetMapping("/api/em/navbar/isSiteAdmin")
   public boolean isSiteAdmin(Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      return OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   private static String DEFAULT_LOGOUT_URI = "../logout?fromEm=true";
}