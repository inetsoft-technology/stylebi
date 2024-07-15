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
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
         principal = (SRPrincipal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
      }

      if(!provider.getAuthenticationProvider().isVirtual() && !isPublicResource(httpRequest) &&
         !isPublicApi(httpRequest) && !isTeamWebsocketEndpoint(httpRequest))
      {
         if((principal == null || principal.getName().equals(XPrincipal.ANONYMOUS)) &&
            // if there is a user explicitly named "anonymous", it is an allowed guest login
            (engine == null || !engine.containsAnonymous()))
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
            String orgID = getLoginOrganization(httpRequest);
            String requestedUrl = LinkUriArgumentResolver.transformUri(httpRequest);
            String sep = requestedUrl.contains("?") ? "&" : "?";
            String queryString = httpRequest.getQueryString() != null ?
               sep + httpRequest.getQueryString() : "";

            String redirectUrl = LinkUriArgumentResolver.getLinkUri(httpRequest) + LOGIN_PAGE +
               "?requestedUrl=" + URLEncoder.encode(requestedUrl + queryString, "UTF-8");

            if(orgID != null) {
               ((HttpServletResponse) response).addCookie(new Cookie(ORG_COOKIE, orgID));
            }

            ((HttpServletResponse) response).sendRedirect(StringUtils.normalizeSpace(redirectUrl));
         }
      }
      else {
         Cookie[] cookies = ((HttpServletRequest) request).getCookies();
         String recordedOrgID = cookies == null ? null :
            Arrays.stream(cookies).filter(c -> c.getName().equals(ORG_COOKIE))
               .map(Cookie::getValue).findFirst().orElse(null);

         if(recordedOrgID == null &&
            isPageRequested("/" + LOGIN_PAGE, (HttpServletRequest) request))
         {
            String orgID = getLoginOrganization(httpRequest);

            if(orgID != null) {
               ((HttpServletResponse) response).addCookie(new Cookie(ORG_COOKIE, orgID));
            }
         }

         chain.doFilter(request, response);
      }
   }

   private String getLoginOrganization(HttpServletRequest request) {
      String type = SreeEnv.getProperty("security.login.orgLocation", "domain");
      String orgID = null;

      if("path".equals(type)) {
         // get as the first directory of the url, of the form "http://somehost.com/orgID/"
            URI uri = URI.create(LinkUriArgumentResolver.getLinkUri(request));
            String requestedPath = request.getPathInfo();

            if(requestedPath == null) {
               String uriPath = uri.getRawPath();

               if(uriPath != null) {
                  if(uriPath.startsWith("/")) {
                     uriPath = uriPath.substring(1);
                  }

                  int index = uriPath.indexOf('/');

                  if(index < 0) {
                     orgID = uriPath;
                  }
                  else {
                     orgID = uriPath.substring(0, index);
                  }
               }
            }
         }
         else {
            // get the lowest level subdomain, of the form "http://orgID.somehost.com/"
            String host = LinkUriArgumentResolver.getRequestHost(request);

            if(host != null) {
               int index = host.indexOf('.');

               if(index >= 0) {
                  orgID = host.substring(0, index);
               }
            }
         }
      return orgID;
   }

   public static final String LOGIN_PAGE = "login.html";
   private static final String ORG_COOKIE = "X-INETSOFT-ORGID";

}
