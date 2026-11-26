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
package inetsoft.uql.rest.json.lookup;

import com.jayway.jsonpath.spi.json.JsonProvider;
import inetsoft.uql.rest.EndpointQuery;
import inetsoft.uql.rest.json.*;
import inetsoft.uql.rest.xml.parse.*;
import inetsoft.uql.tabular.*;

import java.net.URLDecoder;
import java.util.*;

public class LookupService {
   public <T extends EndpointJsonQuery.Endpoint> EndpointQuery createQueryFromLookupQuery(
      EndpointJsonQuery<T> query, EndpointJsonQuery<?> parentQuery,
      JsonLookupQuery lookupQuery, Object jsonEntity)
   {
      final JsonProvider jsonProvider = JsonTransformer.getJsonProvider();

      if(!jsonProvider.isMap(jsonEntity)) {
         throw new IllegalStateException("Only lookups on json objects are supported.");
      }

      final EndpointJsonQuery<T> newQuery = ((EndpointJsonQuery<T>) query.clone());

      final JsonLookupEndpoint lookupEndpoint = lookupQuery.getLookupEndpoint();
      final String id = Optional
         .ofNullable(getMapValue(jsonEntity, lookupEndpoint.key(), parentQuery))
         .map(Object::toString)
         .orElse(null);

      return createEndpointQuery(parentQuery, lookupQuery, jsonEntity, newQuery, lookupEndpoint, id);
   }

   public RestJsonQuery createQueryFromLookupQuery(
      RestJsonQuery query, RestJsonQuery parentQuery,
      JsonLookupQuery lookupQuery, Object jsonEntity)
   {
      final CustomJsonLookupEndpoint lookupEndpoint = (CustomJsonLookupEndpoint) lookupQuery.getLookupEndpoint();
      final JsonProvider jsonProvider = JsonTransformer.getJsonProvider();
      boolean keyMissing = false;

      if(lookupEndpoint.key().isEmpty() && jsonEntity instanceof String) {
         query.setLookupValue(parentQuery.getLookupDepth(), (String) jsonEntity);
      }
      else {
         if(!jsonProvider.isMap(jsonEntity)) {
            throw new IllegalStateException("Only lookups on json objects are supported.");
         }

         final String id = Optional
            .ofNullable(getMapValue(jsonEntity, lookupEndpoint.key(), null))
            .map(Object::toString)
            .orElse(null);

         query.setLookupValue(parentQuery.getLookupDepth(), id);
         keyMissing = id == null && !lookupEndpoint.key().isEmpty();
      }

      SuffixTemplate template = new SuffixTemplate(lookupEndpoint.url());

      // the entity doesn't have the configured key, skip its lookup like the endpoint queries do
      // instead of failing the query or requesting the URL without the key
      if(keyMissing && usesVariable(template, lookupEndpoint.parameterName())) {
         return null;
      }

      for(int i = 0; i < parentQuery.getLookupDepth() + 1; i ++) {
         if(query.getCustomLookup(i) != null) {
            template.setVariable(query.getCustomLookup(i).parameterName(), query.getLookupValue(i));
         }
      }

      String suffix = template.build();

      if(lookupEndpoint.ignoreBaseURL()) {
         try {
            suffix = URLDecoder.decode(suffix, "UTF-8");
         }
         catch(Exception e) {
            // ignore it
         }
      }

      final RestJsonQuery newQuery = query.clone();
      newQuery.setSuffix(suffix);
      newQuery.setLookupDepth(parentQuery.getLookupDepth() + 1);
      newQuery.setJsonPath(lookupQuery.getJsonPath());
      newQuery.setExpandTop(lookupQuery.isTopLevelOnly());
      newQuery.setExpanded(lookupQuery.isExpandArrays());
      newQuery.setIgnoreBaseUrl(lookupEndpoint.ignoreBaseURL());

      return newQuery;
   }

   public <T extends EndpointQuery> EndpointQuery createXmlLookupQuery(EndpointQuery query,
                                                                       EndpointQuery parentQuery,
                                                                       JsonLookupQuery lookupQuery,
                                                                       MapNode entity)
   {
      final EndpointQuery newQuery = (T) query.clone();
      final JsonLookupEndpoint lookupEndpoint = lookupQuery.getLookupEndpoint();
      final Object valueNode = entity.get(lookupEndpoint.key());

      // the entity doesn't have the key element, skip its lookup
      if(valueNode == null) {
         return null;
      }

      if(valueNode instanceof ValueNode) {
         final Object nodeValue = ((ValueNode) valueNode).unwrap();
         final String id = nodeValue != null ? nodeValue.toString() : null;
         return createEndpointQuery(parentQuery, lookupQuery, entity, newQuery, lookupEndpoint, id);
      }
      else {
         throw new IllegalStateException(String.format("Expected value %s was missing from entity.",
            lookupEndpoint.key()));
      }
   }

