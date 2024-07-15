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
import inetsoft.util.Catalog;
import org.immutables.value.Value;

import java.util.Calendar;

@Value.Immutable
@Value.Modifiable
@JsonSerialize(as = ImmutableDistributionData.class)
@JsonDeserialize(as = ImmutableDistributionData.class)
public interface DistributionData {
   int index();
   String label();
   int hardCount();
   int softCount();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableDistributionData.Builder {
      public Builder weekday(int day, Catalog catalog) {
         index(day - 1);

         switch(day) {
         case Calendar.SUNDAY:
            label(catalog.getString("Sunday"));
            break;
         case Calendar.MONDAY:
            label(catalog.getString("Monday"));
            break;
         case Calendar.TUESDAY:
            label(catalog.getString("Tuesday"));
            break;
         case Calendar.WEDNESDAY:
            label(catalog.getString("Wednesday"));
            break;
         case Calendar.THURSDAY:
            label(catalog.getString("Thursday"));
            break;
         case Calendar.FRIDAY:
            label(catalog.getString("Friday"));
            break;
         case Calendar.SATURDAY:
            label(catalog.getString("Saturday"));
            break;
         }

         return this;
      }

      public Builder hour(int hour, Catalog catalog) {
         index(hour);
         label(String.format("%1$02d:00-%1$02d:59", hour));
         return this;
      }

      public Builder minute(int hour, int minute, Catalog catalog) {
         index(minute);
         label(String.format("%1$02d:%2$02d-%1$02d:%3$02d", hour, minute * 10, minute * 10 + 9));
         return this;
      }
   }
}
