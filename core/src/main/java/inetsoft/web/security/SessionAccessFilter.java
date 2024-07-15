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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.*;

public class SessionAccessFilter extends OncePerRequestFilter {
   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException
   {
      HttpSession session = request.getSession(false);
      Principal principal = request.getUserPrincipal();

      if(session != null && principal != null) {
         SessionAccessDispatcher.access(
            this, () -> principal, session::getId, () -> getSessionAttributes(session));
      }

      filterChain.doFilter(request, response);
   }

   private Map<String, Object> getSessionAttributes(HttpSession session) {
      Map<String, Object> sessionAttributes = new HashMap<>();

      for(Enumeration<String> e = session.getAttributeNames(); e.hasMoreElements();) {
         String key = e.nextElement();
         sessionAttributes.put(key, session.getAttribute(key));
      }

      return sessionAttributes;
   }
}
