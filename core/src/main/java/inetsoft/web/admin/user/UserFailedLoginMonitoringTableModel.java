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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import org.immutables.value.Value;

import java.text.SimpleDateFormat;

@Value.Immutable
@JsonSerialize(as = ImmutableUserFailedLoginMonitoringTableModel.class)
@JsonDeserialize(as = ImmutableUserFailedLoginMonitoringTableModel.class)
public interface UserFailedLoginMonitoringTableModel {
   String user();

   String address();

   String time();

   static UserFailedLoginMonitoringTableModel.Builder builder() {
      return new UserFailedLoginMonitoringTableModel.Builder();
   }

   class Builder extends ImmutableUserFailedLoginMonitoringTableModel.Builder {
      public Builder from(FailedLoginModel info) {
         user(info.user() != null ? info.user().getName() : "");
         address(info.address());

         // @by jasonshobe, don't cache date formats, they are not thread safe
         SimpleDateFormat dateTimeFormat = Tool.getDateTimeFormat();
         long time0 = info.timestamp();
         String timeStr = dateTimeFormat.format(time0);
         time(timeStr);

         return this;
      }
   }
}
