/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.util.ThreadContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.security.Principal;

public class ThreadLocalCleanupFilter implements Filter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      try {
         Principal principal = SUtil.getPrincipal((HttpServletRequest) request);

         if(principal != null) {
            ThreadContext.setContextPrincipal(principal);
         }

         chain.doFilter(request, response);
      }
      finally {
         ThreadContext.setContextPrincipal(null);
         ThreadContext.setPrincipal(null);
         OrganizationContextHolder.clear();
         ThreadContext.setLocale(null);
         ThreadContext.setProfiling(null);
      }
   }
}
