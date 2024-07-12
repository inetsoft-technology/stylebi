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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;

import java.io.InputStream;
import java.util.Collections;

/**
 * Strategy which reads the uses the total count from the response to iterate the results, using
 * a user-supplied max-results-per-page param to find the total number of pages.
 * It then iterates through each page.
 */
public class JsonTotalCountAndPageIteratorStrategy extends HttpRestDataIteratorStrategy<RestJsonQuery, JsonTransformer> {
   public JsonTotalCountAndPageIteratorStrategy(RestJsonQuery query,
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
      this.pageNumber = spec.getFirstPageIndex();
   }

   private void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.TOTAL_COUNT_AND_PAGE) {
         throw new IllegalStateException("Query pagination type must be Total Count.");
      }

      if(isEmpty(spec.getTotalCountParam())) {
         throw new IllegalStateException(
            "Query Pagination must have non-empty Total Count parameter.");
      }

      if(isEmpty(spec.getPageNumberParamToWrite())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Page Number Param To Write parameter.");
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

      return lastPageNumber != null && pageNumber > lastPageNumber;
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final Object json;

      if(lastPageNumber == null) {
         json = fetchFirstPage();

         if(lastPageNumber == null) {
            lastPageNumber = -1;
         }
      }
      else {
         json = fetchSubsequentPage();
      }

      pageNumber++;
      return json;
   }

   private Object fetchFirstPage() throws Exception {
      return handleRequest(RestRequest.fromQuery(query)).getHandledResult();
   }

   @Override
   protected Object processResponse(InputStream responseBody, HttpResponse response) throws Exception {
      final Object json = super.processResponse(responseBody, response);

      if(lastPageNumber == null) {
         lastPageNumber = getLastPageNumber(query.getPaginationSpec(), response, json);
      }

      return json;
   }

   private int getLastPageNumber(PaginationSpec spec, HttpResponse response, Object json) {
      final int totalCount = getTotalCount(spec, json, response);
      final int pageCount = (int) Math.ceil(totalCount / (double) maxResults);
      return spec.isZeroBasedPageIndex() ? pageCount - 1 : pageCount;
   }

   private Object fetchSubsequentPage() throws Exception {
      final PaginationSpec spec = query.getPaginationSpec();
      final String pgNumberParam = spec.getPageNumberParamToWrite().getValue();
      final RestRequest request = RestRequest.builder()
         .query(query)
         .queryParameters(Collections.singletonMap(pgNumberParam, Integer.toString(pageNumber)))
         .build();

      return handleRequest(request).getHandledResult();
   }

   private int getTotalCount(PaginationSpec spec, Object json, HttpResponse response) {
      try {
         return parser.parseInt(spec.getTotalCountParam(), json, response);
      }
      catch(Exception e) {
         return 0;
      }
   }

   private Integer lastPageNumber;
   private int pageNumber;

   private final int maxResults;
   private final HttpResponseParameterParser parser;
}
