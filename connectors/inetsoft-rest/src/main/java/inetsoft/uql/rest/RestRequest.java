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

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.*;

@Value.Immutable
public interface RestRequest {
   AbstractRestQuery query();

   @Value.Default
   default Map<String, String> queryParameters() {
      return Collections.emptyMap();
   }

   @Value.Default
   default Map<String, String> urlVariables() {
      return Collections.emptyMap();
   }

   @Value.Derived
   default String key() {
      return url() + ":" + query().getURL() + query().getSuffix() + ":" +
             queryParameters() + ":" + urlVariables() + ":" +
             Arrays.deepToString(dataSource().getQueryHttpParameters()) + ":" +
             query().getRequestBody();
   }

   @Nullable
   URL url();

   @Value.Derived
   default AbstractRestDataSource dataSource() {
      return (AbstractRestDataSource) query().getDataSource();
   }

   static Builder builder() {
      return new Builder();
   }

   static RestRequest fromQuery(AbstractRestQuery query) {
      return builder().query(query).build();
   }

   class Builder extends ImmutableRestRequest.Builder {
   }
}
