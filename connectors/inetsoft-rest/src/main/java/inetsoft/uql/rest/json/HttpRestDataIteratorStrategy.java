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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.rest.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parent class for http rest data iterators.
 * Provides scaffolding for iteratively making http requests.
 */
public abstract class HttpRestDataIteratorStrategy<Q extends AbstractRestQuery, T extends InputTransformer>
   extends AbstractRestDataIteratorStrategy<Q, T>
{
   protected HttpRestDataIteratorStrategy(Q query,
                                          T transformer,
                                          IHttpHandler httpHandler,
                                          RestErrorHandler errorHandler)
   {
      super(query, transformer);
      this.httpHandler = httpHandler;
      this.errorHandler = errorHandler;
   }

   @Override
   public void close() throws IOException {
      httpHandler.close();
   }

   @Override
   public boolean hasNext() throws Exception {
      return getNextResult() != null;
   }

   @Override
   public Object next() throws Exception {
      final Object result = getNextResult();
      this.next = null;
      return result;
   }

   private Object getNextResult() throws Exception {
      if(next != null || done()) {
         return next;
      }

      next = fetchNextResult();

      if(next == null) {
         complete();
      }

      return next;
   }

   /**
    * @return true if the iterator will make no further requests, otherwise false.
    */
   protected boolean done() {
      return completed;
   }

   /**
    * @return the next chunk of data, or null if no more data exists.
    */
   protected abstract Object fetchNextResult() throws Exception;

   /**
    * Mark this iterator as completed so that it makes no further requests.
    */
   protected void complete() {
      this.completed = true;
   }

   protected RestErrorResponse<Object> handleRequest(RestRequest request) throws Exception {
      final HttpResponse cachedResponse = isCacheActive() ? cache.get(request.key(), isLiveMode()) : null;
      final RestErrorResponse<Object> response;

      if(cachedResponse != null) {
         response = errorHandler.catchError(() -> processResponse(cachedResponse.getResponseBodyAsStream(),
                                                                  cachedResponse));
      }
      else {
         response = errorHandler.catchError(() -> RestQuotaManager.withQuota(
            query, () -> executeRequest(request)));
      }

      return response;
   }

   private Object executeRequest(RestRequest request) throws Exception {
      try(HttpResponse response = httpHandler.executeRequest(request);
          InputStream input = response.getResponseBodyAsStream())
      {
         HttpResponse res = response;
         InputStream in = input;

         if(shouldCacheRequest(response, input)) {
            res = cache.putResponse(request.key(), response);
            in = res.getResponseBodyAsStream();
         }

         return in != null ? processResponse(in, res) : null;
      }
   }

   private boolean shouldCacheRequest(HttpResponse response, InputStream input) {
      return isCacheActive() && input != null && response.getResponseStatusCode() == 200;
   }

   private boolean isCacheActive() {
      final Long cacheTimeout = SreeEnv.getLong("rest.cache.timeout.millis");
      return cacheTimeout != null && cacheTimeout > 0;
   }

   /**
    * Meant to be used as a hook for implementing classes to process the response body and
    * open http request.
    */
   protected Object processResponse(InputStream responseBody, HttpResponse response)
      throws Exception
   {
      return transformer.transform(responseBody);
   }

   private Object next;
   private boolean completed;

   protected final IHttpHandler httpHandler;

   private static final HttpResponseCache cache = new HttpResponseCache();
}
