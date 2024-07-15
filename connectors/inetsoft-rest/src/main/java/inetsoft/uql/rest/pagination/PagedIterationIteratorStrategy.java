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
package inetsoft.uql.rest.pagination;

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.HttpRestDataIteratorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
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
public class PagedIterationIteratorStrategy<Q extends AbstractRestQuery, T extends InputTransformer>
   extends HttpRestDataIteratorStrategy<Q, T>
{
   public PagedIterationIteratorStrategy(Q query,
                                         T transformer,
                                         IHttpHandler httpHandler,
                                         RestErrorHandler errorHandler,
                                         HttpResponseParameterParser parser)
   {
      super(query, transformer, httpHandler, errorHandler);
      this.parser = parser;

      final PaginationSpec spec = query.getPaginationSpec();
      validate(spec);
      addMaxResultsParameter(spec);
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

      return !hasNext;
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final RestRequest.Builder builder = RestRequest.builder().query(query);
      final PaginationSpec spec = query.getPaginationSpec();

      if(pageOffset != null) {
         final String name = spec.getPageOffsetParamToWrite().getValue();
         builder.queryParameters(Collections.singletonMap(name, pageOffset));
      }

      return fetchPage(builder.build());
   }

   private Object fetchPage(RestRequest request) throws Exception {
      return handleRequest(request).getHandledResult();
   }

   @Override
   protected Object processResponse(InputStream responseBody, HttpResponse response) throws Exception {
      final Object json = super.processResponse(responseBody, response);
      this.readAndUpdateParameters(json, response);
      return json;
   }

   private void readAndUpdateParameters(Object json, HttpResponse response) {
      final PaginationSpec spec = query.getPaginationSpec();
      hasNext = getHasNext(spec, json, response);

      if(hasNext) {
         final String newPageOffset = getPageOffset(spec, json, response);
         checkAgainstLastPageOffset(newPageOffset, pageOffset);
         pageOffset = newPageOffset;

         if(spec.isIncrementOffset()) {
            pageOffset = incrementOffset(pageOffset);
         }
      }
      else {
         pageOffset = null;
      }
   }

   private boolean getHasNext(PaginationSpec spec, Object json, HttpResponse response) {
      try {
         if(spec.getHasNextParamValue() != null) {
            final String result = parser.parseString(spec.getHasNextParam(), json, response);
            return !spec.getHasNextParamValue().equals(result);
         }

         return parser.parseBoolean(spec.getHasNextParam(), json, response);
      }
      catch(Exception e) {
         LOG.debug("Has next param {} was not found", spec.getHasNextParam());
         return false;
      }
   }

   private String getPageOffset(PaginationSpec spec, Object json, HttpResponse response) {
      try {
         return parser.parseString(spec.getPageOffsetParamToRead(), json, response);
      }
      catch(Exception e) {
         throw new ParameterParseException(
            "Failed to parse Page Offset To Read Parameter from response.", e);
      }
   }

   /**
    * Check that the offset is not repeating to not get stuck in an infinite loop.
    */
   private void checkAgainstLastPageOffset(String pageOffset, String lastPageOffset) {
      if(lastPageOffset != null && lastPageOffset.equals(pageOffset)) {
         throw new IllegalStateException("Offset parameter is not updating.");
      }
   }

   private String incrementOffset(String offset) {
      try {
         return (Long.parseLong(offset) + 1L) + "";
      }
      catch(Exception e) {
         throw new ParameterParseException(
            "Failed to increment the page offset. Make sure that it's a number.", e);
      }
   }

   private String pageOffset;
   private boolean hasNext = true;

   private final HttpResponseParameterParser parser;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
