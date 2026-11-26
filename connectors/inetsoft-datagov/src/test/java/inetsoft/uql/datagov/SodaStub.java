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
package inetsoft.uql.datagov;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.json.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local Socrata site for the SODA read path: the dataset metadata at
 * {@code /api/views/<id>.json}, rows at {@code /resource/<id>.json} honouring {@code $limit}
 * (default 1000, as Socrata silently applies), {@code $offset} and a substring {@code $q}, and a
 * retired {@code /api/views/<id>/rows.json} that answers a configurable status. Every request URI
 * is recorded.
 */
final class SodaStub implements AutoCloseable {
   SodaStub(String id, String viewJson, JsonArray rows) throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> handle(exchange, id, viewJson, rows));
      server.start();
   }

   String baseUrl() {
      return "http://localhost:" + server.getAddress().getPort() + "/";
   }

   /**
    * @return every request URI (path and query) received so far, in order.
    */
   List<String> requests() {
      return requests;
   }

   /**
    * Makes every request whose path starts with {@code pathPrefix} answer {@code status}.
    */
   void failWith(String pathPrefix, int status, String body) {
      failPrefix = pathPrefix;
      failStatus = status;
      failBody = body;
   }

   static JsonArray array(String json) {
      try(JsonReader reader = Json.createReader(new StringReader(json))) {
         return reader.readArray();
      }
   }

   private void handle(HttpExchange exchange, String id, String viewJson, JsonArray rows)
      throws IOException
   {
      String path = exchange.getRequestURI().getRawPath();
      String query = exchange.getRequestURI().getRawQuery();
      requests.add(path + (query == null ? "" : "?" + query));
      int status = 200;
      String body;

      if(failPrefix != null && path.startsWith(failPrefix)) {
         status = failStatus;
         body = failBody;
      }
      else if(path.equals("/api/views/" + id + ".json")) {
         body = viewJson;
      }
      else if(path.equals("/resource/" + id + ".json")) {
         body = page(query, rows).toString();
      }
      else {
         status = 404;
         body = "{\"code\":\"not_found\"}";
      }

      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);

      try(OutputStream out = exchange.getResponseBody()) {
         out.write(bytes);
      }
   }

   private static JsonArray page(String query, JsonArray rows) {
      int limit = 1000;
      int offset = 0;
      String q = null;

      if(query != null) {
         for(String param : query.split("&")) {
            int eq = param.indexOf('=');
            String name = URLDecoder.decode(param.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8);

            switch(name) {
            case "$limit":
               limit = Integer.parseInt(value);
               break;
            case "$offset":
               offset = Integer.parseInt(value);
               break;
            case "$q":
               q = value.toLowerCase();
               break;
            default:
               break;
            }
         }
      }

      List<JsonValue> matching = new ArrayList<>();

      for(JsonValue row : rows) {
         if(q == null || row.toString().toLowerCase().contains(q)) {
            matching.add(row);
         }
      }

      JsonArrayBuilder out = Json.createArrayBuilder();

      for(int i = offset; i < Math.min(matching.size(), offset + limit); i++) {
         out.add(matching.get(i));
      }

      return out.build();
   }

   @Override
   public void close() {
      server.stop(0);
   }

   private final HttpServer server;
   private final List<String> requests = new CopyOnWriteArrayList<>();
   private volatile String failPrefix;
   private volatile int failStatus;
   private volatile String failBody;
}
