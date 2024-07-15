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
package inetsoft.uql.rest.xml.xslt;

import com.helger.xml.transform.StringStreamSource;
import inetsoft.util.Tool;

import java.io.*;
import java.util.Map;

public class XSLTParamTransformer {
   /**
    * Replace xslt parameter placeholders with the supplied parameters.
    */
   public StringStreamSource transform(InputStream input, Map<String, String> params)
      throws IOException
   {
      final StringBuilder stringBuilder = new StringBuilder();
      final BufferedReader reader = new BufferedReader(new InputStreamReader(input));

      while(reader.ready()) {
         String line = reader.readLine();

         for(final Map.Entry<String, String> entry : params.entrySet()) {
            final String paramName = entry.getKey();
            final String paramValue = entry.getValue();
            line = line.replace(paramName, Tool.encodeHTMLAttribute(paramValue));
         }

         stringBuilder.append(line);
      }

      return new StringStreamSource(stringBuilder.toString());
   }
}
