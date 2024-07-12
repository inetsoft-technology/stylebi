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

import inetsoft.sree.SreeEnv;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * {@code SecurityHeaderFilter} adds various security-related headers into the HTTP response.
 * <p>
 * Unless the {@code security.allow.iframe} property is set to "true", the {@code X-Frame-Options}
 * header will be set to "SAMEORIGIN". If it is set to "true", the header will not be added.
 * <p>
 * If the {@code security.enableXSSProtection} property is set to "true", the
 * {@code X-XSS-Protection} header will be set to "1". If it is not set to "true", the header will
 * not be added.
 * <p>
 * If the {@code security.enableContentTypeOptions} property is set to "true", the
 * {@code X-Content-Type-Options} header will be set to "nosniff". If it is not set to "true", the
 * header will not be added.
 */
public class SecurityHeaderFilter extends AbstractSecurityFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      HttpServletResponse httpResponse = (HttpServletResponse) response;

      if(!isSecurityAllowIframe()) {
         //TODO need to differentiate between top-level and nested leave as same origin for now.
//         if(isEnterpriseManager((HttpServletRequest) request)) {
//            httpResponse.setHeader("X-Frame-Options", "DENY");
//         }
//         else {
            httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
//         }
      }

      if("true".equals(enableXSSProtection.get())) {
         httpResponse.setHeader("X-XSS-Protection", "1");
      }

      if("true".equals(enableContentTypeOptions.get())) {
         httpResponse.setHeader("X-Content-Type-Options", "nosniff");
      }

      chain.doFilter(request, response);
   }

   private final SreeEnv.Value enableXSSProtection =
      new SreeEnv.Value("security.enableXSSProtection", 10000, "false");
   private final SreeEnv.Value enableContentTypeOptions =
      new SreeEnv.Value("security.enableContentTypeOptions", 10000, "false");
}
