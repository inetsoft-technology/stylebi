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
package inetsoft.web.security;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.commons.lang.StringUtils;
import org.apache.hc.core5.net.InetAddressUtils;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Filter that redirects the user to the default login page if they try to access a
 * restricted page and have not been authenticated.
 */
public class DefaultAuthorizationFilter extends AbstractSecurityFilter {
   public DefaultAuthorizationFilter() {
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {
      boolean unauthorized = false;
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      SecurityEngine engine = getSecurityEngine();
      SecurityProvider provider = getSecurityProvider();

      SRPrincipal principal = null;
      HttpSession session = httpRequest.getSession(false);

      if(session != null) {
         principal = (SRPrincipal) SUtil.getPrincipal(httpRequest);
      }

      if(!provider.getAuthenticationProvider().isVirtual() && !isPublicResource(httpRequest) &&
         !isPublicApi(httpRequest) && !isTeamWebsocketEndpoint(httpRequest))
      {
         Cookie[] cookies = ((HttpServletRequest) request).getCookies();
         String recordedOrgID = cookies == null ? null :
            Arrays.stream(cookies).filter(c -> c.getName().equals(ORG_COOKIE))
               .map(Cookie::getValue).findFirst().orElse(null);
         recordedOrgID = recordedOrgID == null ? SUtil.getLoginOrganization(httpRequest) : recordedOrgID;
         recordedOrgID = recordedOrgID == null ? getCookieRecordedOrgID((HttpServletRequest) request) : recordedOrgID;

         if((principal == null || principal.getName().equals(XPrincipal.ANONYMOUS)) &&
            // if there is a user explicitly named "anonymous", it is an allowed guest login
            (engine == null || !engine.containsAnonymous(recordedOrgID)))
         {
            unauthorized = true;
         }
      }
      else if(isEnterpriseManager(httpRequest)) {
         if(principal == null || principal.getName().equals(XPrincipal.ANONYMOUS) ||
            !provider.checkPermission(principal, ResourceType.EM, "*", ResourceAction.ACCESS))
         {
            unauthorized = true;
         }
      }

      if(unauthorized) {
         if(shouldSendAuthenticationRedirect(httpRequest, (HttpServletResponse) response)) {
            String orgID = SUtil.getLoginOrganization(httpRequest);
            String requestedUrl = LinkUriArgumentResolver.transformUri(httpRequest);
            String sep = requestedUrl.contains("?") ? "&" : "?";
            String queryString = httpRequest.getQueryString() != null ?
               sep + httpRequest.getQueryString() : "";

            String redirectUrl = LinkUriArgumentResolver.getLinkUri(httpRequest) + LOGIN_PAGE +
               "?requestedUrl=" +
               URLEncoder.encode(requestedUrl + queryString, StandardCharsets.UTF_8);

            if(orgID == null) {
               Cookie[] cookies = httpRequest.getCookies();

               if(cookies != null) {
                  for(Cookie cookie : cookies) {
                     if(cookie.getName().equals(ORG_COOKIE)) {
                        // clear any existing cookie
                        Cookie cleared = (Cookie) cookie.clone();
                        cleared.setValue("");
                        cleared.setMaxAge(0);
                        ((HttpServletResponse) response).addCookie(cleared);
                        break;
                     }
                  }
               }
            }
            else {
               ((HttpServletResponse) response).addCookie(getOrgCookie(orgID));
            }

            ((HttpServletResponse) response).sendRedirect(StringUtils.normalizeSpace(redirectUrl));
         }
      }
      else {
         Cookie[] cookies = ((HttpServletRequest) request).getCookies();
         String recordedOrgID = cookies == null ? null :
            Arrays.stream(cookies).filter(c -> c.getName().equals(ORG_COOKIE))
               .map(Cookie::getValue).findFirst().orElse(null);

         if(recordedOrgID == null) {
            String orgID = SUtil.getLoginOrganization(httpRequest);

            if(orgID != null) {
               ((HttpServletResponse) response).addCookie(getOrgCookie(orgID));
            }
         }

         chain.doFilter(request, response);
      }
   }

   private Cookie getOrgCookie(String orgID) {
      Cookie cookie = new Cookie(ORG_COOKIE, orgID);
      cookie.setAttribute("SameSite", "None");
      cookie.setSecure(true);

      return cookie;
   }

   public static final String LOGIN_PAGE = "login.html";

}
