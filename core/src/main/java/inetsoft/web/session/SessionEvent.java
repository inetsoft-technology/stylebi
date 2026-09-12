/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.session;

import inetsoft.sree.RepletRepository;
import inetsoft.web.security.AbstractLogoutFilter;
import org.springframework.context.ApplicationEvent;
import org.springframework.session.Session;

import java.security.Principal;

public class SessionEvent extends ApplicationEvent {
   public SessionEvent(Object source, Session session) {
      super(source);

      if(session == null) {
         throw new IllegalArgumentException("Session cannot be null");
      }

      this.sessionId = session.getId();
      this.loggedOutAttribute = session.getAttribute(AbstractLogoutFilter.LOGGED_OUT);
      this.principalCookie = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
   }

   public String getSessionId() {
      return sessionId;
   }

   public Principal getPrincipalCookie() {
      return principalCookie;
   }

   public Object getLoggedOutAttribute() {
      return loggedOutAttribute;
   }

   @Override
   public String toString() {
      return this.getClass().getName() + "{" +
         "sessionId='" + sessionId + '\'' +
         ", loggedOutAttribute=" + loggedOutAttribute +
         ", principalCookie=" + principalCookie +
         ", source=" + getSource() +
         '}';
   }

   private final String sessionId;
   private final Object loggedOutAttribute;
   private final Principal principalCookie;
}