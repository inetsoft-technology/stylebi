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

import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.SuffixTemplate;
import inetsoft.uql.tabular.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointQueryDelegate<T extends EndpointJsonQuery.Endpoint> {
   public SuffixTemplate getSuffix(String endpoint,
                                   RestParameters parameters,
                                   HttpParameter[] additionalParameters,
                                   Map<String, T> endpointMap)
   {
      if(endpoint == null) {
         return null;
      }

      String templateString = getSuffixTemplate(endpoint, endpointMap);

      if(templateString == null) {
         return null;
      }

      RestParameters params = parameters == null ? new RestParameters() : parameters;
      SuffixTemplate template = new SuffixTemplate(templateString);

      for(RestParameter parameter : params.getParameters()) {
         if(parameter.getValue() != null && !parameter.getValue().trim().isEmpty()) {
            template.setVariable(parameter.getName(), parameter.getValue().trim());
         }
      }

      template.withAdditionalParameters(additionalParameters);
      return template;
   }

   public RestParameters getRestParameters(RestParameters parameters, String endpoint,
                                           ResourceBundle bundle, Map<String, T> endpointMap)
   {
      RestParameters params;

      if(parameters == null || !Objects.equals(endpoint, parameters.getEndpoint())) {
         params = createParameters(endpoint, parameters, endpointMap);
      }
      else {
         params = parameters;
      }

      if(bundle != null) {
         params = (RestParameters) params.clone();

         for(RestParameter parameter : params.getParameters()) {
            try {
               parameter.setLabel(bundle.getString(parameter.getName()));
            }
            catch(MissingResourceException e) {
               parameter.setLabel(parameter.getName());
            }
         }
      }

      return params;
   }

   public String[][] getEndpoints(ResourceBundle bundle, Map<String, T> endpointMap) {
      return getEndpoints(bundle, endpointMap.keySet());
   }

   /**
    * Gets the names of the endpoints that can be selected.
    *
    * @param bundle    the resource bundle
    * @param endpoints the collection of endpoints
    *
    * @return the endpoint names.
    */
   public String[][] getEndpoints(ResourceBundle bundle, Collection<String> endpoints) {
      return endpoints.stream()
         .map(key -> getEndpointTag(key, bundle))
         .toArray(String[][]::new);
   }

   public void copyInfo(TabularQuery query, RestParameters parameters) {
      if(query instanceof EndpointQuery) {
         if(parameters == null) {
            parameters = new RestParameters();
         }

         parameters.copyParameterValues(((EndpointQuery) query).getParameters());
      }
   }

   /**
    * Gets the URL suffix template for the specified endpoint.
    *
    * @param endpoint the name of the endpoint.
    *
    * @return the URL template.
    */
   private String getSuffixTemplate(String endpoint, Map<String, T> endpointMap) {
      T ep = endpointMap.get(endpoint);
      return ep == null ? null : ep.getSuffix();
   }

   private RestParameters createParameters(String endpoint, RestParameters parameters, Map<String, T> endpointMap) {
      RestParameters params = new RestParameters();
      params.setEndpoint(endpoint);
      String template = endpoint == null ? null : getSuffixTemplate(endpoint, endpointMap);

      if(template != null) {
         Pattern pattern = Pattern.compile("\\{[^}]+}");
         Matcher matcher = pattern.matcher(template);
         Set<String> added = new HashSet<>();

         while(matcher.find()) {
            String token = matcher.group(0);

            try {
               RestParameter param = RestParameter.fromToken(token);

               // some api (e.g. ZohoCRM DeletedRecords) uses the same parameter
               // multiple times. only show it once on gui
               if(added.contains(param.getName())) {
                  continue;
               }

               if(parameters != null) {
                  // keep the existing parameter values
                  param.setValue(parameters.getKnownParameterValue(param.getName()));
               }

               added.add(param.getName());
               params.getParameters().add(param);
            }
            catch(IllegalArgumentException e) {
               LOG.warn(
                  "Invalid parameter token for endpoint \"{}\" in REST URL template: {}",
                  endpoint, template, e);
            }
         }
      }

      return params;
   }

   private String[] getEndpointTag(String name, ResourceBundle bundle) {
      if(bundle != null) {
         try {
            return new String[]{ bundle.getString(name), name };
         }
         catch(MissingResourceException ignore) {
         }
      }

      return new String[]{ name, name };
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}