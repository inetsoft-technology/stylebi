/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
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
 * Strategy which finds the total number of pages from the initial response and then iterates
 * through them page by page.
 */
public class JsonPagedPageCountIteratorStrategy extends HttpRestDataIteratorStrategy<RestJsonQuery, JsonTransformer> {
   public JsonPagedPageCountIteratorStrategy(RestJsonQuery query,
                                             JsonTransformer transformer,
                                             IHttpHandler httpHandler,
                                             RestErrorHandler errorHandler,
                                             HttpResponseParameterParser parser)
   {
      super(query, transformer, httpHandler, errorHandler);
      this.parser = parser;

      final PaginationSpec spec = query.getPaginationSpec();
      validate(spec);
      addMaxResultsParameter(spec);
      pageNumber = spec.getFirstPageIndex();
   }

   private void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.PAGE_COUNT) {
         throw new IllegalStateException("Query pagination type must be Page Count.");
      }

      if(isEmpty(spec.getTotalPagesParam())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Total Pages parameter.");
      }

      if(isEmpty(spec.getPageNumberParamToWrite())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Page Number To Write parameter.");
      }
   }

   @Override
   protected boolean done() {
      if(super.done()) {
         return true;
      }

      final PaginationSpec spec = query.getPaginationSpec();
      final int firstPageIndex = spec.getFirstPageIndex();

      return totalPages != null && (totalPages < 0 || pageNumber >= (totalPages + firstPageIndex));
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final Object json;

      if(totalPages == null) {
         json = fetchFirstPage();

         if(totalPages == null) {
            totalPages = -1;
         }
      }
      else {
         json = fetchSubsequentPage(pageNumber);
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

      if(totalPages == null) {
         totalPages = getTotalPages(query.getPaginationSpec(), json, response);
      }

      return json;
   }

   private int getTotalPages(PaginationSpec spec, Object json, HttpResponse response) {
      try {
         return parser.parseInt(spec.getTotalPagesParam(), json, response);
      }
      catch(Exception ex) {
         return 0;
      }
   }

   private Object fetchSubsequentPage(int pageNumber) throws Exception {
      final PaginationSpec spec = query.getPaginationSpec();
      final String pageNumberParamName = spec.getPageNumberParamToWrite().getValue();

      final RestRequest request = RestRequest.builder()
         .query(query)
         .queryParameters(Collections.singletonMap(pageNumberParamName, Integer.toString(pageNumber)))
         .build();

      return handleRequest(request).getHandledResult();
   }

   private Integer totalPages = null;
   private int pageNumber;

   private final HttpResponseParameterParser parser;
}
