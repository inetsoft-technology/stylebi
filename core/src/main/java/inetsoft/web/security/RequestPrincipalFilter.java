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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.ThreadContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.Principal;
import java.util.Locale;

/**
 * Filter that adds the authenticated user principal to the request.
 *
 * @since 12.3
 */
public class RequestPrincipalFilter extends AbstractSecurityFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      Locale oldLocale = ThreadContext.getLocale();

      if(!isPublicApi(httpRequest)) {
         Principal principal = getPrincipal(httpRequest);

         if(principal != null) {
            if(principal instanceof SRPrincipal) {
               ClientInfo user = ((SRPrincipal) principal).getUser();

               if(user != null) {
                  user.setLocale(httpRequest.getLocale());
               }

               ThreadContext.setContextPrincipal(principal);
               ThreadContext.setLocale(((SRPrincipal) principal).getLocale());
               ((SRPrincipal) principal).setLastAccess(System.currentTimeMillis());
            }

            httpRequest = new AuthenticatedRequest(httpRequest, principal);
         }
      }

      try {
         chain.doFilter(httpRequest, response);
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
         ThreadContext.setLocale(oldLocale);
      }
   }

   @Override
   public void destroy() {
      // NO-OP
   }

   @Override
   public void sessionAccessed(SessionAccessDispatcher.SessionAccessEvent event) {
      final Principal principal = event.getPrincipal();

      if(principal instanceof SRPrincipal) {
         ((SRPrincipal) principal).setLastAccess(System.currentTimeMillis());
      }
   }

   private Principal getPrincipal(HttpServletRequest request) {
      HttpSession session = request.getSession();
      return (Principal) session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
   }
}
