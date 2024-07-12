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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.web.portal.controller.SessionErrorController;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Filter that sets the current user to an anonymous user if not authenticated.
 *
 * @since 12.3
 */
public class AnonymousUserFilter extends AbstractSecurityFilter {
   @Override
   public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;

      if(!isPublicResource(httpRequest) && !isPublicApi(httpRequest)) {
         HttpSession session = httpRequest.getSession(true);

         if(session.getAttribute(RepletRepository.PRINCIPAL_COOKIE) == null) {
            try {
               SRPrincipal principal = authenticateAnonymous(request);

               if(principal != null) {
                  authenticate(request, new IdentityID(ClientInfo.ANONYMOUS, OrganizationManager.getCurrentOrgName()), null, null, SUtil.MY_LOCALE);
               }
            }
            catch(AuthenticationFailureException e) {
               if(e.getReason() == AuthenticationFailureReason.SESSION_EXCEEDED) {
                  if("XMLHttpRequest".equalsIgnoreCase(httpRequest.getHeader("X-Requested-With")))
                  {
                     HttpServletResponse httpResponse = (HttpServletResponse) response;
                     httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                     httpResponse.setContentType("application/json");
                     ObjectMapper mapper = new ObjectMapper();
                     ObjectNode body = mapper.createObjectNode();
                     body.put("error", e.getReason().name());
                     body.put("message", e.getCause().getMessage());

                     try(PrintWriter writer = httpResponse.getWriter()) {
                        mapper.writeValue(writer, body);
                     }
                  }
                  else {
                     request.setAttribute("authenticationFailure", e);
                     final String endpoint;

                     if(isSecurityEnabled()) {
                        endpoint = SessionErrorController.SESSIONS_EXCEEDED;
                     }
                     else {
                        endpoint = SessionErrorController.NAMED_USER_WITHOUT_SECURITY;
                     }

                     request.getRequestDispatcher("/error/" + endpoint)
                            .forward(request, response);
                  }

                  return;
               }
               else {
                  throw new ServletException(e.getMessage(), e);
               }
            }
         }
      }

      chain.doFilter(request, response);
   }
}
