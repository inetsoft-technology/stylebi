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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestErrorResponse;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.util.Collections;

/**
 * Strategy that uses a record offset and max results parameters to iterate results until none are
 * returned.
 */
public class JsonPagedOffsetIteratorStrategy extends HttpRestDataIteratorStrategy<RestJsonQuery, JsonTransformer> {
   public JsonPagedOffsetIteratorStrategy(RestJsonQuery query,
                                          JsonTransformer transformer,
                                          IHttpHandler httpHandler,
                                          RestErrorHandler errorHandler)
   {
      super(query, transformer, httpHandler, errorHandler);

      final PaginationSpec spec = query.getPaginationSpec();
      validate(spec);
      addMaxResultsParameter(spec);
      offset = spec.getFirstPageIndex();
   }

   public void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.OFFSET) {
         throw new IllegalStateException("Query pagination type must be Offset");
      }

      if(spec.getRecordCountPath() == null || spec.getRecordCountPath().isEmpty()) {
         throw new IllegalStateException("Query pagination must have non-empty record count path");
      }

      if(isEmpty(spec.getOffsetParam())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Offset parameter.");
      }

      if(spec.getMaxResultsPerPage() <= 0) {
         throw new IllegalStateException(
            "Query pagination must have positive Max Results Per Page parameter.");
      }
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final PaginationSpec spec = query.getPaginationSpec();
      final String parameterName = spec.getOffsetParam().getValue();
      final int baseRecordLength = spec.getBaseRecordLength();
      final RestRequest request = RestRequest.builder()
         .query(query)
         .queryParameters(Collections.singletonMap(parameterName, Integer.toString(offset)))
         .build();

      final RestErrorResponse<Object> handlerResponse = handleRequest(request);
      final Object json = handlerResponse.getHandledResult();

      if(json != null) {
         final int count = getRecordCount(json, spec);

         if(count > baseRecordLength) {
            offset += count;
            return json;
         }
         else {
            return null;
         }
      }

      return null;
   }

   private int getRecordCount(Object json, PaginationSpec spec) {
      try {
         Object result = transformer.transform(json, spec.getRecordCountPath());

         if(result != null) {
            if(result.getClass().isArray()) {
               int size = Array.getLength(result);

               if(size > 0) {
                  Object value = Array.get(result, 0);

                  if(value instanceof Number) {
                     return ((Number) value).intValue();
                  }
               }
            }
            else if(result instanceof Number) {
               return ((Number) result).intValue();
            }
         }

         return 0;
      }
      catch(Exception e) {
         LOG.debug("Failed to determine if results are empty", e);
         return 0;
      }
   }

   private int offset;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
