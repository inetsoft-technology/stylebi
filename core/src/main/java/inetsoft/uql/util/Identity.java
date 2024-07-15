/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util;

import com.fasterxml.jackson.annotation.JsonValue;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;

import java.io.Serializable;

/**
 * This class defines the Identity.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public interface Identity extends Serializable, Cloneable {
   /**
    * Enumeration of the types of identities.
    */
   enum Type {
      USER(0),
      GROUP(1),
      ROLE(2),
      UNKNOWN_USER(3),
      ORGANIZATION(4);

      private final int code;

      Type(int code) {
         this.code = code;
      }

      @JsonValue
      public int code() {
         return code;
      }
   }

   /**
    * User.
    */
   int USER = 0;

   /**
    * Group.
    */
   int GROUP = 1;

   /**
    * Role.
    */
   int ROLE = 2;

   /**
    * Organization
    */
   int ORGANIZATION = 4;

   /**
    * Unknown user.
    */
   String UNKNOWN_USER = "unknown_user";

   /**
    * user suffix.
    */
   String USER_SUFFIX = "(User)";

   /**
    * group suffix.
    */
   String GROUP_SUFFIX = "(Group)";

   /**
    * Get the name of the Identity.
    */
   String getName();

   /**
    * Create one user.
    */
   XPrincipal create();

   /**
    * Get the type of the identity.
    */
   int getType();

   /**
    * Indicates whether or not this Identity is editable.
    */
   boolean isEditable();

   /**
    * Get roles assigned to the identity.
    */
   IdentityID[] getRoles();

   /**
    * Get parent groups.
    */
   String[] getGroups();

   /**
    * Get the organization assigned to the identity
    */
   String getOrganization();

   /**
    * Get the identityID object referencing this identity
    */
   IdentityID getIdentityID();

   /**
    * Get the identifier.
    */
   String toIdentifier();

   /**
    * Create a copy of this object.
    */
   Object clone();
}
