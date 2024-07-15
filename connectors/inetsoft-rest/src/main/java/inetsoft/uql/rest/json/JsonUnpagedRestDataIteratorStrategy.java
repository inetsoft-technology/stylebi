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

import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;

/**
 * Strategy for reading unpaged HTTP JSON responses.
 */
public class JsonUnpagedRestDataIteratorStrategy extends HttpRestDataIteratorStrategy<RestJsonQuery, JsonTransformer> {
   public JsonUnpagedRestDataIteratorStrategy(RestJsonQuery query,
                                              JsonTransformer transformer,
                                              IHttpHandler httpHandler,
                                              RestErrorHandler errorHandler)
   {
      super(query, transformer, httpHandler, errorHandler);
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      try {
         return handleRequest(RestRequest.fromQuery(query)).getValue();
      }
      finally {
         complete();
      }
   }
}
