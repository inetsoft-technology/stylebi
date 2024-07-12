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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.Catalog;
import inetsoft.util.TimeZoneUtil;
import org.immutables.value.Value;

import java.security.Principal;
import java.util.*;

@Value.Immutable
@JsonSerialize(as = ImmutableScheduleTaskList.class)
@JsonDeserialize(as = ImmutableScheduleTaskList.class)
public interface ScheduleTaskList {
   List<ScheduleTaskModel> tasks();
   String dateTimeFormat();
   String timeZone();
   String timeZoneId();
   long timeZoneOffset();
   boolean showOwners();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableScheduleTaskList.Builder {
      public Builder defaultTimeZone(Principal principal) {
         TimeZone tz = TimeZone.getDefault();
         Locale locale = Catalog.getCatalog(principal).getLocale();

         if(locale == null) {
            locale = Locale.getDefault();
         }

         timeZone(tz.getDisplayName(locale));
         timeZoneId(TimeZoneUtil.getTimeZoneID(tz));
         timeZoneOffset(tz.getOffset(System.currentTimeMillis()));

         return this;
      }

      public Builder timeFormat(String fmt) {
         dateTimeFormat(fmt);
         return this;
      }
   }
}
