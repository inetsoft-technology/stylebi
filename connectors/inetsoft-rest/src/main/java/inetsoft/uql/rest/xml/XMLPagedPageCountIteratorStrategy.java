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
package inetsoft.uql.rest.xml;

import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.json.HttpRestDataIteratorStrategy;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;

import java.util.Collections;

/**
 * Strategy which uses the {@link XMLPagedStreamTransformer} to parse the page count from an XML
 * document and iterate over the pages. The pages are constructed using a URL variable, starting
 * from the first request.
 *
 * Example: http://example.com/{{page}} --> http://example.com/1
 */
class XMLPagedPageCountIteratorStrategy extends HttpRestDataIteratorStrategy<RestXMLQuery, XMLPagedStreamTransformer> {
   protected XMLPagedPageCountIteratorStrategy(RestXMLQuery query,
                                               XMLPagedStreamTransformer transformer,
                                               IHttpHandler httpHandler,
                                               RestErrorHandler errorHandler)
   {
      super(query, transformer, httpHandler, errorHandler);

      final PaginationSpec spec = query.getPaginationSpec();
      validate(spec);
      currentPage = spec.getFirstPageIndex();
   }

   public void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.PAGE_COUNT) {
         throw new IllegalStateException("Query pagination type must be Page Count.");
      }

      if(isEmpty(spec.getPageNumberUrlVariable())) {
         throw new IllegalStateException(
            "Query Pagination must have non-empty Page Number Url Variable parameter.");
      }

      if(isEmpty(spec.getPageCountXpath())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Page Count XPath parameter.");
      }
   }

   @Override
   protected boolean done() {
      if(super.done()) {
         return true;
      }

      return lastPage != null && currentPage > lastPage;
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      try {
         return fetchPage();
      }
      finally {
         lastPage = getLastPage();
      }
   }

   private Object fetchPage() throws Exception {
      final PaginationSpec spec = query.getPaginationSpec();
      final RestRequest request = RestRequest.builder()
         .query(query)
         .urlVariables(Collections.singletonMap(
            spec.getPageNumberUrlVariable().getValue(), String.valueOf(currentPage++)))
         .build();

      return handleRequest(request).getHandledResult();
   }

   private int getLastPage() {
      final boolean zeroBased = query.getPaginationSpec().isZeroBasedPageIndex();
      final int pageCount = transformer.getPageCount();
      return zeroBased ? pageCount - 1 : pageCount;
   }

   private Integer currentPage;
   private Integer lastPage;
}
