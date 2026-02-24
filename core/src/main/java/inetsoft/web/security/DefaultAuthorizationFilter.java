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

import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
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

         // Only wrap response for potential anonymous sessions (no authenticated principal yet)
         // This avoids overhead for authenticated users
         boolean mayHaveAnonymousSession = principal == null ||
            (principal.getName() != null && principal.getName().startsWith(ClientInfo.ANONYMOUS));

         if(mayHaveAnonymousSession) {
            StatusCapturingResponseWrapper responseWrapper =
               new StatusCapturingResponseWrapper((HttpServletResponse) response);

            chain.doFilter(request, responseWrapper);

            // Handle fresh anonymous sessions based on response status
            int status = responseWrapper.getStatus();

            if(status >= 400) {
               // Error response - invalidate fresh anonymous sessions to prevent
               // bots probing invalid URLs from consuming session resources
               invalidateFreshAnonymousSession(httpRequest);
            }
            else {
               // Successful or redirect response - clear the fresh flag so session becomes established
               clearFreshSessionFlag(httpRequest);
            }
         }
         else {
            // Authenticated user - no need to track response status
            chain.doFilter(request, response);
         }
      }
   }

   /**
    * Clears the fresh session flag after a successful response,
    * marking the session as established.
    */
   private void clearFreshSessionFlag(HttpServletRequest request) {
      HttpSession session = request.getSession(false);

      if(session != null) {
         session.removeAttribute(FRESH_ANONYMOUS_SESSION_ATTR);
      }
   }

   private Cookie getOrgCookie(String orgID) {
      Cookie cookie = new Cookie(ORG_COOKIE, orgID);
      final String sameSite = SreeEnv.getProperty("same.site", "Lax");

      if(isSecurityAllowIframe() || "none".equalsIgnoreCase(sameSite)) {
         cookie.setAttribute("SameSite", "None");
         cookie.setSecure(true);
      }

      return cookie;
   }

   /**
    * Invalidates a fresh anonymous session after an error response (4xx/5xx).
    * This prevents bots probing invalid URLs from consuming session resources.
    * Only sessions marked as "fresh" (just created this request) are invalidated.
    */
   private void invalidateFreshAnonymousSession(HttpServletRequest request) {
      try {
         HttpSession session = request.getSession(false);

         if(session == null) {
            return;
         }

         // Only invalidate sessions marked as fresh anonymous sessions
         Boolean isFresh = (Boolean) session.getAttribute(FRESH_ANONYMOUS_SESSION_ATTR);

         if(!Boolean.TRUE.equals(isFresh)) {
            return;
         }

         SRPrincipal principal = (SRPrincipal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal != null) {
            AuthenticationService.getInstance().logout(principal, request.getRemoteAddr(), "");
            session.invalidate();

            LOG.debug("Invalidated fresh anonymous session after error response for: {}",
                      request.getRequestURI());
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to invalidate fresh anonymous session", e);
      }
   }

   public static final String LOGIN_PAGE = "login.html";
   private static final Logger LOG = LoggerFactory.getLogger(DefaultAuthorizationFilter.class);

   /**
    * Response wrapper that captures the HTTP status code.
    */
   private static class StatusCapturingResponseWrapper extends HttpServletResponseWrapper {
      StatusCapturingResponseWrapper(HttpServletResponse response) {
         super(response);
      }

      @Override
      public void setStatus(int sc) {
         this.status = sc;
         super.setStatus(sc);
      }

      @Override
      public void sendError(int sc) throws IOException {
         this.status = sc;
         super.sendError(sc);
      }

      @Override
      public void sendError(int sc, String msg) throws IOException {
         this.status = sc;
         super.sendError(sc, msg);
      }

      @Override
      public void sendRedirect(String location) throws IOException {
         this.status = HttpServletResponse.SC_FOUND;
         super.sendRedirect(location);
      }

      @Override
      public void reset() {
         this.status = HttpServletResponse.SC_OK;
         super.reset();
      }

      @Override
      public int getStatus() {
         return status;
      }

      private int status = HttpServletResponse.SC_OK;
   }
}
