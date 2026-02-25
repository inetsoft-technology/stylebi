/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.json;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper for the RuntimeSheet prop map that handles polymorphic type serialization.
 * This wrapper embeds type information for each value, allowing types like Dimension,
 * Point2D.Double, etc. to be correctly serialized and deserialized without requiring
 * global default typing on the ObjectMapper.
 *
 * <p><b>SECURITY NOTE:</b> The deserializer allows instantiation of arbitrary classes
 * based on embedded type information. This is acceptable because the prop map stores
 * internal application state only and is never populated with user-supplied input.
 * The serialized data is only exchanged between trusted Ignite cluster nodes.
 * <b>DO NOT</b> use this wrapper for untrusted external data.</p>
 */
@JsonSerialize(using = TypedPropertyMapSerializer.class)
@JsonDeserialize(using = TypedPropertyMapDeserializer.class)
public class TypedPropertyMapWrapper {
   public TypedPropertyMapWrapper() {
      this.values = new HashMap<>();
   }

   public TypedPropertyMapWrapper(Map<String, Object> values) {
      this.values = values != null ? values : new HashMap<>();
   }

   public Map<String, Object> getValues() {
      return values;
   }

   public void setValues(Map<String, Object> values) {
      this.values = values;
   }

   private Map<String, Object> values;
}
