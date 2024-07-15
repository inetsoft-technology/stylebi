/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.json;

import inetsoft.uql.tabular.RestParameter;

import java.util.*;

class TemplateParser {
   public TemplateParser(List<TemplateComponent> pathComponents,
                         Map<String, TemplateComponent> queryComponents)
   {
      this.pathComponents = pathComponents;
      this.queryComponents = queryComponents;
   }

   public void parse(String template) {
      List<String> pathAndQuery = split(template, '?', true);
      String pathString = pathAndQuery.get(0);
      String queryString = pathAndQuery.size() == 1 ? null : pathAndQuery.get(1);

      for(String part : split(pathString, '/')) {
         pathComponents.add(createTemplateComponent(part));
      }

      if(queryString != null) {
         for(String part : split(queryString, '&')) {
            int index = part.indexOf('=');

            if(index < 0) {
               queryComponents.put(part, new TemplateComponent(""));
            }
            else {
               String name = part.substring(0, index);
               String value = part.substring(index + 1);
               queryComponents.put(name, createTemplateComponent(value));
            }
         }
      }
   }

   private List<int[]> getTokenOffsets(String string) {
      List<int[]> offsets = new ArrayList<>();
      int start = string.indexOf('{');

      while(start >= 0 && start < string.length()) {
         int end = string.indexOf('}', start);

         if(end < 0) {
            break;
         }

         offsets.add(new int[] { start, end });
         start = string.indexOf('{', end + 1);
      }

      return offsets;
   }

   private boolean isInOffset(int index, List<int[]> offsets) {
      for(int[] offset : offsets) {
         if(index < offset[0]) {
            return false; // short circuit
         }

         if(index > offset[0] && index < offset[1]) {
            return true;
         }
      }

      return false;
   }

   private List<String> split(String string, char delimiter) {
      return split(string, delimiter, false);
   }

   private List<String> split(String string, char delimiter, boolean onlyOne) {
      List<String> list = new ArrayList<>();
      List<int[]> offsets = getTokenOffsets(string);
      int start = 0;

      while(start >= 0 && start < string.length()) {
         int end = string.indexOf(delimiter, start);

         while(end >= 0 && isInOffset(end, offsets)) {
            end = string.indexOf(delimiter, end + 1);
         }

         if(end < 0) {
            list.add(string.substring(start));
            break;
         }
         else {
            list.add(string.substring(start, end));
            start = end + 1;
         }

         if(onlyOne) {
            list.add(string.substring(start));
            break;
         }
      }

      return list;
   }

   private TemplateComponent createTemplateComponent(String part) {
      String token;
      String prefix = null;
      String extension = null;

      int start = part.indexOf('{');
      int end = part.lastIndexOf('}');

      if(start >= 0 && end >= 0) {
         if(start > 0) {
            prefix = part.substring(0, start);
         }

         if(end < part.length() - 1) {
            extension = part.substring(end + 1);
         }

         token = part.substring(start, end + 1);
      }
      else {
         token = part;
      }

      try {
         return new TemplateComponent(RestParameter.fromToken(token), prefix, extension);
      }
      catch(IllegalArgumentException ignore) {
      }

      return new TemplateComponent(part);
   }

   private final List<TemplateComponent> pathComponents;
   private final Map<String, TemplateComponent> queryComponents;
}
