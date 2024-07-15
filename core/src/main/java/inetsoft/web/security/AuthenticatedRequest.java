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

import inetsoft.sree.security.SRPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.security.Principal;
import java.util.Arrays;

final class AuthenticatedRequest extends HttpServletRequestWrapper {
   AuthenticatedRequest(HttpServletRequest request, Principal principal) {
      super(request);
      this.principal = principal;
   }

   @Override
   public String getRemoteUser() {
      return principal.getName();
   }

   @Override
   public Principal getUserPrincipal() {
      return principal;
   }

   @Override
   public boolean isUserInRole(String role) {
      boolean result = super.isUserInRole(role);

      if(!result && principal instanceof SRPrincipal) {
         for(String r : Arrays.stream(((SRPrincipal) principal).getRoles()).map(r -> r.name).toList()) {
            if(r.equals(role)) {
               result = true;
               break;
            }
         }
      }

      return result;
   }

   private final Principal principal;
}
