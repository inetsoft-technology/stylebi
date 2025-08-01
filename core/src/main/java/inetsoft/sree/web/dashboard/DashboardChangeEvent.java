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
package inetsoft.sree.web.dashboard;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;

import java.util.EventObject;

/**
 * Event that signals that a dashboard has changed.
 */
public class DashboardChangeEvent extends EventObject {
   public DashboardChangeEvent(Object source, Type type, String oldName, String newName,
                               IdentityID user)
   {
      super(source);
      this.type = type;
      this.oldName = oldName;
      this.newName = newName;
      this.user = user;
      this.orgID = OrganizationManager.getInstance().getCurrentOrgID();
   }

   public Type getType() {
      return type;
   }

   public String getOldName() {
      return oldName;
   }

   public String getNewName() {
      return newName;
   }

   public IdentityID getUser() {
      return user;
   }

   public String getOrgID() {
      return orgID;
   }

   private final Type type;
   private final String oldName;
   private final String newName;
   private final IdentityID user;
   private final String orgID;

   public enum Type {
      CREATED, MODIFIED, RENAMED, REMOVED
   }
}
