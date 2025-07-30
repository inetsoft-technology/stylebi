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
package inetsoft.web.admin;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.*;
import inetsoft.sree.security.*;
import inetsoft.web.viewsheet.service.LinkUri;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import java.net.URLEncoder;
import java.security.Principal;

@Controller
public class AdminPageController {
   @Autowired
   public AdminPageController(SecurityEngine securityEngine) {
      this.securityEngine = securityEngine;
   }

   @GetMapping({
      "/em", "/em/", "/em/index.html", "/em/monitoring", "/em/monitoring/**",
      "/em/settings", "/em/settings/**", "/em/password", "/em/favorites",
      "/em/auditing", "/em/auditing/**"
   })
   public ModelAndView showAdminPage(HttpServletRequest request, HttpServletResponse response,
                                     Principal principal, @LinkUri String linkUri) throws Exception
   {
      String header = CacheControl.noCache()
         .cachePrivate()
         .mustRevalidate()
         .getHeaderValue();
      response.setHeader(HttpHeaders.CACHE_CONTROL, header);
      ModelAndView model;

      if(!securityEngine.checkPermission(
         principal, ResourceType.EM, "*", ResourceAction.ACCESS))
      {
         model = new ModelAndView("restricted");
         PortalThemesManager manager = PortalThemesManager.getManager();
         String redirectUri = LinkUriArgumentResolver.transformUri(request);
         String baseLink = SreeEnv.getProperty("sso.logout.url", linkUri + "logout");
         String paramAppend = baseLink.contains("?") ? "&" : "?";
         String logoutUrl =  baseLink + paramAppend +
            "fromEm=true&redirectUri=" + URLEncoder.encode(redirectUri, "UTF-8");
         model.addObject("logoutUrl", logoutUrl);
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         model.addObject("customLogo", manager.hasCustomLogo(orgId));
      }
      else {
         model = new ModelAndView("em/index");
      }

      CustomThemesManager themes = CustomThemesManager.getManager();
      boolean hasOrgTheme = false;
      boolean hasDarkEMTheme = false;

      if(OrganizationManager.getInstance().getCurrentOrgID() != null) {
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

         if(provider.getOrganization(orgId) != null &&
            provider.getOrganization(orgId).getTheme() != null &&
            provider.getOrganization(orgId).getTheme().length() != 0)
         {
            hasOrgTheme = true;

            hasDarkEMTheme = CustomThemesManager.getManager().getCustomThemes().stream()
               .filter(t -> t.getId().equals(provider.getOrganization(orgId).getTheme()))
               .findAny()
               .map(CustomTheme::isEMDark)
               .orElse(false);
         }
      }

      model.addObject("linkUri", linkUri);
      model.addObject("customTheme", themes.isCustomThemeApplied() || hasOrgTheme);
      model.addObject("darkTheme", themes.isEMDarkTheme() || hasDarkEMTheme);
      model.addObject("scriptThemeCssPath", themes.getScriptThemeCssPath(false));
      return model;
   }

   private final SecurityEngine securityEngine;
}
