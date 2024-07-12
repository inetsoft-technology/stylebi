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
package inetsoft.uql.rest.xml;

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.HttpRestDataIteratorStrategy;
import inetsoft.uql.rest.pagination.*;

import java.util.Collections;

/**
 * Strategy for iterating over multiple pages of an endpoint.
 * The basic flow of the strategy is the following:
 *
 * <ul>
 *    <li> Check the response for whether the next page exists.
 *    <li> If it does, then the strategy expects there to be a page offset parameter to read from
 *    the response that specifies the next page.
 *    <li> The strategy then uses that offset to get the next page.
 *    <li> Repeat from beginning.
 * </ul>
 */
public class XMLPagedIterationIteratorStrategy extends HttpRestDataIteratorStrategy<RestXMLQuery, XMLIterationStreamTransformer> {
   protected XMLPagedIterationIteratorStrategy(RestXMLQuery query,
                                               XMLIterationStreamTransformer transformer,
                                               IHttpHandler httpHandler,
                                               RestErrorHandler errorHandler)
   {
      super(query, transformer, httpHandler, errorHandler);
      validate(query.getPaginationSpec());
   }

   private void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.ITERATION) {
         throw new IllegalStateException("Query pagination type must be Iteration.");
      }

      if(isEmpty(spec.getHasNextParam())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Has-Next parameter.");
      }

      if(isEmpty(spec.getPageOffsetParamToRead())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Page Offset To Read parameter.");
      }

      if(isEmpty(spec.getPageOffsetParamToWrite())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Page Offset To Write parameter.");
      }
   }

   @Override
   protected boolean done() {
      if(super.done()) {
         return true;
      }

      return !transformer.hasNext();
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final RestRequest.Builder builder = RestRequest.builder().query(query);
      final PaginationSpec spec = query.getPaginationSpec();
      final String pageOffset = transformer.getPageOffsetParam();

      if(pageOffset != null) {
         final String name = spec.getPageOffsetParamToWrite().getValue();
         builder.queryParameters(Collections.singletonMap(name, pageOffset));
      }

      transformer.setPageOffsetParam(null);
      transformer.setHasNext(false);
      return fetchPage(builder.build());
   }

   private Object fetchPage(RestRequest request) throws Exception {
      return handleRequest(request).getHandledResult();
   }
}
