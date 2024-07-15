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
package inetsoft.util;

import java.util.HashMap;
import java.util.TimeZone;

/**
 * Utility methods for timezone.
 *
 * @version 13.1, 3/27/2019
 * @author InetSoft Technology Corp
 */
public class TimeZoneUtil {
   /**
    * tzdatabase only listed the etc/gmt timezone ids, so get the etc/gmt timezone id from the
    * mapping for the gmt timezone id to make sure client can get the right timezone info by
    * moment-timezone.
    */
   public static String getTimeZoneID(TimeZone tz) {
      String id = tz.getID();
      String key = id;

      if(id.indexOf(":") != -1) {
         key = key.substring(0, id.indexOf(":"));
      }

      if(idMappings.containsKey(key)) {
         return idMappings.get(key);
      }

      return id;
   }

   private static HashMap<String, String> idMappings = new HashMap<String, String>() {
      {
         put("GMT-0", "Etc/GMT+0");
         put("GMT-1", "Etc/GMT+1");
         put("GMT-2", "Etc/GMT+2");
         put("GMT-3", "Etc/GMT+3");
         put("GMT-4", "Etc/GMT+4");
         put("GMT-5", "Etc/GMT+5");
         put("GMT-6", "Etc/GMT+6");
         put("GMT-7", "Etc/GMT+7");
         put("GMT-8", "Etc/GMT+8");
         put("GMT-9", "Etc/GMT+9");
         put("GMT-10", "Etc/GMT+10");
         put("GMT-11", "Etc/GMT+11");
         put("GMT-12", "Etc/GMT+12");
         put("GMT-00", "Etc/GMT+0");
         put("GMT-01", "Etc/GMT+1");
         put("GMT-02", "Etc/GMT+2");
         put("GMT-03", "Etc/GMT+3");
         put("GMT-04", "Etc/GMT+4");
         put("GMT-05", "Etc/GMT+5");
         put("GMT-06", "Etc/GMT+6");
         put("GMT-07", "Etc/GMT+7");
         put("GMT-08", "Etc/GMT+8");
         put("GMT-09", "Etc/GMT+9");
         put("GMT+0", "Etc/GMT-0");
         put("GMT+1", "Etc/GMT-1");
         put("GMT+2", "Etc/GMT-2");
         put("GMT+3", "Etc/GMT-3");
         put("GMT+4", "Etc/GMT-4");
         put("GMT+5", "Etc/GMT-5");
         put("GMT+6", "Etc/GMT-6");
         put("GMT+7", "Etc/GMT-7");
         put("GMT+8", "Etc/GMT-8");
         put("GMT+9", "Etc/GMT-9");
         put("GMT+10", "Etc/GMT-10");
         put("GMT+11", "Etc/GMT-11");
         put("GMT+12", "Etc/GMT-12");
         put("GMT+00", "Etc/GMT-0");
         put("GMT+01", "Etc/GMT-1");
         put("GMT+02", "Etc/GMT-2");
         put("GMT+03", "Etc/GMT-3");
         put("GMT+04", "Etc/GMT-4");
         put("GMT+05", "Etc/GMT-5");
         put("GMT+06", "Etc/GMT-6");
         put("GMT+07", "Etc/GMT-7");
         put("GMT+08", "Etc/GMT-8");
         put("GMT+09", "Etc/GMT-9");
      }
   };
}