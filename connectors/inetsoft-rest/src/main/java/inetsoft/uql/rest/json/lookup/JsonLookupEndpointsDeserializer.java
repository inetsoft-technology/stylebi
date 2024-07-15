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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import java.io.IOException;
import java.util.*;

public class JsonLookupEndpointsDeserializer extends JsonDeserializer<List<JsonLookupEndpoint>> {
   @SuppressWarnings("unchecked")
   @Override
   public List<JsonLookupEndpoint> deserialize(JsonParser parser, DeserializationContext ctxt)
      throws IOException
   {
      if(parser.getCurrentToken() != JsonToken.START_ARRAY) {
         throw new IllegalStateException("Expected an array of JsonLookupEndpoints.");
      }

      List<JsonLookupEndpoint> lookupEndpoints = new ArrayList<>();

      while(parser.nextToken() != JsonToken.END_ARRAY) {
         assert parser.getCurrentToken() == JsonToken.START_OBJECT;
         List<String> endpoints = new ArrayList<>();
         JsonLookupEndpoint.Builder builder = JsonLookupEndpoint.builder();

         while(parser.nextToken() != JsonToken.END_OBJECT) {
            assert parser.getCurrentToken() == JsonToken.FIELD_NAME;
            String field = parser.getCurrentName();
            parser.nextToken();

            switch(field) {
            case "endpoint":
               endpoints.add(parser.getValueAsString());
               break;
            case "endpoints":
               readEndpointNames(parser, endpoints);
               break;
            case "parameterName":
               builder.parameterName(parser.getValueAsString());
               break;
            case "jsonPath":
               builder.jsonPath(parser.getValueAsString());
               break;
            case "key":
               builder.key(parser.getValueAsString());
               break;
            case "parameters":
               builder.parameters(ctxt.readValue(parser, Map.class));
               break;
            case "inheritParameters":
               builder.inheritParameters(parser.getValueAsBoolean());
               break;
            default:
               throw new UnrecognizedPropertyException(
                  parser, "Unrecognized property:" + field, parser.getCurrentLocation(),
                  JsonLookupEndpoint.class, field, Arrays.asList(
                     "endpoint", "endpoints", "parameterName", "jsonPath", "key", "parameters"));
            }
         }

         for(String endpoint : endpoints) {
            lookupEndpoints.add(builder.endpoint(endpoint).build());
         }
      }

      return lookupEndpoints;
   }

   void readEndpointNames(JsonParser parser, List<String> endpoints) throws IOException {
      assert parser.getCurrentToken() == JsonToken.START_ARRAY;

      while(parser.nextToken() != JsonToken.END_ARRAY) {
         endpoints.add(parser.getValueAsString());
      }
   }
}
