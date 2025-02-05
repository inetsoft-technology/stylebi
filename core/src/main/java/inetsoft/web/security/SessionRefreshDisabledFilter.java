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

import inetsoft.util.ThreadContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Prevents some requests from refreshing the session (updating lastAccessedTime)
 */
public class SessionRefreshDisabledFilter extends AbstractSecurityFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;

      if(isPageRequested("/em/monitoring/server/summary", httpRequest) ||
         isPageRequested("/em/getSummaryImage/**", httpRequest))
      {
         try {
            ThreadContext.setSessionInfo("session.refresh.disabled", true);
            chain.doFilter(request, response);
         }
         finally {
            ThreadContext.setSessionInfo("session.refresh.disabled", null);
         }
      }
      else {
         chain.doFilter(request, response);
      }
   }
}
