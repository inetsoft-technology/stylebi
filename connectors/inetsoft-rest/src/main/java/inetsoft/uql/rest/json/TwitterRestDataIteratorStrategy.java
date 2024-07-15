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
import inetsoft.uql.rest.datasource.twitter.TwitterDataSource;
import inetsoft.uql.rest.datasource.twitter.TwitterQuery;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.rest.pagination.ParameterParseException;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitterRestDataIteratorStrategy extends HttpRestDataIteratorStrategy<TwitterQuery, JsonTransformer> {
   public TwitterRestDataIteratorStrategy(TwitterQuery query,
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
   }

   private void validate(PaginationSpec spec) {
      if(spec.getType() != PaginationType.TWITTER_ITERATION) {
         throw new IllegalStateException("Query pagination type must be Iteration.");
      }

      if(isEmpty(spec.getPageOffsetParamToRead())) {
         throw new IllegalStateException(
            "Query pagination must have non-empty Page Offset To Read parameter.");
      }
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final RestRequest.Builder builder = RestRequest.builder().query(query);

      if(pageOffset != null) {
         builder.queryParameters(createQueryParams(pageOffset));
      }

      final Object json = fetchPage(builder.build());

      if(pageOffset == null) {
         complete();
      }

      return json;
   }

   private Object fetchPage(RestRequest request) throws Exception {
      return handleRequest(request).getHandledResult();
   }

   @Override
   protected Object processResponse(InputStream responseBody, HttpResponse response) throws Exception {
      final Object json = super.processResponse(responseBody, response);
      updatePageOffset(json, response);
      return json;
   }

   private void updatePageOffset(Object json, HttpResponse response) {
      final PaginationSpec spec = query.getPaginationSpec();
      final String newPageOffset = getPageOffset(spec, json, response);

      checkAgainstLastPageOffset(newPageOffset, pageOffset);
      pageOffset = newPageOffset;
   }

   private Map<String, String> createQueryParams(String query) {
      final String[] parts = query.split("&");
      final HashMap<String, String> queryParams = new HashMap<>();
      final Pattern compile = Pattern.compile("\\??(\\w+)=(\\w+)");

      for(String part : parts) {
         final Matcher matcher = compile.matcher(part);

         if(matcher.matches()) {
            final String key = matcher.group(1);
            final String value = matcher.group(2);
            queryParams.put(key, value);
         }
      }

      queryParams.computeIfPresent("max_id", (key, value) -> {
         try {
            long maxId = Long.parseLong(value);
            return String.valueOf(maxId - 1);
         }
         catch(NumberFormatException e) {
            // ignore if not preset or parsable
            return value;
         }
      });

      final String suffix = this.query.getSuffix();
      final Map<String, String> queryParameters = this.query.getQueryParameters();

      // update after calling getSuffix
      queryParameters.putAll(queryParams);
      ((TwitterDataSource) this.query.getDataSource()).update(suffix, queryParameters);
      return queryParameters;
   }

   /**
    * Check that the offset is not repeating to not get stuck in an infinite loop.
    */
   private void checkAgainstLastPageOffset(String pageOffset, String lastPageOffset) {
      if(lastPageOffset != null && lastPageOffset.equals(pageOffset)) {
         throw new IllegalStateException("Offset parameter is not updating.");
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

   private String pageOffset;

   private final HttpResponseParameterParser parser;
}
