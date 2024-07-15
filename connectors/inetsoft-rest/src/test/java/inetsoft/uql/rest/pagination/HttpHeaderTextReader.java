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
package inetsoft.uql.rest.pagination;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads HTTP headers from a text file for use in testing.
 *
 * The text file is expected to be formatted as below:
 *
 * {headerName1}: {headerValue1}
 * {headerName2}: {headerValue2}
 * . . .
 */
public class HttpHeaderTextReader {
   public List<HttpHeader> read(InputStream input) throws IOException {
      final List<HttpHeader> headers = new ArrayList<>();
      final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String line;

      while((line = reader.readLine()) != null) {
         final String[] parts = line.split(": ");

         if(parts.length == 2) {
            headers.add(new HttpHeader(parts[0], parts[1]));
         }
      }

      reader.close();
      return headers;
   }
}
