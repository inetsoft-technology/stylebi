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
import inetsoft.sree.web.SessionLicenseServiceProvider;
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
   public AbstractLogoutFilter(SessionLicenseServiceProvider sessionLicenseServiceProvider,
                               AuthenticationService authenticationService)
   {
      super(sessionLicenseServiceProvider,  authenticationService);
   }

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
      String defRedirectUri = LinkUriArgumentResolver.getLinkUri(request) + REDIRECT_URI;
      String redirectUri = SreeEnv.getProperty("portal.logout.url", defRedirectUri);
      Map<String, String[]> queryParameters = getQueryParameters(request);
      boolean fromEm = isFromEm(queryParameters);
      boolean showLogin = isShowLogin(queryParameters);
      boolean guestLogin = isGuestLogin(request, showLogin);

      // for SSO only redirect to EM when portal.logout.url is not set
      if(fromEm && (!isSSO() || redirectUri.equals(defRedirectUri))) {
         String requestedRedirectUri = "GET".equals(request.getMethod()) ?
            request.getParameter("redirectUri") : null;

         if(requestedRedirectUri != null && isSameOriginRedirectUri(request, requestedRedirectUri)) {
            redirectUri = requestedRedirectUri;
         }
         else {
            redirectUri = request.getContextPath() + "/em";
         }
      }

      boolean securityEnabled = getSecurityEngine().isSecurityEnabled();

      // SSO needs login at the SSO provider's login page.
      // showLogin=true means the user explicitly clicked "Log In" as a guest, so redirect to the
      // login page even when security is not formally enabled — the login page always exists.
      if((securityEnabled || showLogin) && (guestLogin || fromEm) && !isSSO()) {
         redirectUri = LinkUriArgumentResolver.getLinkUri(request) +
            DefaultAuthorizationFilter.LOGIN_PAGE +
            "?requestedUrl=" + URLEncoder.encode(redirectUri, "UTF-8");
      }

      return redirectUri;
   }

   /**
    * Determines whether a client-supplied redirect target is safe to honor: either an
    * app-relative path or an absolute URL within this application's own origin. Rejects
    * protocol-relative ("//host/...") targets and backslash variants that browsers normalize to
    * protocol-relative, since both resolve to an external host despite lacking a scheme. Also
    * rejects any embedded ASCII tab/CR/LF: per the WHATWG URL Standard, a browser strips those
    * characters from the entire URL string as its first normalization step (before
    * scheme/authority parsing), so e.g. "/\t/attacker.example" would otherwise look app-relative
    * here while resolving to the protocol-relative "//attacker.example" in the browser.
    */
   private boolean isSameOriginRedirectUri(HttpServletRequest request, String redirectUri) {
      if(redirectUri.isEmpty() || containsUrlWhitespace(redirectUri)) {
         return false;
      }

      char first = redirectUri.charAt(0);

      if(first == '/' || first == '\\') {
         return redirectUri.length() < 2 ||
            (redirectUri.charAt(1) != '/' && redirectUri.charAt(1) != '\\');
      }

      return redirectUri.startsWith(LinkUriArgumentResolver.getLinkUri(request));
   }

   private boolean containsUrlWhitespace(String value) {
      for(int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);

         if(c == '\t' || c == '\n' || c == '\r') {
            return true;
         }
      }

      return false;
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
         Principal principal = SUtil.getPrincipal(request);
         IdentityID currUser = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

         if(principal != null && XPrincipal.ANONYMOUS.equals(currUser.getName())) {
            SecurityEngine engine = getSecurityEngine();

            if((engine != null && engine.containsAnonymous()) || showLogin) {
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
