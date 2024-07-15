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

import jakarta.servlet.http.HttpServletRequest;

public class RequestUriInfo {
   public RequestUriInfo(HttpServletRequest request) {
      String ip = request.getHeader("remote_ip");

      if(ip == null || ip.isEmpty()) {
         ip = request.getHeader("X-Forwarded-For");
      }

      if(ip == null || ip.isEmpty()) {
         ip = request.getRemoteAddr();
      }

      StringBuilder uri = new StringBuilder()
         .append(request.getRequestURI());

      if(request.getQueryString() != null &&
         !request.getQueryString().isEmpty())
      {
         uri.append('?').append(request.getQueryString());
      }

      this.remoteIp = ip;
      this.requestedUri = uri.toString();
   }

   public String getRemoteIp() {
      return remoteIp;
   }

   public String getRequestedUri() {
      return requestedUri;
   }

   private final String remoteIp;
   private final String requestedUri;
}
