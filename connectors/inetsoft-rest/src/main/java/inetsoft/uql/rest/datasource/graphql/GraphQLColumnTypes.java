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
package inetsoft.uql.rest.datasource.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.uql.tabular.TabularColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the schema type of a root field's columns, the same way {@code get_table_details} does
 * ({@link GraphQLCatalog#describeDataset}), for columns that are null on every row and so have no
 * data to infer a type from. Results, failures included, are cached for a short time per data
 * source and field, because introspection is a full schema request that every refresh would
 * otherwise repeat (and that fails every time on a server with introspection disabled).
 */
final class GraphQLColumnTypes {
   private GraphQLColumnTypes() {
   }

   /**
    * @return column name to type name; empty when the schema cannot be read. Never throws.
    */
   static Map<String, String> resolve(AbstractGraphQLDataSource<?> dataSource, String field) {
      final String key = dataSource.getName() + "\u0000" + dataSource.getURL() + "\u0000" + field;
      final long now = System.currentTimeMillis();
      final Entry cached = CACHE.get(key);

      if(cached != null && now - cached.time < ttlMillis) {
         return cached.types;
      }

      Map<String, String> types;

      try {
         final JsonNode schema = introspector.introspect(dataSource);
         types = new HashMap<>();

         for(TabularColumn column : GraphQLCatalog.describeDataset(schema, field).columns()) {
            types.put(column.name(), column.type());
         }
      }
      catch(Exception | LinkageError e) {
         LOG.debug("Could not resolve GraphQL column types for {}", field, e);
         types = Collections.emptyMap();
      }

      CACHE.put(key, new Entry(types, now));
      return types;
   }

   static void clearCache() {
      CACHE.clear();
   }

   // test hooks
   interface Introspector {
      JsonNode introspect(AbstractGraphQLDataSource<?> dataSource) throws Exception;
   }

   static Introspector introspector = GraphQLIntrospectionClient::introspect;
   static long ttlMillis = 60_000L;

   private record Entry(Map<String, String> types, long time) {
   }

   private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
