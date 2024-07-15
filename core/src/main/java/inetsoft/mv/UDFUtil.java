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
package inetsoft.mv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.XConstants;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// separate out to avoid class dependency
public class UDFUtil {
   // encode date range/part udf
   public static String encodeDateRange(String field, int option) {
      String op = ((option & XConstants.PART_DATE_GROUP) != 0) ? "datePart" : "dateRange";

      return "@udf{\"name\": \"" + op + "\", \"column\": \"" + field +
         "\", \"option\": " + option + "}";
   }

   // encode alias udf
   public static String encodeAlias(String field) {
      return "@udf{\"name\": \"alias\", \"column\": \"" + field + "\"}";
   }

   // get the udf definition from string embedded in comments
   public static Map<String,Object> extractUDF(String str) {
      int comment1 = str.indexOf("/*");
      int comment2 = str.indexOf("*/", comment1 + 2);

      if(comment1 >= 0 && comment2 > 0) {
         Pattern comment = Pattern.compile("/\\*((?:.|[\\n\\r])*?)\\*/");
         Matcher matcher = comment.matcher(str);

         try {
            if(matcher.find()) {
               String content = matcher.group(1);
               Pattern udf_def = Pattern.compile(".*@udf(\\{.*\\}).*");
               Matcher udf_matcher = udf_def.matcher(content);

               if(udf_matcher.find()) {
                  ObjectMapper mapper = new ObjectMapper();

                  try {
                     JsonNode json = mapper.readTree(udf_matcher.group(1));
                     return mapper.convertValue(json, Map.class);
                  }
                  catch(Exception ex) {
                     return null;
                  }
               }
            }
         }
         catch(StackOverflowError ex) {
            // ignore, could be caused by very long string with mangled chars
         }
      }

      return null;
   }
}
