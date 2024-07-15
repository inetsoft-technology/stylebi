/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.security;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XPrincipal;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.servlet.http.*;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Map;

public abstract class AbstractLogoutFilter extends AbstractSecurityFilter {
   protected void logout(HttpServletRequest request, HttpServletResponse response)
      throws IOException
   {
      String redirectUri = getLogoutRedirectUri(request);
      HttpSession session = request.getSession(false);

      if(session != null) {
         session.setAttribute(LOGGED_OUT, true);
      }

      logout(request);
      response.sendRedirect(redirectUri);
   }

   protected String getLogoutRedirectUri(HttpServletRequest request)
      throws UnsupportedEncodingException
   {
      String redirectUri = SreeEnv.getProperty(
         "portal.logout.url", LinkUriArgumentResolver.getLinkUri(request) + REDIRECT_URI);
      Map<String, String[]> queryParameters = getQueryParameters(request);
      boolean fromEm = isFromEm(queryParameters);
      boolean showLogin = isShowLogin(queryParameters);
      boolean guestLogin = isGuestLogin(request, showLogin);

      if(fromEm) {
         if("GET".equals(request.getMethod()) && request.getParameter("redirectUri") != null) {
            redirectUri = request.getParameter("redirectUri");
         }
         else {
            redirectUri = request.getContextPath() + "/em";
         }
      }

      // SSO need login in login page of sso.
      if(guestLogin || fromEm && !isSSO()) {
         redirectUri = LinkUriArgumentResolver.getLinkUri(request) +
            DefaultAuthorizationFilter.LOGIN_PAGE +
            "?requestedUrl=" + URLEncoder.encode(redirectUri, "UTF-8");
      }

      return redirectUri;
   }

   private boolean isFromEm(Map<String, String[]> queryParameters) {
      String[] params = queryParameters.get("fromEm");
      return params != null && params.length > 0 && "true".equals(params[0]);
   }

   private boolean isShowLogin(Map<String, String[]> queryParameters) {
      String[] params = queryParameters.get("showLogin");
      return params != null && params.length > 0 && "true".equals(params[0]);
   }

   private boolean isGuestLogin(HttpServletRequest request, boolean showLogin) {
      boolean guestLogin = false;
      HttpSession session = request.getSession(false);

      if(session != null) {
         Principal principal = (Principal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal != null && XPrincipal.ANONYMOUS.equals(principal.getName())) {
            SecurityEngine engine = getSecurityEngine();

            if(engine != null && engine.containsAnonymous() || showLogin) {
               // guest log in, redirect to the login page
               guestLogin = true;
            }
         }
      }

      return guestLogin;
   }

   /**
    * <code>true</code> If SSO Logout URL is specified
    *
    * @return <code>true</code> if use sso logout; <code>false</code> otherwise
    */
   private boolean isSSO() {
      return !StringUtils.isEmpty(SreeEnv.getProperty("sso.logout.url"));
   }

   public static final String LOGGED_OUT = AbstractLogoutFilter.class.getName() + ".loggedOut";
   private static final String REDIRECT_URI = "app/portal";
}
