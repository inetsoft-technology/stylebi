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
import org.springframework.context.ApplicationEvent;
import org.springframework.session.Session;

import java.security.Principal;

public class SessionExpiringSoonEvent extends ApplicationEvent {
   private final String sessionId;
   private final long remainingTime;
   private final boolean expiringSoon;
   private final boolean nodeProtection;
   private final Principal principalCookie;

   public SessionExpiringSoonEvent(Object source, Session session, long remainingTime,
                                   boolean expiringSoon, boolean nodeProtection)
   {
      super(source);
      this.sessionId = session.getId();
      this.remainingTime = remainingTime;
      this.expiringSoon = expiringSoon;
      this.nodeProtection = nodeProtection;
      this.principalCookie = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
   }

   public String getSessionId() {
      return sessionId;
   }

   public Principal getPrincipalCookie() {
      return principalCookie;
   }

   public long getRemainingTime() {
      return remainingTime;
   }

   public boolean isExpiringSoon() {
      return expiringSoon;
   }

   public boolean isNodeProtection() {
      return nodeProtection;
   }
}
