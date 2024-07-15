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

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.pagination.*;

import java.io.InputStream;
import java.util.Collections;

/**
 * Strategy which reads the uses the total count from the response to iterate the results, using
 * a user-supplied max-results-per-page param to find the next page's offset.
 * The next page's offset is found by incrementing by max-results-per-page until the total count is
 * reached.
 */
public class JsonTotalCountAndOffsetIteratorStrategy extends HttpRestDataIteratorStrategy<RestJsonQuery, JsonTransformer> {
   public JsonTotalCountAndOffsetIteratorStrategy(RestJsonQuery query,
                                                  JsonTransformer transformer,
                                                  IHttpHandler httpHandler,
                                                  RestErrorHandler errorHandler,
                                                  HttpResponseParameterParser parser)
   {
      super(query, transformer, httpHandler, errorHandler);
      this.parser = parser;

      final PaginationSpec spec = query.getPaginationSpec();
      validate(spec);

      int maxResults = spec.getMaxResultsPerPage();

      if(!addMaxResultsParameter(spec)) {
         maxResults = getMaxResultsPerPage(spec);
      }

      this.maxResults = maxResults;
   }

   private void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.TOTAL_COUNT_AND_OFFSET) {
         throw new IllegalStateException("Query pagination type must be Total Count.");
      }

      if(isEmpty(spec.getTotalCountParam())) {
         throw new IllegalStateException(
            "Query Pagination must have non-empty Total Count parameter.");
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
   protected boolean done() {
      if(super.done()) {
         return true;
      }

      return totalCount != null && count >= totalCount;
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final Object json;
      final PaginationSpec spec = query.getPaginationSpec();

      if(totalCount == null) {
         json = fetchFirstPage();

         if(totalCount == null) {
            totalCount = -1;
         }
      }
      else {
         json = fetchSubsequentPage(spec);
      }

      count += maxResults;
      return json;
   }

   private Object fetchFirstPage() throws Exception {
      return handleRequest(RestRequest.fromQuery(query)).getHandledResult();
   }

   @Override
   protected Object processResponse(InputStream responseBody, HttpResponse response) throws Exception {
      final Object json = super.processResponse(responseBody, response);

      if(totalCount == null) {
         totalCount = getTotalCount(query.getPaginationSpec(), json, response);
      }

      return json;
   }

   private Object fetchSubsequentPage(PaginationSpec spec) throws Exception {
      final String offsetParamName = spec.getOffsetParam().getValue();

      if(spec.getOffsetParam().getType() == PaginationParamType.JSON_PATH &&
         query.getContentType().equals("application/json"))
      {
         String content = isEmpty(query.getRequestBody()) ? "{}" : query.getRequestBody();
         content = transformer.updateOutputString(content, offsetParamName, count);
         query.setRequestBody(content);

         final RestRequest request = RestRequest.builder()
            .query(query)
            .build();

         return handleRequest(request).getHandledResult();
      }
      else {
         final RestRequest request = RestRequest.builder()
            .query(query)
            .queryParameters(Collections.singletonMap(offsetParamName, Integer.toString(count)))
            .build();

         return handleRequest(request).getHandledResult();
      }
   }

   private int getTotalCount(PaginationSpec spec, Object json, HttpResponse response) {
      try {
         return parser.parseInt(spec.getTotalCountParam(), json, response);
      }
      catch(Exception e) {
         return 0;
      }
   }

   private Integer totalCount;
   private int count;

   private final int maxResults;
   private final HttpResponseParameterParser parser;
}
