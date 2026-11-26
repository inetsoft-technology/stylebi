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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Socrata dataset through the SODA 2.1 {@code /resource/<id>.json} endpoint and hands it to
 * {@link DatagovTable} in the shape the connector has always read: a {@code meta.view.columns}
 * list plus a {@code data} array of positional rows, as the retired
 * {@code /api/views/<id>/rows.json} export returned them.
 *
 * <p>Socrata deprecated {@code rows.json} (sunset 2026-12-22, HTTP 410 during brownouts). The two
 * endpoints differ in shape, so this class rebuilds the old output cell for cell instead of
 * teaching {@link DatagovTable} a second input format:
 * <ul>
 *   <li>the column list comes from the dataset metadata ({@code /api/views/<id>.json}), never from
 *       the keys of the rows, because SODA omits the key of a null cell (an all-null column has no
 *       key in any row);</li>
 *   <li>the eight system columns rows.json prepends are kept ({@code sid} from {@code :id}, the two
 *       epoch timestamps from {@code :created_at}/{@code :updated_at}, {@code id} filled with the
 *       sid because SODA has no per-row UUID), so saved worksheets that bind them keep working;</li>
 *   <li>values SODA encodes differently (dates as ISO text, geometry as GeoJSON, location/url/phone
 *       as objects) are converted back to the rows.json encodings;</li>
 *   <li>rows are paged with {@code $limit}/{@code $offset} in SODA's default order, which is the
 *       order rows.json returned them in - but only when the request selects the system fields
 *       ({@code $select=:*,*}, which the connector needs anyway for sid and the timestamps):
 *       without it SODA returns some datasets in another order, and an added {@code $order}
 *       reorders the first rows of others. SODA otherwise silently stops at 1000 rows.</li>
 * </ul>
 *
 * <p>A saved query whose URL suffix still names {@code /api/views/<id>/rows.json} is translated to
 * the SODA request at run time ({@code max_rows} becomes the row cap, {@code search} becomes
 * {@code $q}, any other legacy parameter is ignored as rows.json ignored it), so no saved asset has
 * to be rewritten.
 */
final class DatagovSoda {
   private DatagovSoda() {
   }

   /**
    * A parsed request for one dataset.
    *
    * @param origin    scheme, host and port of the Socrata site.
    * @param datasetId the dataset's 4x4 id.
    * @param legacy    true if the request came from a retired rows.json URL.
    * @param limit     the row cap from the URL ({@code max_rows} or {@code $limit}), or
    *                  {@link #NO_LIMIT}; 0 means no rows.
    * @param offset    the first row to read ({@code $offset}).
    * @param search    the raw, already encoded full-text term, or null.
    * @param filters   raw, already encoded parameters passed through to SODA unchanged.
    */
   record Request(String origin, String datasetId, boolean legacy, int limit, int offset,
                  String search, List<String> filters)
   {
   }

