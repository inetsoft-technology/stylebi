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

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.TimeZone;

@Value.Immutable
@JsonSerialize(as = ImmutableTimeZoneModel.class)
@JsonDeserialize(as = ImmutableTimeZoneModel.class)
public interface TimeZoneModel {
   String timeZoneId();
   @Nullable String label();
   @Nullable String hourOffset();

   static TimeZoneModel.Builder builder() {
      return new TimeZoneModel.Builder();
   }

   final class Builder extends ImmutableTimeZoneModel.Builder {
   }

   static ArrayList<TimeZoneModel> getTimeZoneOptions() {
      String[] tZIds = new String[] {
        "Pacific/Midway",
        "Pacific/Honolulu",
        "Pacific/Marquesas",
        "America/Adak",
        "America/Anchorage",
        "US/Pacific",
        "US/Mountain",
        "US/Central",
        "US/Eastern",
        "America/Araguaina",
        "America/Argentina/Buenos_Aires",
        "America/St_Johns",
        "America/Godthab",
        "Atlantic/Cape_Verde",
        "Greenwich",
        "WET",
        "Africa/Bangui",
        "Poland",
        "Asia/Amman",
        "Europe/Moscow",
        "Asia/Jerusalem ",
        "Africa/Juba",
        "Asia/Yerevan",
        "Iran",
        "Asia/Karachi",
        "IST",
        "Asia/Kathmandu",
        "Asia/Dacca",
        "Asia/Rangoon",
        "Asia/Bangkok",
        "Asia/Shanghai",
        "Australia/Eucla",
        "Asia/Tokyo",
        "Australia/Adelaide",
        "Australia/ACT",
        "Australia/LHI",
        "Pacific/Guadalcanal",
        "Pacific/Fiji",
        "NZ-CHAT",
        "Pacific/Enderbury",
        "Pacific/Kiritimati"
      };

      ArrayList<TimeZoneModel> tzList = new ArrayList<>();
      tzList.add(TimeZoneModel.builder()
                    .timeZoneId(TimeZone.getDefault().getID())
                    .label(Catalog.getCatalog().getString("Default"))
                    .hourOffset(Catalog.getCatalog().getString(""))
                    .build());

      LocalDateTime now = LocalDateTime.now();

      for(String id : tZIds) {
         TimeZone tz = TimeZone.getTimeZone(id);
         String offset = now.atZone(tz.toZoneId()).getOffset()
            .getId()
            .replace("Z", "+00:00");
         offset = "(UTC" + offset + ")";

         tzList.add(TimeZoneModel.builder()
                       .timeZoneId(id)
                       .label(tz.getDisplayName())
                       .hourOffset(offset)
                       .build());
      }

      return tzList;
   }
}
