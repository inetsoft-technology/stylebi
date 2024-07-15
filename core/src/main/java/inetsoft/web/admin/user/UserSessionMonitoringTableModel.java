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
package inetsoft.web.admin.user;

import inetsoft.report.internal.Util;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Catalog;

import java.util.Date;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableUserSessionMonitoringTableModel.class)
@JsonDeserialize(as = ImmutableUserSessionMonitoringTableModel.class)
public interface UserSessionMonitoringTableModel {
   String sessionID();
   String sessionLabel();

   String user();

   String userName();

   String address();

   String age();

   @Nullable
   String accessed();

   String roles();

   String groups();

   String organization();

   static UserSessionMonitoringTableModel.Builder builder() {
      return new UserSessionMonitoringTableModel.Builder();
   }

   class Builder extends ImmutableUserSessionMonitoringTableModel.Builder {
      public Builder from(SessionModel info, boolean lastAccessEnabled, Catalog catalog) {
         sessionID(info.id());
         sessionLabel(IdentityID.getIdentityIDFromKey(info.id()).name);
         user(info.user().name);
         address(info.address());
         age(Util.formatAge(new Date(info.dateCreated()), false));
         accessed(lastAccessEnabled ? Util.formatAge(new Date(info.dateAccessed()), false) : null);
         roles(info.roles().stream()
                  .map(r -> catalog.getString(r.getName()))
                  .collect(Collectors.joining(", ")));
         groups(info.groups().stream()
                   .map(catalog::getString)
                   .collect(Collectors.joining(", ")));
         organization(catalog.getString(info.organization()));
         userName(info.user().name);

         return this;
      }
   }
}
