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

package inetsoft.web.admin.security;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.User;

import java.io.Serializable;

public class IdentityChangedMessage implements Serializable {
   public IdentityChangedMessage(int type, IdentityID identity, IdentityID oldIdentity) {
      this.type = type;
      this.identity = identity;
      this.oldIdentity = oldIdentity;
      this.sessionID = null;
   }

   public IdentityChangedMessage(IdentityID orgIdentity, String sessionID) {
      this.type = User.ORGANIZATION;
      this.identity = orgIdentity;
      this.oldIdentity = null;
      this.sessionID = sessionID;
   }

   public int getType() {
      return type;
   }

   public IdentityID getIdentity() {
      return identity;
   }

   public IdentityID getOldIdentity() {
      return oldIdentity;
   }

   public String getSessionID() {
      return sessionID;
   }

   private final int type;
   private final IdentityID identity;
   private final IdentityID oldIdentity;
   private final String sessionID;
}
