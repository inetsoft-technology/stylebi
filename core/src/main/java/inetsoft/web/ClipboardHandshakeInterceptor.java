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

import inetsoft.web.composer.ClipboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

/**
 * Intercepts the websocket handshake to add clipboard object to session attributes.
 */
public class ClipboardHandshakeInterceptor extends HttpSessionHandshakeInterceptor {
   @Override
   public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler,
                                  Map<String, Object> attributes)
      throws Exception
   {
      HttpSession session = this.getSession(request);

      if(session.getAttribute(ClipboardService.CLIPBOARD) == null) {
         session.setAttribute(ClipboardService.CLIPBOARD, new ClipboardService());
      }

      return super.beforeHandshake(request, response, wsHandler, attributes);
   }

   private HttpSession getSession(ServerHttpRequest request) {
      if(request instanceof ServletServerHttpRequest) {
         ServletServerHttpRequest serverRequest = (ServletServerHttpRequest)request;
         return serverRequest.getServletRequest().getSession(this.isCreateSession());
      }
      else {
         return null;
      }
   }
}
