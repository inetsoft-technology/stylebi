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
package inetsoft.web.service;

import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.util.XUtil;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
public class DateLevelExamplesController {
   @PostMapping("api/date-level-examples")
   public DateLevelExamplesModel getDateLevelExamples(@RequestParam("dataType") String dataType,
                                                      @RequestBody String[] items)
   {
      String[] levelExamples = getLevelExamples(items, dataType);
      return DateLevelExamplesModel.builder()
         .dateLevelExamples(levelExamples)
         .build();
   }

   private String[] getLevelExamples(String[] dateLevels, String dataType) {
      List<String> examples = new ArrayList<>();

      for(String dateLevel : dateLevels) {
         examples.add(getLevelExample(Integer.parseInt(dateLevel), dataType));
      }

      return examples.toArray(new String[examples.size()]);
   }

   public static String getLevelExample(int dateLevel, String dataType) {
      String example = "";

      if(dateLevel == -1 || dateLevel == XConstants.NONE_DATE_GROUP) {
         return example;
      }

      SimpleDateFormat defaultDateFormat = XUtil.getDefaultDateFormat(dateLevel, dataType);
      Calendar cal = Calendar.getInstance();

      if(dateLevel == -1) {
         return cal.getTime().toString();
      }

      Object data = DateRangeRef.getData(dateLevel, cal.getTime());

      if(defaultDateFormat != null && data instanceof Timestamp) {
         example = defaultDateFormat.format((Timestamp) data);
      }
      else {
         example = data.toString();
      }

      return example;
   }
}
