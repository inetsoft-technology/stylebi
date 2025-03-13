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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.ThreadContext;
import inetsoft.web.security.auth.UnauthorizedAccessError;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import inetsoft.web.security.auth.MissingTokenException;
import inetsoft.web.security.auth.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.Arrays;
import java.util.Locale;

/**
 * {@code JWTFilter} handles the authorization for the public web API endpoints. Authentication is
 * handled at the "/api/public/login" and "/api/public/logout" endpoints. These endpoints are
 * described in the API documentation.
 * <p>
 * Authorization is accomplished by verifying that all not-authentication requests to the public web
 * API endpoints provide a valid JSON web token (JWT) using in the "X-Inetsoft-Api-Token" HTTP
 * request header. The JWT is provided to the client in the response for requests to the
 * "/api/public/login" endpoint.
 * <p>
 * When a request is received that provides a principal, but is missing the JWT header, it is
 * assumed that a single-sign on has taken place. These requests are treated as requests to the
 * "/api/public/login" endpoint without performing the authentication step.
 *
 * @see <a href="https://jwt.io/">JSON Web Tokens</a>
 * @see <a href="https://tools.ietf.org/html/rfc6750#section-2.1">HTTP Bearer Authorization</a>
 */
public class JWTFilter extends AbstractSecurityFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      Locale oldLocale = ThreadContext.getLocale();

      if(!isPageRequested(LOGIN_URI, httpRequest) && !isDocumentationResource(httpRequest) &&
         isPublicApi(httpRequest) || isTeamWebsocketEndpoint(httpRequest))
      {
         HttpServletResponse httpResponse = (HttpServletResponse) response;
         String header = httpRequest.getHeader(TOKEN_HEADER);

         if(header == null && httpRequest.getUserPrincipal() != null) {
            request.getRequestDispatcher(LOGIN_URI).forward(request, response);
            return;
         }

         if(header == null) {
            if(isPageRequested(LOGOUT_URI, httpRequest)) {
               httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
            else {
               handleUnauthorized(
                  httpResponse, new MissingTokenException(
                     "Access to the requested resource requires a valid authorization token."));
            }

            return;
         }

         try {
            SRPrincipal principal = service.getPrincipal(
               request.getRemoteHost(), header, request.getLocale());
            httpRequest = new AuthenticatedRequest(httpRequest, principal);
            ThreadContext.setContextPrincipal(principal);
            ThreadContext.setLocale(principal.getLocale());
            principal.setLastAccess(System.currentTimeMillis());
         }
         catch(UnauthorizedAccessException e) {
            handleUnauthorized(httpResponse, e);
            return;
         }
      }

      try {
         chain.doFilter(httpRequest, response);
      }
      catch(Exception ex) {
         if(ex.getCause() instanceof UnauthorizedAccessException) {
            handleUnauthorized((HttpServletResponse) response, (UnauthorizedAccessException) ex.getCause());
            return;
         }

         throw ex;
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
         ThreadContext.setLocale(oldLocale);
      }
   }

   @Autowired
   public void setJwtService(JwtService service) {
      this.service = service;
   }

   private void handleUnauthorized(HttpServletResponse response, UnauthorizedAccessException e)
      throws IOException
   {
      UnauthorizedAccessError error = new UnauthorizedAccessError(e);
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      try(PrintWriter writer = response.getWriter()) {
         mapper.writeValue(writer, error);
      }
   }

   private boolean isDocumentationResource(HttpServletRequest request) {
      return Arrays.stream(docResources).anyMatch(path -> isPageRequested(path, request));
   }

   private JwtService service;
   private final ObjectMapper mapper = new ObjectMapper();

   private static final String LOGIN_URI = "/api/public/login";
   private static final String LOGOUT_URI = "/api/public/logout";
   private static final String[] docResources = {
      "/api/public/api-docs.html",
      "/api/public/rapidoc-min.js",
      "/api/public/openapi.json"
   };
   private static final String TOKEN_HEADER = "X-Inetsoft-Api-Token";
}
