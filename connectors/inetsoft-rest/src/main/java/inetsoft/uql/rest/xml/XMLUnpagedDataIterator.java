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
import inetsoft.uql.rest.xml.parse.ParsedNode;

import java.io.InputStream;

/**
 * Strategy for reading unpaged HTTP XML responses.
 */
public class XMLUnpagedDataIterator extends HttpRestDataIteratorStrategy<RestXMLQuery, XMLBasicStreamTransformer> {
   XMLUnpagedDataIterator(RestXMLQuery query,
                          XMLBasicStreamTransformer transformer,
                          IHttpHandler httpHandler,
                          RestErrorHandler errorHandler)
   {
      super(query, transformer, httpHandler, errorHandler);
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      try {
         final RestErrorResponse<Object> handlerResponse = handleRequest(RestRequest.fromQuery(query));

         if(handlerResponse.shouldContinue()) {
            return handlerResponse.getValue();
         }
      }
      finally {
         complete();
      }

      return null;
   }

   @Override
   protected ParsedNode processResponse(InputStream responseBody, HttpResponse response)
      throws Exception
   {
      if(response.getContentLength() != 0) {
         return transformer.transform(responseBody);
      }
      else {
         return null;
      }
   }
}
