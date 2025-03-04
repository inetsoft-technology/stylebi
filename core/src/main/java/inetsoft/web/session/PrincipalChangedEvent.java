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

import inetsoft.sree.security.SRPrincipal;
import org.springframework.context.ApplicationEvent;
import org.springframework.session.Session;

public class PrincipalChangedEvent extends ApplicationEvent {
   private final SRPrincipal oldPrincipal;
   private final SRPrincipal newPrincipal;
   private final Session session;

   public PrincipalChangedEvent(Object source, SRPrincipal oldPrincipal, SRPrincipal newPrincipal,
                                Session session)
   {
      super(source);
      this.oldPrincipal = oldPrincipal;
      this.newPrincipal = newPrincipal;
      this.session = session;
   }

   public SRPrincipal getOldPrincipal() {
      return oldPrincipal;
   }

   public SRPrincipal getNewPrincipal() {
      return newPrincipal;
   }

   public Session getSession() {
      return session;
   }
}
