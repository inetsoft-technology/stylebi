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

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;
import java.util.Objects;

/**
 * Copies the HTTP session attributes into the handshake attributes, skipping attributes whose
 * value is null.
 * <p>
 * {@link HttpSessionHandshakeInterceptor} enumerates the attribute names and then reads each
 * value separately. With the distributed session repository an attribute can be removed (or the
 * session expire) between the two calls, so the value read is null. The SockJS session later
 * copies the handshake attributes into a {@code ConcurrentHashMap}, which rejects null values
 * with a {@code NullPointerException} (Bug #80694).
 */
public class NullSafeHttpSessionHandshakeInterceptor extends HttpSessionHandshakeInterceptor {
   @Override
   public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler,
                                  Map<String, Object> attributes) throws Exception
   {
      boolean result = super.beforeHandshake(request, response, wsHandler, attributes);
      attributes.values().removeIf(Objects::isNull);
      return result;
   }
}