   /**
    * Move a field of an entity that has the name a lookup result is attached under, so that the
    * lookup doesn't overwrite it. The field is renamed to "name (Field)", or to
    * "name (Field n)" if that is taken. The match is case-sensitive.
    *
    * @param entity the entity the lookup result is attached to.
    * @param name   the name of the lookup result.
    *
    * @return the new name of the moved field, or null if nothing was moved.
    */
   @SuppressWarnings("unchecked")
   public String moveCollidingField(Object entity, String name) {
      if(!(entity instanceof Map) || name == null) {
         return null;
      }

      final Map<String, Object> map = (Map<String, Object>) entity;

      if(!map.containsKey(name)) {
         return null;
      }

      String newName = name + " (Field)";

      for(int n = 2; map.containsKey(newName); n++) {
         newName = name + " (Field " + n + ")";
      }

      map.put(newName, map.remove(name));
      return newName;
   }

   /**
    * Check if a suffix template references a variable, in the path or in the query.
    */
   private static boolean usesVariable(SuffixTemplate template, String name) {
      for(TemplateComponent part : template.getPath()) {
         if(part.isVariable() && name.equals(part.getValue())) {
            return true;
         }
      }

      for(TemplateComponent part : template.getQuery().values()) {
         if(part.isVariable() && name.equals(part.getValue())) {
            return true;
         }
      }

      return false;
   }

   private EndpointQuery createEndpointQuery(EndpointQuery parentQuery, JsonLookupQuery lookupQuery,
                                             Object entity, EndpointQuery newQuery,
                                             JsonLookupEndpoint lookupEndpoint, String id)
   {
      if(id == null) {
         return null;
      }

      newQuery.setEndpoint(lookupEndpoint.endpoint());
      final RestParameters restParameters = new RestParameters();
      final RestParameter restParameter = new RestParameter();
      List<RestParameter> oparams = lookupEndpoint.inheritParameters()
         ? new ArrayList<>(parentQuery.getParameters().getParameters()) : new ArrayList<>();

      if(lookupEndpoint.parameters() != null) {
         lookupEndpoint.parameters().entrySet().forEach(entry -> {
            RestParameter param = new RestParameter();
            String value = entry.getValue();

            if(value.startsWith("$.")) {
               Object fieldValue = getMapValue(entity, value, parentQuery);
               value = fieldValue == null ? null : fieldValue.toString();
            }

            param.setName(entry.getKey());
            param.setValue(value);
            oparams.add(param);
         });
      }

      restParameter.setName(lookupEndpoint.parameterName());
      restParameter.setValue(id);
      oparams.add(restParameter);
      restParameters.setParameters(oparams);
      newQuery.setParameters(restParameters);

      newQuery.setJsonPath(lookupQuery.getJsonPath());
      newQuery.setExpanded(lookupQuery.isExpandArrays());
      newQuery.setExpandTop(lookupQuery.isTopLevelOnly());
      newQuery.setLookupQueries(lookupQuery.getLookupQueries());

      if(!lookupEndpoint.inheritParameters() && (newQuery instanceof EndpointJsonQuery)) {
         ((EndpointJsonQuery<?>) newQuery).setAdditionalParameters(new HttpParameter[0]);
      }

      return newQuery;
   }

   private Object getMapValue(Object json, String key, EndpointQuery query) {
      if(key.startsWith("$(") && key.endsWith(")") && query != null) {
         String var = key.substring(2, key.length() - 1);
         RestParameter param = query.getParameters().findParameter(var);
         return param.getValue();
      }

      if(key.startsWith("$.")) {
         key = key.substring(2);
      }

      Object val = JsonTransformer.getJsonProvider().getMapValue(json, key);

      if(val == JsonProvider.UNDEFINED && key.contains(".")) {
         int dot = key.indexOf('.');
         String first = key.substring(0, dot);

         val = JsonTransformer.getJsonProvider().getMapValue(json, first);

         if(val != JsonProvider.UNDEFINED) {
            return getMapValue(val, key.substring(dot + 1), query);
         }
      }

      return val == JsonProvider.UNDEFINED ? null : val;
   }
}
