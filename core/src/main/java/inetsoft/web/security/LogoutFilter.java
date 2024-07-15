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

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Filter that handles logging the user out of the application.
 */
public class LogoutFilter extends AbstractLogoutFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;

      if(isPageRequested(LOGOUT_URI, httpRequest)) {
         logout(httpRequest, (HttpServletResponse) response);
      }
      else if(isPageRequested(EXPIRED_URI, httpRequest)) {
         handleSessionExpired(httpRequest, (HttpServletResponse) response);
      }
      else {
         chain.doFilter(request, response);
      }
   }

   private void handleSessionExpired(HttpServletRequest request, HttpServletResponse response)
      throws IOException
   {
      response.sendRedirect(getLogoutRedirectUri(request));
   }

   public static final String LOGOUT_URI = "/logout";
   private static final String EXPIRED_URI = "/sessionexpired";
}
