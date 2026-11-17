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

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80694: a session attribute that disappears between enumerating the names and reading the
 * value (distributed session repository) must not end up as a null handshake attribute, since
 * SockJS copies the attributes into a ConcurrentHashMap.
 */
@Tag("core")
class NullSafeHttpSessionHandshakeInterceptorTest {
   private HttpSession session() {
      HttpSession session = mock(HttpSession.class);
      when(session.getId()).thenReturn("sid");
      when(session.getAttributeNames())
         .thenAnswer(i -> Collections.enumeration(java.util.List.of("present", "gone")));
      when(session.getAttribute("present")).thenReturn("value");
      when(session.getAttribute("gone")).thenReturn(null);
      return session;
   }

   private Map<String, Object> handshake(HttpSessionHandshakeInterceptor interceptor)
      throws Exception
   {
      MockHttpServletRequest servletRequest = new MockHttpServletRequest() {
         @Override
         public HttpSession getSession(boolean create) {
            return session();
         }
      };
      Map<String, Object> attributes = new HashMap<>();
      assertTrue(interceptor.beforeHandshake(
         new ServletServerHttpRequest(servletRequest),
         new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes));
      return attributes;
   }

   @Test
   void springInterceptorProducesNullAttributeThatBreaksSockJsCopy() throws Exception {
      Map<String, Object> attributes = handshake(new HttpSessionHandshakeInterceptor());
      assertTrue(attributes.containsKey("gone"));
      assertThrows(NullPointerException.class, () -> new ConcurrentHashMap<>().putAll(attributes));
   }

   @Test
   void nullValuedSessionAttributesAreSkipped() throws Exception {
      Map<String, Object> attributes = handshake(new NullSafeHttpSessionHandshakeInterceptor());

      assertEquals("value", attributes.get("present"));
      assertFalse(attributes.containsKey("gone"));
      assertEquals("sid", attributes.get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME));
      assertDoesNotThrow(() -> new ConcurrentHashMap<>().putAll(attributes));
   }
}
