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

import com.jayway.jsonpath.PathNotFoundException;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.List;

/**
 * Strategy which extracts and executes the next page's url link from the current response.
 * The iterator is complete once the next page's url is not present in the most recent response.
 */
public class JsonPagedLinkIterationStrategy extends HttpRestDataIteratorStrategy<RestJsonQuery, JsonTransformer> {
   public JsonPagedLinkIterationStrategy(RestJsonQuery query,
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
      if(spec.getType() != PaginationType.LINK_ITERATION) {
         throw new IllegalStateException("Query pagination type must be Link Iteration.");
      }

      final PaginationParameter link = spec.getLinkParam();

      if(isEmpty(link)) {
         throw new IllegalStateException("Query pagination must have non-empty Link parameter.");
      }

      if(link.getType() == PaginationParamType.LINK_HEADER && isEmpty(link.getLinkRelation())) {
         throw new IllegalStateException(
            "Link Header parameter must have non-empty Link Relation.");
      }
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final RestRequest.Builder builder = RestRequest.builder().query(query);

      if(nextUrl != null) {
         builder.url(nextUrl);
      }

      final Object json = handleRequest(builder.build()).getHandledResult();

      if(nextUrl == null) {
         complete();
      }

      return json;
   }

   @Override
   protected Object processResponse(InputStream responseBody, HttpResponse response) throws Exception {
      final Object json = super.processResponse(responseBody, response);
      final PaginationSpec spec = query.getPaginationSpec();
      nextUrl = getNextURL(spec, json, response);

      return json;
   }

   private URL getNextURL(PaginationSpec spec, Object json, HttpResponse response)
      throws IOException
   {
      if(json instanceof List && ((List) json).size() == 0) {
         return null;
      }

      try {
         return parser.parseURL(spec.getLinkParam(), json, response);
      }
      catch(PathNotFoundException e) {
         LOG.debug("Next page URL path does not exist in path: {}", spec.getLinkParam());
         return null;
      }
   }

   private URL nextUrl;

   private final HttpResponseParameterParser parser;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
