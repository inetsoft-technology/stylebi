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
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.util.Collections;

/**
 * Strategy keeps requesting pages until no results are returned.
 */
public class JsonPagedPageNumberIteratorStrategy extends HttpRestDataIteratorStrategy<RestJsonQuery, JsonTransformer> {
   public JsonPagedPageNumberIteratorStrategy(RestJsonQuery query,
                                              JsonTransformer transformer,
                                              IHttpHandler httpHandler,
                                              RestErrorHandler errorHandler)
   {
      super(query, transformer, httpHandler, errorHandler);

      final PaginationSpec spec = query.getPaginationSpec();
      validate(spec);
      addMaxResultsParameter(spec);
      pageNumber = spec.getFirstPageIndex();
   }

   private void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.PAGE) {
         throw new IllegalStateException("Query pagination type must be Page.");
      }

      if(isEmpty(spec.getPageNumberParamToWrite())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Page Number To Write parameter.");
      }

      if(spec.getRecordCountPath() == null || spec.getRecordCountPath().isEmpty()) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Empty Results Test.");
      }
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final PaginationSpec spec = query.getPaginationSpec();
      final String parameterName = spec.getPageNumberParamToWrite().getValue();
      final RestRequest request;

      if(spec.getPageNumberParamToWrite().getType() == PaginationParamType.JSON_PATH &&
         query.getContentType().equals("application/json"))
      {
         RestJsonQuery newQuery =  (RestJsonQuery) query.clone();
         String content = query.getRequestBody();
         content = transformer.updateOutputString(content, parameterName, pageNumber++);
         newQuery.setRequestBody(content);

         request = RestRequest.builder()
            .query(newQuery)
            .build();
      }
      else {
         request = RestRequest.builder()
            .query(query)
            .queryParameters(Collections.singletonMap(parameterName, Integer.toString(pageNumber++)))
            .build();
      }

      final Object json = handleRequest(request).getHandledResult();

      if(json != null && isEmpty(json, spec)) {
         return null;
      }

      return json;
   }

   private boolean isEmpty(Object json, PaginationSpec spec) {
      try {
         Object result = transformer.transform(json, spec.getRecordCountPath());

         if(result != null) {
            if(result.getClass().isArray()) {
               int size = Array.getLength(result);

               if(size > 0) {
                  Object value = Array.get(result, 0);

                  if(value instanceof Number) {
                     return ((Number) value).intValue() == 0;
                  }
               }
            }
            else if(result instanceof Number) {
               return ((Number) result).intValue() == 0;
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to determine if results are empty", e);
         return true;
      }

      return true;
   }

   private int pageNumber;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
