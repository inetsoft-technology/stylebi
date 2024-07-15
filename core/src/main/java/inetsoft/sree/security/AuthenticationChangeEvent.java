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
package inetsoft.sree.security;

import inetsoft.uql.util.Identity;

import java.util.EventObject;

/**
 * Event that signals that a security object has been removed or renamed.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class AuthenticationChangeEvent extends EventObject {
   /**
    * Creates a new instance of AuthenticationChangeEvent.
    *
    * @param source  the object on which the event originally occurred.
    * @param oldID   the old name of the security object.
    * @param newID   the new name of the security object.
    * @param type    the type of the security object. The value of this parameter
    *                must be one of the type constants defined in
    *                {@link Identity}.
    * @param removed <code>true</code> if the security object has been removed;
    *                <code>false</code> otherwise.
    */
   public AuthenticationChangeEvent(Object source, IdentityID oldID, IdentityID newID,
                                    String oldOrgID, String newOrgID, int type, boolean removed)
   {
      super(source);
      
      this.oldID = oldID;
      this.newID = newID;
      this.oldOrgID = oldOrgID;
      this.newOrgID = newOrgID;
      this.type = type;
      this.removed = removed;
   }
   
   /**
    * Gets the old name of the security object.
    *
    * @return the old name of the security object.
    */
   public IdentityID getOldID() {
      return oldID;
   }
   
   /**
    * Gets the new name of the security object.
    *
    * @return the new name of the security object.
    */
   public IdentityID getNewID() {
      return newID;
   }

   /**
    * Gets the name of the new orgID, if security object is organization
    * @return the new id of the object if organization, null if not
    */
   public String getNewOrgID() {
      return newOrgID;
   }

   /**
    * Gets the name of the old orgID, if security object is organization
    * @return the old id of the object if organization, null if not
    */
   public String getOldOrgID() {
      return oldOrgID;
   }
   
   /**
    * Gets the type of the security object. This value will be one of the type
    * constants defined in {@link inetsoft.uql.util.Identity}.
    *
    * @return the security object type.
    */
   public int getType() {
      return type;
   }
   
   /**
    * Determines if the security object has been removed or renamed.
    *
    * @return <code>true</code> if the security object has been removed;
    *         <code>false</code> if it has been renamed.
    */
   public boolean isRemoved() {
      return removed;
   }
   
   private IdentityID oldID = null;
   private IdentityID newID = null;
   private String oldOrgID = null;
   private String newOrgID = null;
   private int type = Identity.USER;
   private boolean removed = false;
}
