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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.sree.security.Group;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Catalog;
import org.immutables.value.Value;

import java.util.Arrays;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableGroupEmailModel.class)
@JsonDeserialize(as = ImmutableGroupEmailModel.class)
public interface GroupEmailModel {
   String name();
   String label();
   List<String> memberOf();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableGroupEmailModel.Builder {
      public Builder from(IdentityID group, AuthenticationProvider provider, Catalog catalog) {
         Group groupObj = provider.getGroup(group);

         name(group.name);
         label(catalog.getString(group.name));


         String[] groups = provider.getGroup(group).getGroups();

         if(groups != null && groups.length > 0) {
            memberOf(Arrays.asList(groups));
         }

         return this;
      }
   }
}
