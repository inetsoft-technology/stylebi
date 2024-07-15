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

import inetsoft.uql.rest.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser which only extracts the URL from the Link response header as defined by RFC 8288.
 * The user must specify the link relation to use, as there may be multiple urls present in the
 * header.
 */
public class LinkHeaderParameterParser extends AbstractParameterParser {
   public LinkHeaderParameterParser(HttpResponse response) {
      this.response = response;
   }

   @Override
   public URL parseURL(PaginationParameter param) throws IOException {
      final String value = response.getResponseHeaderValue(param.getValue());

      if(value == null) {
         return null;
      }

      final String linkRelation = param.getLinkRelation();
      Pattern headerPattern = Pattern.compile("(<[^>]*>(;\\s*\\w+=\"[^\"]*\")+)");
      Pattern linkPattern = Pattern.compile("<(.*)>;(.*)");
      String uriPattern = "(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
      Pattern attrPattern = Pattern.compile(
         "(\\w+)=\"(\\p{Lower}[\\p{Lower}\\p{Digit}.\\-\\s]*|" + uriPattern + ")\"");
      Matcher headerMatcher = headerPattern.matcher(value);

      while(headerMatcher.find()) {
         Matcher linkMatcher = linkPattern.matcher(headerMatcher.group(1));

         if(linkMatcher.matches()) {
            String uri = linkMatcher.group(1);
            Matcher attrMatcher = attrPattern.matcher(linkMatcher.group(2));

            while(attrMatcher.find()) {
               if("rel".equals(attrMatcher.group(1)) && linkRelation.equals(attrMatcher.group(2))) {
                  return URI.create(uri).toURL();
               }
            }
         }
      }

      return null;
   }

   private final HttpResponse response;
}
