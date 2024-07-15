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
package inetsoft.report.internal.binding;

import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * RealtimeGenerator, preprocess element, create all runtime objects from
 * design time, include binding attr, layout and so on.
 */
public class RealtimeGenerator {

   /**
    * Parse used field name from script.
    */
   public static String[] parseFieldFromScript(String value) {
      return parseFieldFromScript(value, "data");
   }

   /**
    * Parse used field name from script.
    */
   private static String[] parseFieldFromScript(String value, String var) {
      Set<String> fields = new HashSet<>();

      // toList(data['a@...']) the 'a'
      fields.addAll(Tool.extractRegex(
         var + "\\[['\"]([^@'\"\\?]*)[@'\"\\?]", value));

      // toList(data['a@state:$state;city:$city']) the @ part
      List<String> at = Tool.extractRegex(
         var + "\\[.*@(([^:]*:[^;'\"\\?]*[;]*)*)", value);

      for(String str : at) {
         String[] arr = Tool.split(str, ';');

         for(String pairstr : arr) {
            String[] pair = Tool.split(pairstr, ':');

            if(pair.length != 2) {
               continue;
            }

            if(pair[0].startsWith("=")) {
               fields.addAll(parseFieldsFromExpression(pair[0].substring(1)));
            }
            else {
               fields.add(pair[0]);
            }
         }
      }

      // extract the ? condition
      List<String> conds = Tool.extractRegex(
         var + "\\[(['\"])[^'\"\\?]*\\?(.*)\\1", value);

      for(String cond : conds) {
         if(cond.equals("'") || cond.equals("\"")) {
            continue;
         }

         fields.addAll(parseFieldsFromExpression(cond));
      }

      // field['a'] or field["b"]
      fields.addAll(Tool.extractRegex("field\\[(['\"])(.*)\\1\\]", value));

      // toList(data['*'], 'field=state');
      fields.addAll(Tool.extractRegex("[,'\"]field=([^,'\"]*)", value));

      // data['id@STATE:$STATE;CITY:...'] (55657)
      fields.addAll(Tool.extractRegex("[@;]([^,'\";]*):", value));

      // for multisource
      List<String> list = Tool.extractRegex("[,'\"]fields=([^,'\"]*)", value);

      for(int i = 0; i < list.size(); i++) {
         String str = list.get(i);

         if(!StringUtils.isEmpty(str)) {
            String[] arr = str.split(":");
            fields.addAll(Arrays.asList(arr));
         }
      }

      // sorton=state
      for(String sorton : Tool.extractRegex("[,'\"]sorton=([^,'\"]*)", value)) {
         if(sorton.indexOf('(') < 0) {
            fields.add(sorton);
         }
      }

      // sorton=correl(quantity,discount)
      for(String sorton : Tool.extractRegex("[,'\"]sorton=\\w*\\(([^)]*)\\)", value)) {
         for(String v : Tool.split(sorton, ',')) {
            fields.add(v);
         }
      }

      return fields.toArray(new String[0]);
   }

   /**
    * Parse (potential) fields from a javascript expression.
    */
   private static List<String> parseFieldsFromExpression(String expr) {
      String pattern = "field\\['(.*)'\\]|field\\[\"(.*)\"\\]|" +
         "rowValue\\['(.*)'\\]|rowValue\\[\"(.*)\"\\]|'.*'|\".*\"|(\\w*)";
      List<String> names = Tool.extractRegex(pattern, expr);

      for(int i = 0; i < names.size(); i++) {
         String name = names.get(i);

         if(name.equals("field") || name.equals("rowValue")) {
            names.remove(i--);
         }
      }

      return names;
   }

}
