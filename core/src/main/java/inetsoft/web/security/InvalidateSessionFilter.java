/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.security;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.admin.security.SSOSettingsService;
import inetsoft.web.admin.security.SSOType;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that invalidates the session if the request principal object is not the
 * same as the active user
 */
@Component
public class InvalidateSessionFilter extends AbstractSecurityFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      HttpSession session = httpRequest.getSession(false);

      if(session != null && !isSSO() && !isPublicResource(httpRequest) &&
         !isPublicApi(httpRequest))
      {
         SRPrincipal principal =
            (SRPrincipal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal != null) {
            if(isSecurityEnabled()) {
               SecurityEngine securityEngine = SecurityEngine.getSecurity();

               if(securityEngine != null) {
                  if(!securityEngine.isActiveUser(principal)) {
                     session.invalidate();
                  }
               }
            }

            if(isApp(httpRequest) && principal.getProperty("curr_org_id") != null) {
               principal.setProperty("curr_org_id", null);
            }
         }
      }

      chain.doFilter(request, response);
   }

   @Autowired
   public void setSsoService(SSOSettingsService ssoService) {
      this.ssoService = ssoService;
   }

   private boolean isSSO() {
      return ssoService != null && ssoService.getActiveFilterType() != SSOType.NONE;
   }

   private SSOSettingsService ssoService;
}
