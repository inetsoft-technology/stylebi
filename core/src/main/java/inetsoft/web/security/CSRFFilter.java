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
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that provides CSRF protection using the double submit cookies approach.
 */
public class CSRFFilter extends AbstractSecurityFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain filterChain)
      throws IOException, ServletException
   {
      if(!isEnabled()) {
         filterChain.doFilter(request, response);
         return;
      }

      HttpServletRequest httpRequest = (HttpServletRequest) request;
      HttpServletResponse httpResponse = (HttpServletResponse) response;

      String token = loadToken(httpRequest);
      boolean missingToken = token == null;

      if(missingToken) {
         token = generateToken();
      }

      if(!isCsrfProtectionRequired(httpRequest)) {
         applyToken(token, httpRequest, httpResponse);
         filterChain.doFilter(request, response);
         return;
      }

      String actualToken = httpRequest.getHeader(HEADER_NAME);

      if(actualToken == null) {
         actualToken = httpRequest.getParameter(PARAMETER_NAME);
      }

      if(!token.equals(actualToken)) {
         if(missingToken) {
            LOG.warn("Missing CSRF token accessing {} from {} ",
                     httpRequest.getRequestURL(), httpRequest.getRemoteAddr());
         }
         else {
            LOG.warn("Invalid CSRF token accessing {} from {} ",
                     httpRequest.getRequestURL(), httpRequest.getRemoteAddr());
         }

         SUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN);
         return;
      }

      applyToken(token, httpRequest, httpResponse);
      filterChain.doFilter(request, response);
   }

   /**
    * Applies the token to the HTTP response and saves it if it has not already
    * been done.
    *
    * @param token    the CSRF token.
    * @param request  the HTTP request.
    * @param response the HTTP response.
    */
   private void applyToken(String token, HttpServletRequest request,
                           HttpServletResponse response)
   {
      Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);

      if(cookie == null || !token.equals(cookie.getValue())) {
         String safeToken = StringUtils.normalizeSpace(token);
         final StringBuilder builder = new StringBuilder().append(COOKIE_NAME)
                                                         .append("=")
                                                         .append(safeToken)
                                                         .append("; Path=/");

         final String sameSite = SreeEnv.getProperty("same.site", "Lax");

         // SameSite=None requires Secure
         if(isSecurityAllowIframe() || "none".equalsIgnoreCase(sameSite)) {
            builder.append("; SameSite=None");
            builder.append("; Secure");
         }
         else {
            builder.append("; SameSite=")
               .append(sameSite);

            if(SreeEnv.getBooleanProperty("secure.cookie")) {
               builder.append("; Secure");
            }
         }

         response.addHeader("Set-Cookie", builder.toString());
         saveToken(safeToken, request);
      }
   }

   /**
    * Loads the CSRF token for the specified request.
    *
    * @param request the HTTP request.
    *
    * @return the CSRF token or <tt>null</tt> if none has been generated.
    */
   private String loadToken(HttpServletRequest request) {
      String token = null;
      HttpSession session = request.getSession(false);

      if(session != null) {
         token = (String) session.getAttribute(SESSION_ATTRIBUTE_NAME);
      }

      return token;
   }

   /**
    * Save the CSRF token for the specified request.
    *
    * @param token   the token to save or <tt>null</tt> to clear any previously
    *                saved token.
    * @param request the HTTP request.
    */
   private void saveToken(String token, HttpServletRequest request) {
      if(token == null) {
         HttpSession session = request.getSession(false);

         if(session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE_NAME);
         }
      }
      else {
         HttpSession session = request.getSession();
         session.setAttribute(SESSION_ATTRIBUTE_NAME, token);
      }
   }

   /**
    * Generates a new CSRF token.
    *
    * @return the new token.
    */
   private String generateToken() {
      return UUID.randomUUID().toString();
   }

   /**
    * Determines if the specified request requires CSRF protection.
    *
    * @param request the HTTP request.
    *
    * @return <tt>true</tt> if protection is required; <tt>false</tt>
    *         otherwise.
    */
   private boolean isCsrfProtectionRequired(HttpServletRequest request) {
      return isApi(request) && !isPublicApi(request);
   }

   private boolean isEnabled() {
      return "true".equals(csrfFilterEnabled.get());
   }

   private final SreeEnv.Value csrfFilterEnabled =
      new SreeEnv.Value("csrf.filter.enabled", 10000, "true");
   private static final String COOKIE_NAME = "XSRF-TOKEN";
   private static final String HEADER_NAME = "X-XSRF-TOKEN";
   private static final String PARAMETER_NAME = "_csrf";
   private static final String SESSION_ATTRIBUTE_NAME = "__private_" +
      CSRFFilter.class.getName() + ".TOKEN";
   private static final Logger LOG = LoggerFactory.getLogger(CSRFFilter.class);
}
