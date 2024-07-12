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
package inetsoft.uql.rest.json.lookup;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Data class defining a json lookup endpoint.
 */
@Value.Immutable
@JsonDeserialize(as = ImmutableJsonLookupEndpoint.class)
public interface JsonLookupEndpoint {
   /**
    * The name of the lookup endpoint. This endpoint must match an endpoint name defined
    * in the endpoints.json.
    *
    * Alternatively, in endpoints.json, you can specify a field "endpoints" and populate it
    * with an array of string endpoints, and the deserializer will flatten the entry into
    * individual {@link JsonLookupEndpoint}s, using the other fields as prototypes.
    */
   String endpoint();

   /**
    * The name of the parameter in the lookup endpoint that will be substituted with the
    * parent endpoint’s entity identifier.
    */
   String parameterName();

   /**
    * The jsonPath to use to transform the parent endpoint’s data into an entity or
    * list of entities, e.g. "$.customers.[*]".
    */
   String jsonPath();

   /**
    * Assuming that an entity is an json object, this key denotes the object’s field that
    * will be used as the parameter for the lookup query, e.g. "id". It can reference a value
    * in nested field by using path name, optionally prefixed the field path with "$."
    * (e.g. subscription.id or $.subscription.id). In addition, a key can also refer to a
    * parameter passed in from the parent query, using the variable notation $(parameterName).
    */
   String key();

   /**
    * Additional hardcoded or field parameters to pass to the endpoint query. If the value starts
    * with '$.' (e.g. $.product_id), it's treated as a field name and value is retrieved
    * from the json object. The use of field value is different from key in that it's not
    * treated as required, so a missing value will not cause lookup query to be ignored.
    */
   @Nullable
   Map<String, String> parameters();

   /**
    * By default, parent query parameters are passed down to lookup queries. Set to false to
    * disable the parameter inheritance.
    */
   @Value.Default
   default boolean inheritParameters() {
      return true;
   }

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableJsonLookupEndpoint.Builder {
   }
}
