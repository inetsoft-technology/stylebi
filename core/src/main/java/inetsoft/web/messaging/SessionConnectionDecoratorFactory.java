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
package inetsoft.web.messaging;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

public class SessionConnectionDecoratorFactory implements WebSocketHandlerDecoratorFactory {

   public SessionConnectionDecoratorFactory(SessionConnectionService connectionService) {
      this.connectionService = connectionService;
   }

   @Override
   public WebSocketHandler decorate(WebSocketHandler handler) {
      return new SessionConnectionHandler(handler);
   }

   private final SessionConnectionService connectionService;

   private final class SessionConnectionHandler extends WebSocketHandlerDecorator {
      public SessionConnectionHandler(WebSocketHandler delegate) {
         super(delegate);
      }

      @Override
      public void afterConnectionEstablished(WebSocketSession session) throws Exception {
         super.afterConnectionEstablished(session);
         connectionService.webSocketConnected(session);
      }

      @Override
      public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
         throws Exception
      {
         connectionService.webSocketDisconnected(session);
         super.afterConnectionClosed(session, closeStatus);
      }
   }
}
