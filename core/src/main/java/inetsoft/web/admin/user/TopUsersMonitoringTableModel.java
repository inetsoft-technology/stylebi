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
package inetsoft.web.admin.user;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.*;
import org.immutables.value.Value;

import java.util.Date;

@Value.Immutable
@JsonSerialize(as = ImmutableTopUsersMonitoringTableModel.class)
@JsonDeserialize(as = ImmutableTopUsersMonitoringTableModel.class)
public interface TopUsersMonitoringTableModel {
   IdentityID user();

   String reportCount();

   int viewsheetCount();

   String userName();

   String age();

   String organization();

   static TopUsersMonitoringTableModel.Builder builder() {
      return new TopUsersMonitoringTableModel.Builder();
   }

   class Builder extends ImmutableTopUsersMonitoringTableModel.Builder {
      public Builder from(TopUser topUser) {
         user(topUser.name());
         String reportString = topUser.executingReports() == 0 ?
            Integer.toString(topUser.activeReports()) :
            topUser.activeReports() + "(" + topUser.executingReports() + ")";
         reportCount(reportString);
         viewsheetCount(topUser.activeViewsheets());
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

         String ageString = topUser.age() == 0 ? "Logout" :
            Util.formatAge(new Date(topUser.age()), false);
         age(ageString);
         userName(topUser.name().name);
         String orgId = topUser.name().orgID;
         String orgName = orgId == null ? "" : provider.getOrgNameFromID(orgId);
         organization(orgName);

         return this;
      }
   }
}
