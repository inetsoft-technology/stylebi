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

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.HttpRestDataIteratorStrategy;
import inetsoft.uql.rest.xml.parse.*;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
      if(query.resolveResponseInHeaders()) {
         return createHeaderRecord(response);
      }

      if(response.getContentLength() != 0) {
         return transformer.transform(responseBody);
      }
      else {
         return null;
      }
   }

   @Override
   protected Object processBodylessResponse(HttpResponse response) {
      return query.resolveResponseInHeaders() ? createHeaderRecord(response) : null;
   }

   /**
    * Builds the single record of an endpoint whose data is in the response headers (see
    * {@code AbstractEndpoint#isResponseInHeaders}). The record has the shape the XSLT transformer
    * gives {@code <Response><name>value</name>...</Response>}, but is built directly rather than
    * from XML text: a header name need not be a valid XML name and a value may hold markup.
    *
    * <p>Policy: names are lower-cased and any character outside {@code [a-z0-9_-]} becomes
    * {@code _}, so a column name never carries a path separator; of repeated headers the first
    * wins (as {@link HttpResponse#getResponseHeaders()} already does) and, after lower-casing,
    * the first of two names that collide; every value is a String. Only the headers accepted by
    * {@link #isDataHeader} are kept, so the Azure {@code x-ms-meta-*} headers, whose set is chosen
    * by whoever wrote the metadata, are always included.</p>
    *
    * @return the record, or null when the response carries no data header (so a header-only
    *         endpoint that returns nothing useful still fails loudly instead of yielding an empty
    *         row).
    */
   private static ParsedNode createHeaderRecord(HttpResponse response) {
      final Map<String, String> headers = response.getResponseHeaders();

      if(headers == null) {
         return null;
      }

      final MapNode record = new MapNode();

      for(Map.Entry<String, String> header : headers.entrySet()) {
         if(header.getKey() == null) {
            continue;
         }

         final String name = header.getKey().toLowerCase(Locale.ROOT);

         if(isDataHeader(name)) {
            record.putIfAbsent(name.replaceAll("[^a-z0-9_-]", "_"), new ValueNode(header.getValue()));
         }
      }

      if(record.isEmpty()) {
         return null;
      }

      final MapNode root = new MapNode();
      root.put("Response", record);
      return root;
   }

   /**
    * A header that describes the resource rather than the transport: every {@code x-ms-*} header
    * (metadata, lease, SKU, account kind, public access, ...) plus {@code ETag} and
    * {@code Last-Modified}, minus the per-request ones ({@code x-ms-request-id},
    * {@code x-ms-client-request-id}, {@code x-ms-version}, {@code x-ms-date}) which differ on every
    * call. {@code Date}, {@code Server}, {@code Content-Length} and the like are transport noise.
    *
    * @param name the lower-case header name.
    */
   private static boolean isDataHeader(String name) {
      if(name.startsWith("x-ms-")) {
         return !TRANSPORT_HEADERS.contains(name);
      }

      return name.equals("etag") || name.equals("last-modified");
   }

   private static final Set<String> TRANSPORT_HEADERS = Set.of(
      "x-ms-request-id", "x-ms-client-request-id", "x-ms-version", "x-ms-date");
}
