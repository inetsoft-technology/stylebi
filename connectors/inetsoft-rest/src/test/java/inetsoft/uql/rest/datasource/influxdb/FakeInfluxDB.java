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
package inetsoft.uql.rest.datasource.influxdb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntFunction;

/**
 * A small HTTP server that answers the way InfluxDB 2.7 does where it matters to the connector,
 * copied from what the real server returns:
 *
 * <ul>
 *    <li>{@code /api/v2/buckets}, {@code /orgs}, {@code /users}: {@code limit} (default 20, at most
 *    100, else 400), {@code offset} and, for buckets and users, {@code after}; a {@code name}
 *    lookup answers with that one record whatever the offset is. Buckets say
 *    {@code links.next} once a page is full; users and orgs never do.</li>
 *    <li>{@code /api/v2/tasks}: {@code limit} (default 100, at most 500) and {@code after}, no
 *    {@code offset}. {@code links.next} is present only when the page is full.</li>
 *    <li>{@code /query}: the InfluxQL answer chosen by the {@code q} parameter.</li>
 * </ul>
 *
 * Every request line is recorded so a test can say which pages were asked for.
 */
final class FakeInfluxDB implements AutoCloseable {
   FakeInfluxDB(int buckets, int users, int tasks) throws IOException {
      this.buckets = buckets;
      this.users = users;
      this.tasks = tasks;
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::handle);
      server.start();
   }

   String url() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
   }

   /** The path and query of every request, in order. */
   List<String> requests() {
      synchronized(requests) {
         return new ArrayList<>(requests);
      }
   }

   @Override
   public void close() {
      server.stop(0);
   }

   private void handle(HttpExchange exchange) throws IOException {
      final String path = exchange.getRequestURI().getPath();
      final Map<String, String> params = params(exchange.getRequestURI().getRawQuery());

      synchronized(requests) {
         requests.add(exchange.getRequestURI().toString());
      }

      int status = 200;
      String body;

      switch(path) {
      case "/api/v2/buckets":
         body = list("buckets", buckets, 20, 100, params, true, true, "bucket", id -> "{\"id\":\"b" + id + "\",\"name\":\"bucket" + id + "\"}", path);
         break;
      case "/api/v2/users":
         body = list("users", users, 20, 100, params, false, true, "user", id -> "{\"id\":\"u" + id + "\",\"name\":\"user" + id + "\"}", path);
         break;
      case "/api/v2/tasks":
         body = list("tasks", tasks, 100, 500, params, true, false, "task", id -> "{\"id\":\"t" + id + "\",\"name\":\"task" + id + "\"}", path);
         break;
      case "/query":
         body = query(params.get("q"));
         break;
      default:
         status = 404;
         body = "{\"code\":\"not found\"}";
         break;
      }

      if(body == null) {
         status = 400;
         body = "{\"code\":\"invalid\",\"message\":\"limit must be between 1 and 100\"}";
      }

      final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
   }

   /**
    * One page of {@code total} items numbered from 0, or null when the limit is out of range.
    *
    * @param nextWhenFull whether {@code links.next} is sent once the page is full.
    * @param offsetKnown  false for an endpoint that has only a cursor, where an offset is ignored.
    */
   private String list(String name, int total, int defaultLimit, int maxLimit,
                       Map<String, String> params, boolean nextWhenFull, boolean offsetKnown,
                       String prefix, IntFunction<String> item, String path)
   {
      if(params.containsKey("name")) {
         // a lookup of one record by name answers with that record and ignores offset and after
         final int id = Integer.parseInt(params.get("name").substring(prefix.length()));
         return "{\"links\":{\"self\":\"" + path + "\"},\"" + name + "\":[" + item.apply(id) +
            "]}";
      }

      int limit = defaultLimit;

      if(params.containsKey("limit")) {
         limit = Integer.parseInt(params.get("limit"));

         if(limit < 1 || limit > maxLimit) {
            return null;
         }
      }

      int start = 0;

      if(offsetKnown && params.containsKey("offset")) {
         start = Integer.parseInt(params.get("offset"));
      }

      if(params.containsKey("after")) {
         // the cursor is the id of the last item of the previous page
         start = Integer.parseInt(params.get("after").substring(1)) + 1;
      }

      final int end = Math.min(total, start + limit);
      final StringBuilder items = new StringBuilder();

      for(int i = start; i < end; i++) {
         if(items.length() > 0) {
            items.append(',');
         }

         items.append(item.apply(i));
      }

      String links = "\"self\":\"" + path + "\"";

      if(nextWhenFull && end - start == limit) {
         links += ",\"next\":\"" + path + "?after=" + prefix.charAt(0) + (end - 1) + "&limit=" +
            limit + "\"";
      }

      return "{\"links\":{" + links + "},\"" + name + "\":[" + items + "]}";
   }

   private String query(String q) {
      final String series = "{\"name\":\"cpu\",\"columns\":[\"time\",\"usage_user\"],\"values\":[" +
         rows(0, 24) + "]}";

      switch(q == null ? "" : q) {
      case "ok":
         return "{\"results\":[{\"statement_id\":0,\"series\":[" + series + "]}]}";
      case "empty":
         return "{\"results\":[{\"statement_id\":0}]}";
      case "error":
         return "{\"results\":[{\"statement_id\":0,\"error\":\"database not found: nodb\"}]}";
      case "group":
         return "{\"results\":[{\"statement_id\":0,\"series\":[" +
            "{\"name\":\"cpu\",\"tags\":{\"host\":\"web-1\"},\"columns\":[\"time\",\"usage_user\"]," +
            "\"values\":[" + rows(0, 2) + "]}," +
            "{\"name\":\"cpu\",\"tags\":{\"host\":\"web-2\"},\"columns\":[\"time\",\"usage_user\"]," +
            "\"values\":[" + rows(2, 4) + "]}]}]}";
      case "measurements":
         return "{\"results\":[{\"statement_id\":0,\"series\":[" +
            "{\"name\":\"cpu\",\"columns\":[\"time\",\"usage\"],\"values\":[[\"2023-11-14T22:00:00Z\",1.5]]}," +
            "{\"name\":\"mem\",\"columns\":[\"time\",\"used\"],\"values\":[[\"2023-11-14T22:00:00Z\",7.5]]}]}]}";
      case "tagclash":
         return "{\"results\":[{\"statement_id\":0,\"series\":[" +
            "{\"name\":\"cpu\",\"tags\":{\"host\":\"web-1\"},\"columns\":[\"time\",\"host\"],\"values\":[[\"2023-11-14T22:00:00Z\",\"field-a\"]]}]}]}";
      case "multi":
         return "{\"results\":[{\"statement_id\":0},{\"statement_id\":1,\"series\":[" + series +
            "]}]}";
      case "chunked":
         // the first of several documents; the connector parses only that one
         return "{\"results\":[{\"statement_id\":0,\"series\":[{\"name\":\"cpu\",\"columns\":" +
            "[\"time\",\"usage_user\"],\"values\":[" + rows(0, 2) + "],\"partial\":true}]," +
            "\"partial\":true}]}";
      case "nosuchdb":
         return "{\"error\":\"database not found\"}";
      default:
         return "{\"results\":[{\"statement_id\":0}]}";
      }
   }

   private static String rows(int from, int to) {
      final StringBuilder rows = new StringBuilder();

      for(int i = from; i < to; i++) {
         if(rows.length() > 0) {
            rows.append(',');
         }

         rows.append("[\"2023-11-14T22:").append(String.format("%02d", i)).append(":00Z\",")
            .append(i + 0.5).append(']');
      }

      return rows.toString();
   }

   private static Map<String, String> params(String raw) {
      final Map<String, String> params = new LinkedHashMap<>();

      if(raw != null) {
         for(String pair : raw.split("&")) {
            final int eq = pair.indexOf('=');

            if(eq > 0) {
               params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                          URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
         }
      }

      return params;
   }

   private final HttpServer server;
   private final int buckets;
   private final int users;
   private final int tasks;
   private final List<String> requests = new ArrayList<>();
}