   /**
    * Parses a resolved query URL.
    *
    * @param url the URL {@code DatagovRuntime} resolved from the data source URL and the suffix.
    *
    * @return the request, or null if the URL is neither a rows.json nor a {@code /resource/} URL
    *         (such a URL is read as a rows.json shaped document, as before).
    *
    * @throws Exception if a native SODA parameter cannot be honoured.
    */
   static Request parse(String url) throws Exception {
      URI uri;

      try {
         uri = new URI(url.trim());
      }
      catch(URISyntaxException e) {
         return null;
      }

      String scheme = uri.getScheme();

      if(uri.getHost() == null ||
         !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
      {
         return null;
      }

      String path = uri.getRawPath() == null ? "" : uri.getRawPath();
      boolean legacy = true;
      Matcher matcher = LEGACY_PATH.matcher(path);

      if(!matcher.matches()) {
         legacy = false;
         matcher = NATIVE_PATH.matcher(path);

         if(!matcher.matches()) {
            return null;
         }
      }

      String origin = scheme.toLowerCase() + "://" + uri.getHost() +
         (uri.getPort() == -1 ? "" : ":" + uri.getPort());
      int limit = NO_LIMIT;
      int offset = 0;
      String search = null;
      List<String> filters = new ArrayList<>();
      String query = uri.getRawQuery();

      if(query != null) {
         for(String param : query.split("&")) {
            if(param.isEmpty()) {
               continue;
            }

            int eq = param.indexOf('=');
            String name = decode(eq < 0 ? param : param.substring(0, eq));
            String value = eq < 0 ? "" : param.substring(eq + 1);

            if(legacy) {
               if("max_rows".equals(name)) {
                  Integer rows = parseCount(value);

                  if(rows != null) {
                     limit = rows;
                  }
               }
               else if("search".equals(name)) {
                  search = value.isEmpty() ? null : value;
               }
               else {
                  // rows.json ignored every other parameter (accessType=DOWNLOAD is on Socrata's
                  // own export link), and so must the translation
                  LOG.debug("Ignoring legacy rows.json parameter '{}'", name);
               }
            }
            else if("$limit".equals(name)) {
               limit = requireCount(name, value);
            }
            else if("$offset".equals(name)) {
               offset = requireCount(name, value);
            }
            else if("$select".equals(name) || "$group".equals(name)) {
               throw new Exception("The data.gov URL suffix uses " + name + ", which changes the " +
                  "set of columns the dataset declares; the connector builds the declared columns " +
                  "and does not support it. Remove " + name + " from the suffix and use $where " +
                  "or a worksheet column selection instead");
            }
            else {
               filters.add(param);
            }
         }
      }

      return new Request(origin, matcher.group(1), legacy, limit, offset, search, filters);
   }

   /**
    * Reads the dataset and returns it in the rows.json shape.
    *
    * @param ds           the data source, for the credentials sent to the site.
    * @param request      the parsed request.
    * @param queryMaxRows the query's row cap, or 0 for all rows.
    * @param pageSize     the number of rows to ask for per request.
    *
    * @return an object with {@code meta.view.columns} and {@code data}.
    */
   static JsonObject fetch(DatagovDataSource ds, Request request, int queryMaxRows, int pageSize)
      throws Exception
   {
      if(request.legacy() && WARNED.compareAndSet(false, true)) {
         LOG.warn("Data.gov dataset {} is read through a retired /api/views/<id>/rows.json URL " +
                  "suffix; it is served from /resource/<id>.json instead. Update the suffix to " +
                  "/resource/{}.json.", request.datasetId(), request.datasetId());
      }

      String metaUrl = request.origin() + "/api/views/" + request.datasetId() + ".json";
      String context = "Socrata dataset '" + request.datasetId() + "'";
      JsonObject view = DatagovCatalog.parseObject(
         DatagovRuntime.getSiteMetadata(ds, metaUrl), context);
      JsonArray viewColumns = view.getJsonArray("columns");

      if(viewColumns == null || viewColumns.isEmpty()) {
         throw new Exception(context + " declares no columns.");
      }

      JsonArrayBuilder data = Json.createArrayBuilder();
      int cap = effectiveCap(request.limit(), queryMaxRows);

      if(cap != 0) {
         int got = 0;
         int offset = request.offset();

         while(true) {
            int want = cap == NO_LIMIT ? pageSize : Math.min(pageSize, cap - got);
            String url = pageUrl(request, want, offset);
            JsonValue page = readValue(DatagovRuntime.getSiteData(ds, url), url);

            if(page.getValueType() != JsonValue.ValueType.ARRAY) {
               throw new Exception("Socrata answered " + page.getValueType() + " instead of a " +
                                   "JSON array of rows for " + url);
            }

            JsonArray rows = page.asJsonArray();

            for(JsonValue row : rows) {
               if(row.getValueType() != JsonValue.ValueType.OBJECT) {
                  throw new Exception("Socrata returned a row that is not a JSON object for " +
                                      url);
               }

               data.add(legacyRow(row.asJsonObject(), viewColumns));
            }

            got += rows.size();
            offset += rows.size();

            if(rows.size() < want || cap != NO_LIMIT && got >= cap) {
               break;
            }
         }
      }

      JsonObject viewShape = Json.createObjectBuilder()
         .add("columns", withSystemColumns(viewColumns)).build();
      JsonObject meta = Json.createObjectBuilder().add("view", viewShape).build();
      return Json.createObjectBuilder().add("meta", meta).add("data", data).build();
   }

   /**
    * @return the eight system columns rows.json prepended, followed by the dataset's own columns.
    */
   static JsonArray withSystemColumns(JsonArray viewColumns) {
      JsonArrayBuilder all = Json.createArrayBuilder();

      for(String[] column : SYSTEM_COLUMNS) {
         all.add(Json.createObjectBuilder()
                    .add("name", column[0])
                    .add("dataTypeName", "meta_data")
                    .add("fieldName", column[1]));
      }

      for(JsonValue v : viewColumns) {
         all.add(v);
      }

      return all.build();
   }

   private static String pageUrl(Request request, int limit, int offset) {
      StringBuilder url = new StringBuilder(request.origin())
         .append("/resource/").append(request.datasetId()).append(".json?$select=:*,*");

      if(request.search() != null) {
         url.append("&$q=").append(request.search());
      }

      for(String filter : request.filters()) {
         url.append('&').append(filter);
      }

      return url.append("&$limit=").append(limit).append("&$offset=").append(offset).toString();
   }

   private static int effectiveCap(int urlLimit, int queryMaxRows) {
      int query = queryMaxRows > 0 ? queryMaxRows : NO_LIMIT;

      if(urlLimit == NO_LIMIT) {
         return query;
      }

      return query == NO_LIMIT ? urlLimit : Math.min(urlLimit, query);
   }

   private static JsonValue readValue(String json, String url) throws Exception {
      try(JsonReader reader = Json.createReader(new StringReader(json))) {
         return reader.readValue();
      }
      catch(JsonException e) {
         throw new Exception("Socrata answered with invalid JSON for " + url, e);
      }
   }

   /**
    * Rebuilds one rows.json row (positional, system columns first) from a SODA row.
    */
   private static JsonArray legacyRow(JsonObject row, JsonArray viewColumns) {
      JsonArrayBuilder out = Json.createArrayBuilder();
      JsonValue sid = row.get(":id");
      out.add(sid == null ? JsonValue.NULL : sid);                 // sid
      out.add(sid == null ? JsonValue.NULL : sid);                 // id: no per-row UUID in SODA
      out.add(0);                                                  // position
      out.add(epochSeconds(row.get(":created_at")));               // created_at
      out.add(JsonValue.NULL);                                     // created_meta
      out.add(epochSeconds(row.get(":updated_at")));               // updated_at
      out.add(JsonValue.NULL);                                     // updated_meta
      out.add("{ }");                                              // meta

      for(JsonValue v : viewColumns) {
         JsonObject column = v.asJsonObject();
         String fieldName = column.containsKey("fieldName") && !column.isNull("fieldName")
            ? column.getString("fieldName") : null;
         String type = column.containsKey("dataTypeName") && !column.isNull("dataTypeName")
            ? column.getString("dataTypeName") : null;
         out.add(convert(fieldName == null ? null : row.get(fieldName), type));
      }

      return out.build();
   }

   /**
    * Converts one SODA value to the encoding rows.json used for the same column type.
    */
   private static JsonValue convert(JsonValue value, String type) {
      if(value == null || value.getValueType() == JsonValue.ValueType.NULL) {
         return JsonValue.NULL;
      }

      if(type == null) {
         return value;
      }

      JsonValue.ValueType kind = value.getValueType();

      switch(type) {
      case "date":
         return kind == JsonValue.ValueType.STRING ? epochSeconds(value) : value;
      case "calendar_date":
         return kind == JsonValue.ValueType.STRING
            ? Json.createValue(FRACTION.matcher(((JsonString) value).getString()).replaceFirst(""))
            : value;
      case "point":
      case "multipoint":
      case "line":
      case "multiline":
      case "polygon":
      case "multipolygon":
         return kind == JsonValue.ValueType.OBJECT
            ? Json.createValue(toWkt(value.asJsonObject())) : value;
      case "location":
         if(kind == JsonValue.ValueType.OBJECT) {
            JsonObject loc = value.asJsonObject();
            return Json.createArrayBuilder()
               .add(member(loc, "human_address")).add(member(loc, "latitude"))
               .add(member(loc, "longitude")).add(JsonValue.NULL).add(JsonValue.FALSE).build();
         }

         return value;
      case "url":
         if(kind == JsonValue.ValueType.OBJECT) {
            JsonObject url = value.asJsonObject();
            return Json.createArrayBuilder()
               .add(member(url, "url")).add(member(url, "description")).build();
         }

         return value;
      case "phone":
         if(kind == JsonValue.ValueType.OBJECT) {
            JsonObject phone = value.asJsonObject();
            return Json.createArrayBuilder()
               .add(member(phone, "phone_number")).add(member(phone, "phone_type")).build();
         }

         return value;
      default:
         return value;
      }
   }

   private static JsonValue member(JsonObject obj, String key) {
      JsonValue v = obj.get(key);
      return v == null ? JsonValue.NULL : v;
   }

   /**
    * @return whole seconds since the epoch for an ISO timestamp (an unzoned one is read as UTC, as
    *         Socrata stores floating timestamps), the original text if it is not a timestamp, or
    *         JSON null for no value.
    */
   private static JsonValue epochSeconds(JsonValue value) {
      if(value == null || value.getValueType() != JsonValue.ValueType.STRING) {
         return value == null ? JsonValue.NULL : value;
      }

      String text = ((JsonString) value).getString();

      try {
         Object parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(
            text.trim(), OffsetDateTime::from, LocalDateTime::from);

         return Json.createValue(parsed instanceof OffsetDateTime
            ? ((OffsetDateTime) parsed).toEpochSecond()
            : ((LocalDateTime) parsed).toEpochSecond(ZoneOffset.UTC));
      }
      catch(DateTimeException e) {
         return value;
      }
   }

   /**
    * Converts a GeoJSON geometry to the WKT text rows.json carried for the same column.
    */
   static String toWkt(JsonObject geometry) {
      try {
         String type = geometry.getString("type");
         JsonArray c = geometry.getJsonArray("coordinates");

         switch(type) {
         case "Point":
            return "POINT (" + position(c) + ")";
         case "MultiPoint":
            return "MULTIPOINT (" + join(c, p -> "(" + position(p.asJsonArray()) + ")") + ")";
         case "LineString":
            return "LINESTRING (" + line(c) + ")";
         case "MultiLineString":
            return "MULTILINESTRING (" + join(c, l -> "(" + line(l.asJsonArray()) + ")") + ")";
         case "Polygon":
            return "POLYGON (" + join(c, r -> "(" + line(r.asJsonArray()) + ")") + ")";
         case "MultiPolygon":
            return "MULTIPOLYGON (" + join(c, poly -> "(" + join(poly.asJsonArray(),
               r -> "(" + line(r.asJsonArray()) + ")") + ")") + ")";
         default:
            return geometry.toString();
         }
      }
      catch(RuntimeException e) {
         return geometry.toString();
      }
   }

   private static String line(JsonArray positions) {
      return join(positions, p -> position(p.asJsonArray()));
   }

   private static String join(JsonArray items, Function<JsonValue, String> f) {
      StringBuilder out = new StringBuilder();

      for(JsonValue item : items) {
         if(out.length() > 0) {
            out.append(", ");
         }

         out.append(f.apply(item));
      }

      return out.toString();
   }

   private static String position(JsonArray p) {
      StringBuilder out = new StringBuilder();

      for(JsonValue ordinate : p) {
         if(out.length() > 0) {
            out.append(' ');
         }

         out.append(ordinate.getValueType() == JsonValue.ValueType.NUMBER
            ? ((JsonNumber) ordinate).bigDecimalValue().toPlainString()
            : ((JsonString) ordinate).getString());
      }

      return out.toString();
   }

   private static Integer parseCount(String raw) {
      try {
         int n = Integer.parseInt(decode(raw).trim());
         return n < 0 ? null : n;
      }
      catch(NumberFormatException e) {
         return null;
      }
   }

   private static int requireCount(String name, String raw) throws Exception {
      Integer n = parseCount(raw);

      if(n == null) {
         throw new Exception("The data.gov URL suffix has " + name + "=" + raw +
                             ", which is not a non-negative whole number");
      }

      return n;
   }

   private static String decode(String s) {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
   }

   /** Rows requested per SODA call; SODA's own default of 1000 would silently truncate. */
   static final int PAGE_SIZE = 50_000;

   /** Marks a request with no row cap. */
   static final int NO_LIMIT = -1;

   /** rows.json system columns, in their original order: name and Views API field name. */
   private static final String[][] SYSTEM_COLUMNS = {
      { "sid", ":sid" }, { "id", ":id" }, { "position", ":position" },
      { "created_at", ":created_at" }, { "created_meta", ":created_meta" },
      { "updated_at", ":updated_at" }, { "updated_meta", ":updated_meta" }, { "meta", ":meta" }
   };

   private static final Pattern LEGACY_PATH = Pattern.compile(
      "/api/views/([A-Za-z0-9]{4}-[A-Za-z0-9]{4})/rows\\.json");
   private static final Pattern NATIVE_PATH = Pattern.compile(
      "/resource/([A-Za-z0-9]{4}-[A-Za-z0-9]{4})\\.json");
   private static final Pattern FRACTION = Pattern.compile("\\.\\d+$");
   private static final AtomicBoolean WARNED = new AtomicBoolean();
   private static final Logger LOG = LoggerFactory.getLogger(DatagovSoda.class.getName());
}
