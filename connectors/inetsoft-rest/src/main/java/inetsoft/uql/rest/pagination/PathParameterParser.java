/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.pagination;

import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.InputTransformer;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;

/**
 * Parses parameters from a JSON or XML response using JSONPath or XPath
 */
class PathParameterParser extends AbstractParameterParser {
   PathParameterParser(InputTransformer transformer, Object json, HttpResponse response) {
      this.transformer = transformer;
      this.json = json;
      this.response = response;
   }

   @Override
   public int parseInt(String jsonPath) {
      final Object value = transform(json, jsonPath);
      return value instanceof String ? Integer.parseInt((String) value) : (int) value;
   }

   @Override
   public boolean parseBoolean(String jsonPath) {
      final Object value = transform(json, jsonPath);
      return value != null && !Boolean.FALSE.equals(value);
   }

   @Override
   public String parseString(String jsonPath) {
      final Object value = transform(json, jsonPath);
      return value != null ? value.toString() : null;
   }

   @Override
   public URL parseURL(PaginationParameter param) throws IOException {
      final String urlStr = parseString(param.getValue());

      if(urlStr == null) {
         return null;
      }

      if(response == null || response.getURI() == null) {
         return new URL(urlStr);
      }

      URI current = URI.create(response.getURI());
      return current.resolve(urlStr).toURL();
   }

   private Object transform(Object json, String jsonPath) {
      final Object obj = transformer.transform(json, jsonPath);

      if(obj instanceof List) {
         final List list = (List) obj;

         if(list.isEmpty()) {
            return null;
         }
         else {
            return list.get(0);
         }
      }
      else {
         return obj;
      }
   }

   private final InputTransformer transformer;
   private final Object json;
   private final HttpResponse response;
}
