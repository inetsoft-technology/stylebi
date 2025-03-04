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

import org.springframework.context.ApplicationEvent;
import org.springframework.session.Session;

public class SessionExpiringSoonEvent extends ApplicationEvent {
   private final Session session;
   private final long remainingTime;
   private final boolean expiringSoon;
   private final boolean nodeProtection;

   public SessionExpiringSoonEvent(Object source, Session session, long remainingTime,
                                   boolean expiringSoon, boolean nodeProtection)
   {
      super(source);
      this.session = session;
      this.remainingTime = remainingTime;
      this.expiringSoon = expiringSoon;
      this.nodeProtection = nodeProtection;
   }

   public Session getSession() {
      return session;
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
