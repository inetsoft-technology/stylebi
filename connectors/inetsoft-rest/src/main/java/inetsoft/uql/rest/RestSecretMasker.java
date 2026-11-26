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
package inetsoft.uql.rest;

import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks credentials in text that is relayed to users or logs, such as the URL and body of a failed
 * REST request. Masking is applied to message strings only; request URIs used for pagination are
 * never modified.
 */
final class RestSecretMasker {
   static final String MASK = "***";

   private RestSecretMasker() {
   }

   /**
    * Masks secrets in text.
    *
    * @param text          the text to mask.
    * @param secretValues  literal secret values (for example the data source's password
    *                      properties) to be replaced wherever they occur, raw or URL-encoded.
    */
   static String mask(String text, Collection<String> secretValues) {
      if(text == null || text.isEmpty()) {
         return text;
      }

      String result = text;

      if(secretValues != null) {
         for(String secret : secretValues) {
            if(secret == null || secret.length() < 4) {
               continue;
            }

            result = result.replace(secret, MASK);
            final String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8);

            if(!encoded.equals(secret)) {
               result = result.replace(encoded, MASK);
            }
         }
      }

      final Matcher userInfo = USER_INFO.matcher(result);
      result = userInfo.replaceAll("$1" + MASK + "@");
      final Matcher params = SECRET_PARAM.matcher(result);
      return params.replaceAll("$1$2" + MASK);
   }

   /**
    * Collects the values of the data source's properties that are declared as passwords.
    */
   static List<String> secretValues(Object dataSource) {
      final List<String> values = new ArrayList<>();

      if(dataSource == null) {
         return values;
      }

      try {
         for(PropertyMeta meta : TabularUtil.findProperties(dataSource.getClass())) {
            if(meta.getProperty() != null && meta.getProperty().password()) {
               final Object value = meta.getValue(dataSource);

               if(value instanceof String s && !s.isEmpty()) {
                  values.add(s);
               }
            }
         }
      }
      catch(Exception ignore) {
         // masking by parameter name still applies
      }

      return values;
   }

   private static final Pattern USER_INFO = Pattern.compile("(://[^/\\s:@]+:)[^/\\s@]+@");
   private static final String NAMES =
      "sig|signature|api[_-]?key|apikey|key|token|[a-z_.-]*token|password|passwd|pwd|secret|" +
      "client[_-]?secret|code|x-amz-signature|x-amz-credential|x-amz-security-token";
   private static final Pattern SECRET_PARAM = Pattern.compile(
      "([?&;])((?:" + NAMES + ")=)[^&\\s\"'<>]*", Pattern.CASE_INSENSITIVE);
}
