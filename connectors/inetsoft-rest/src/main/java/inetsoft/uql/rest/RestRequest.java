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

   /**
    * The key of the cached response of the request. It includes everything that selects the
    * response: the method, content type and query type (Accept header) as well as the
    * credentials applied by the authenticator (Bug #81931). The HTTP parameters, body and
    * credentials are hashed so that the key, which is also the name of the cache file, does
    * not contain secrets.
    */
   @Value.Derived
   default String key() {
      final AbstractRestQuery query = query();
      final AbstractRestDataSource dataSource = dataSource();

      return url() + ":" + query.getURL() + query.getSuffix() + ":" +
             queryParameters() + ":" + urlVariables() + ":" +
             query.getRequestType() + ":" +
             (query.isPostRequest() ? query.getContentType() : "") + ":" +
             query.getClass().getName() + ":" +
             AbstractRestDataSource.hashCacheKeyParts(
                AbstractRestDataSource.encodeHttpParameters(dataSource.getQueryHttpParameters()),
                query.getRequestBody(), dataSource.getResponseCacheIdentity());
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
