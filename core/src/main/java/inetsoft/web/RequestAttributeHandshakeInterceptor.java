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
package inetsoft.web;

import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

public class RequestAttributeHandshakeInterceptor implements HandshakeInterceptor {
   @Override
   public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler,
                                  Map<String, Object> attributes) throws Exception
   {
      String uri;

      if(request instanceof ServletServerHttpRequest) {
         uri = LinkUriArgumentResolver.getLinkUri(
            ((ServletServerHttpRequest) request).getServletRequest());
      }
      else {
         uri = request.getURI().toString();
         int index = uri.indexOf("/app/viewer/view");

         if(index >= 0) {
            uri = uri.substring(0, index);
         }
      }

      attributes.put("viewsheetLinkUri", uri);

      try {
         URI url = URI.create(uri);
         String host = url.getHost();
         int port = url.getPort();
         if(port >= 0) {
            host = host + ":" + port;
         }
         attributes.put("viewsheetLinkHost", host);
      }
      catch(Exception ignore) {
      }

      return true;
   }

   @Override
   public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception)
   {
      // NO-OP
   }
}
