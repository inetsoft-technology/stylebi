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

import inetsoft.uql.tabular.HttpParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.util.*;

/**
 * Class that parses a suffix template and allows replacement of variables.
 */
public class SuffixTemplate {
   public SuffixTemplate(String template) {
      if(template == null || template.isEmpty()) {
         throw new IllegalArgumentException("Invalid suffix template: " + template);
      }

      leadingSlash = template.charAt(0) == '/';
      path = new ArrayList<>();
      query = new LinkedHashMap<>();
      variables = new HashMap<>();
      TemplateParser parser = new TemplateParser(path, query);
      parser.parse(leadingSlash ? template.substring(1) : template);
   }

   public List<TemplateComponent> getPath() {
      return path;
   }

   public Map<String, TemplateComponent> getQuery() {
      return query;
   }

   public String getVariable(String name, String value) {
      return variables.get(name);
   }

   public void setVariable(String name, String value) {
      variables.put(name, value);
   }

   public void withAdditionalParameters(HttpParameter[] additionalParameters) {
      if(additionalParameters != null) {
         this.additionalParameters = additionalParameters;
      }
      else {
         this.additionalParameters = new HttpParameter[0];
      }
   }

   public String build() {
      StringBuilder suffix = new StringBuilder();

      boolean first = true;

      for(TemplateComponent part : path) {
         if(part.isVariable()) {
            String variable = variables.get(part.getValue());

            if(variable != null) {
               if(!first || leadingSlash) {
                  suffix.append('/');
               }

               if(part.getPrefix() != null) {
                  suffix.append(part.getPrefix());
               }

               suffix.append(encodeParameter(variable, false));

               if(part.getExtension() != null) {
                  suffix.append(part.getExtension());
               }

               first = false;
            }
            else if(part.isRequired()) {
               throw new IllegalStateException(
                  "Required parameter \"" + part.getValue() + "\" is missing");
            }
         }
         else {
            if(!first || leadingSlash) {
               suffix.append('/');
            }

            suffix.append(part.getValue());
            first = false;
         }
      }

      first = true;

      for(Map.Entry<String, TemplateComponent> e : query.entrySet()) {
         String name = e.getKey();
         TemplateComponent part = e.getValue();

         if(part.isVariable()) {
            String variable = variables.get(part.getValue());

            if(variable != null) {
               if(part.isSplit()) {
                  for(String value : variable.split(",")) {
                     if(first) {
                        suffix.append('?');
                        first = false;
                     }
                     else {
                        suffix.append('&');
                     }

                     suffix.append(name).append('=').append(encodeParameter(value, true));
                  }
               }
               else {
                  if(first) {
                     suffix.append('?');
                     first = false;
                  }
                  else {
                     suffix.append('&');
                  }

                  suffix.append(name).append('=').append(encodeParameter(variable, true));
               }
            }
            else if(part.isRequired()) {
               throw new IllegalStateException(
                  "Required parameter \"" + part.getValue() + "\" is missing");
            }
         }
         else {
            if(first) {
               suffix.append('?');
               first = false;
            }
            else {
               suffix.append('&');
            }

            suffix.append(name).append('=').append(part.getValue());
         }
      }

      for(HttpParameter param : additionalParameters) {
         String name = param.getName();
         String value = param.getValue();

         if(first) {
            suffix.append('?');
            first = false;
         }
         else {
            suffix.append('&');
         }

         suffix.append(name).append('=').append(encodeParameter(value, true));
      }

      return suffix.toString();
   }

   private String encodeParameter(String value, boolean parameter) {
      if(value == null) {
         return null;
      }
      
      try {
         String str = URLEncoder.encode(value, "UTF-8");
         // uri encode space as %20 and query parameter as +
         return parameter ? str : str.replace("+", "%20");
      }
      catch(Exception e) {
         LOG.warn("Failed to encode parameter value: {}", value, e);
         return value;
      }
   }

   private final boolean leadingSlash;
   private final List<TemplateComponent> path;
   private final Map<String, TemplateComponent> query;
   private final Map<String, String> variables;
   private HttpParameter[] additionalParameters = {};

   private static final Logger LOG = LoggerFactory.getLogger(SuffixTemplate.class);
}
