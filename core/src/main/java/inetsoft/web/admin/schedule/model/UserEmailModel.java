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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.*;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.*;

@Value.Immutable
@JsonSerialize(as = ImmutableUserEmailModel.class)
@JsonDeserialize(as = ImmutableUserEmailModel.class)
public interface UserEmailModel {
   IdentityID userID();
   @Nullable String email();
   List<String> memberOf();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableUserEmailModel.Builder {
      public Builder from(IdentityID user, AuthenticationProvider provider) {
         userID(user);

         User userObj = provider.getUser(user);
         String[] userEmails = userObj != null ? userObj.getEmails() : null;
         String emails = "";

         if(userEmails != null && userEmails.length > 0) {
            emails = String.join(",", userEmails);
         }

         email(emails);
         String[] groups = provider.getUser(user).getGroups();

         if(groups != null && groups.length > 0) {
            List<String> groupNames = new ArrayList<>();

            for(int i = 0; i < groups.length; i++) {
               Group group = provider.getGroup(new IdentityID(groups[i], user.organization));

               if(group != null && group.getName() != null) {
                  groupNames.add(group.getName());
               }
               else {
                  groupNames.add(groups[i]);
               }
            }

            memberOf(groupNames);
         }

         return this;
      }
   }
}
