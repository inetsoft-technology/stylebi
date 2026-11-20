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
package inetsoft.uql.rest.pagination;

import inetsoft.uql.rest.HttpResponse;

import java.io.IOException;
import java.net.URL;

/**
 * Parses parameters from HTTP headers.
 */
class HeaderParameterParser extends AbstractParameterParser {
   HeaderParameterParser(HttpResponse response) {
      this.response = response;
   }
   
   @Override
   public int parseInt(String headerName) {
      return Integer.parseInt(parseString(headerName));
   }

   /**
    * Parses a header as a boolean. Headers such as GitLab's {@code x-next-page} carry a
    * value (the next page number) when there is a next page and are empty on the last page,
    * so a missing, blank, "false", "0" or "no" (case-insensitive) value is false and any
    * other value is true.
    */
   @Override
   public boolean parseBoolean(String headerName) {
      final String value = parseString(headerName);

      if(value == null) {
         return false;
      }

      final String trimmed = value.trim();
      return !trimmed.isEmpty() && !"false".equalsIgnoreCase(trimmed) &&
         !"0".equals(trimmed) && !"no".equalsIgnoreCase(trimmed);
   }

   @Override
   public String parseString(String headerName) {
      return response.getResponseHeaderValue(headerName);
   }

   @Override
   public URL parseURL(PaginationParameter param) throws IOException {
      String linkString = parseString(param.getValue());
      return linkString == null ? null : new URL(linkString);
   }

   private final HttpResponse response;
}
