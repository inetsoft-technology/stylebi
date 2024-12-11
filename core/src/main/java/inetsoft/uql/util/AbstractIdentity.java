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
package inetsoft.uql.util;

import inetsoft.sree.security.IdentityID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines the Identity.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public abstract class AbstractIdentity implements Identity {
   /**
    * Constructor.
    */
   public AbstractIdentity() {
      super();
   }


   /**
    * Indicates whether or not this Identity is editable.
    */
   @Override
   public boolean isEditable() {
      return true;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return getName() == null ? getType() : getName().hashCode() ^ getType();
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof Identity)) {
         return false;
      }

      Identity identity2 = (Identity) obj;
      return getName() != null && getName().equals(identity2.getName()) &&
         getType() == identity2.getType();
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone AbstractIdentify", ex);
      }

      return null;
   }

   /**
    * Get parent groups.
    */
   @Override
   public String[] getGroups() {
      return new String[0];
   }

   /**
    * Get roles assigned to the identity.
    */
   @Override
   public IdentityID[] getRoles() {
      return new IdentityID[0];
   }

   /**
    * Get the organization ID assigned to the identity
    */
   @Override
   public String getOrganizationID() {
      return null;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "Identity:[" + getName() + "," + getType() + "," + getOrganizationID() + "]";
   }

   /**
    * Get the identifier.
    */
   @Override
   public String toIdentifier() {
      return getType() + "-" + getName();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractIdentity.class);
}
