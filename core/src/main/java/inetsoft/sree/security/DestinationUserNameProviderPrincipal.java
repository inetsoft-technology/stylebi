/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import inetsoft.sree.ClientInfo;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;

public class DestinationUserNameProviderPrincipal
   extends SRPrincipal implements DestinationUserNameProvider
{
   public DestinationUserNameProviderPrincipal(SRPrincipal principal) {
      super(principal);
   }

   public DestinationUserNameProviderPrincipal(SRPrincipal principal, ClientInfo client) {
      super(principal, client);
   }

   public DestinationUserNameProviderPrincipal(ClientInfo client, IdentityID[] roles, String[] groups,
                                               String orgID, long secureID)
   {
      super(client, roles, groups, orgID, secureID);
   }

   public DestinationUserNameProviderPrincipal(ClientInfo client, IdentityID[] roles, String[] groups,
                                               String orgID, long secureID, String alias)
   {
      super(client, roles, groups, orgID, secureID, alias);
   }

   public DestinationUserNameProviderPrincipal(IdentityID user, IdentityID[] roles, String[] groups,
                                               String orgID, long secureID)
   {
      super(user, roles, groups, orgID, secureID);
   }

   @Override
   public String getDestinationUserName() {
      StringBuilder name = new StringBuilder();
      name.append(getName()).append('[').append(getSecureID()).append("]@");

      if(getUser() != null && getUser().getIPAddress() != null) {
         name.append(getUser().getIPAddress());
      }
      else {
         name.append("localhost");
      }

      return name.toString();
   }
}
