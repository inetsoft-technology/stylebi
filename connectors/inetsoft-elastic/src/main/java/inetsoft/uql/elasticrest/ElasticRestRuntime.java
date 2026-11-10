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
package inetsoft.uql.elasticrest;

import com.jayway.jsonpath.*;
import com.jayway.jsonpath.internal.JsonFormatter;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.*;
import inetsoft.util.Tool;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ElasticRestRuntime extends TabularRuntime implements TabularCatalogProvider {
   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      return ElasticCatalog.listDatasets((ElasticRestDataSource) dataSource);
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      return ElasticCatalog.describeDataset((ElasticRestDataSource) dataSource, datasetId);
   }

   public XTableNode runQuery(TabularQuery query0, VariableTable params) {
      ElasticRestQuery query = (ElasticRestQuery) query0;
      boolean expanded = query.isExpanded();
      InputStream input = null;

      try {
         URLConnection conn = getConnection(query);
         String jsonpath = query.getJsonPath();
         input = conn.getInputStream();

         if(jsonpath == null || jsonpath.isEmpty()) {
            jsonpath = "$";
         }

         DocumentContext response = JsonPath.using(Configuration.builder()
                           .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                           .jsonProvider(jsonProvider)
                           .build())
            .parse(input);
         Object rc = response.read(jsonpath);
         String cappedWarning = getCappedSearchWarning(
            jsonpath, readOrNull(response, "$.hits.total"), readOrNull(response, "$.hits.hits"));
         final BaseJsonTable table;

         if(expanded) {
            table = new ExpandedJsonTable(query.getExpandedPath());
            ((ExpandedJsonTable) table).setAllowEmptyLists(true);
         }
         else {
            table = new JsonTable();
            table.setMaxRows(query.getMaxRows());
         }

         table.applyQueryColumnTypes(query);
         table.load(rc);

         LOG.debug("ElasticSearch Rest query JSON result: \n" +
                   JsonFormatter.prettyPrint(rc.toString()));

         if(cappedWarning != null) {
            Tool.addUserWarning(cappedWarning);
         }

         return table;
      }
      catch(Exception ex) {
         LOG.warn("Error executing Elasticsearch Rest query: " + createURL(query), ex);
         Tool.addUserMessage("Error executing Elasticsearch Rest query: " +
              createURL(query) + "(" + ex.getMessage() + ")");
         handleError(params, ex, () -> null);
      }
      finally {
         IOUtils.closeQuietly(input);
      }

      return null;
   }

   /**
    * Returns a one-line warning when a search response holds fewer hits than the documents it
    * matched, or {@code null} when the table read every matching document (or there is nothing to
    * compare).
    *
    * <p>A single {@code _search} returns at most {@code size} hits (10 when the request names none),
    * and never more than the index's {@code max_result_window}, so a table built from
    * {@code hits.hits} can silently hold only part of the result. The check compares the server's
    * own {@code hits.total} with the number of hits the server returned, never with the rows the
    * table kept, so the query's max-rows limit (applied after the fetch) does not raise it.
    *
    * <p>Elasticsearch 7+ reports {@code hits.total} as {@code {"value": n, "relation": "eq"|"gte"}}
    * and, by default, stops counting at 10000 with {@code relation: "gte"} - so a 10000-hit page of a
    * larger index has {@code value == hits returned} and only the relation reveals the cap.
    * Elasticsearch 6 (and {@code rest_total_hits_as_int}) reports a bare number. A missing total
    * ({@code track_total_hits: false}) gives nothing to compare, so no warning is raised.
    *
    * <p>Only a json path reading the hits themselves ({@code $.hits.hits...}) is checked. An
    * aggregation query reads {@code $.aggregations...} and legitimately returns fewer hits than
    * matched; a path reading the whole response ({@code $}, {@code $.hits}) leaves the hits to the
    * caller, so neither warns.
    *
    * @param jsonPath  the query's json path, already defaulted to {@code $}.
    * @param hitsTotal the response's {@code hits.total}, or {@code null} if absent.
    * @param hits      the response's {@code hits.hits}, or {@code null} if absent.
    */
   static String getCappedSearchWarning(String jsonPath, Object hitsTotal, Object hits) {
      if(jsonPath == null || !HITS_PATH.matcher(jsonPath.replaceAll("\\s", "")).find() ||
         !(hits instanceof List<?> hitList))
      {
         return null;
      }

      long total;
      boolean lowerBound = false;

      if(hitsTotal instanceof Number number) {
         total = number.longValue();
      }
      else if(hitsTotal instanceof Map<?, ?> map && map.get("value") instanceof Number number) {
         total = number.longValue();
         lowerBound = "gte".equals(map.get("relation"));
      }
      else {
         return null;
      }

      int returned = hitList.size();

      if(!lowerBound && total <= returned) {
         return null;
      }

      return "The Elasticsearch search returned " + returned + " of " +
         (lowerBound ? "at least " : "") + total + " matching documents, so the table holds only " +
         "part of the result. Narrow the query (suffix or filter) to read the rest; a single " +
         "search returns at most its size (10 by default) and never more than the index's " +
         "max_result_window (10000 by default).";
   }

   private static Object readOrNull(DocumentContext response, String path) {
      try {
         return response.read(path);
      }
      catch(Exception ex) {
         // the response is not a search response (e.g. an array from a _cat endpoint)
         return null;
      }
   }

   public void testDataSource(TabularDataSource ds0,
                              VariableTable params) throws Exception
   {
      HttpURLConnection conn = null;

      try {
         ElasticRestDataSource restDS = (ElasticRestDataSource) ds0;
         URL url = new URL(restDS.getURL());
         conn = (HttpURLConnection) url.openConnection();

         String userpass = restDS.getUser() + ":" + restDS.getPassword();
         String basicAuth = "Basic " +
            Base64.encodeBase64String(userpass.getBytes(StandardCharsets.UTF_8));
         conn.setRequestProperty("Authorization", basicAuth);
         conn.getInputStream();
      }
      catch(Exception exc) {
         if(conn.getResponseCode() == 401) {
            throw exc;
         }
         else if(conn.getResponseCode() == 403) {
            conn.connect();
         }
      }
   }

   /**
    * Reads one of the cluster's metadata endpoints with a GET and returns the response body.
    *
    * <p><b>This exists because {@link #getConnection} cannot be reused for a metadata read, and it
    * must not be "simplified" back into it.</b> {@code getConnection} calls
    * {@code conn.setDoOutput(true)} unconditionally, which makes {@link HttpURLConnection} issue a
    * POST, and it writes {@code query.getFilter()} as the request body. {@code _search} accepts
    * POST, so {@code runQuery} is unaffected — but neither endpoint {@link ElasticCatalog} reads
    * does. Measured against Elasticsearch 7.9.2:
    *
    * <ul>
    * <li>{@code POST /_cat/indices} answers HTTP 405. It is a GET-only endpoint.</li>
    * <li>{@code POST /{index}/_mapping} with no body answers HTTP 400 — and <b>with</b> a body it
    *     is not a read at all: it is the mapping-UPDATE API, answers 200, and adds the fields the
    *     body names to the live index. A mapping addition cannot be undone without reindexing.
    *     So routing a catalog read through {@code getConnection} would not merely fail; given a
    *     query carrying a filter it would write to the user's index, which
    *     {@link TabularCatalogProvider} forbids in as many words ("must not mutate the passed data
    *     source beyond what a normal connection already does").</li>
    * </ul>
    *
    * <p>Only the basic-auth header and the URL joining are shared, through {@link #applyBasicAuth}
    * and {@link #joinURL}.
    *
    * <p>A non-2xx response is turned into an exception carrying the status and the server's own
    * error body, rather than the bare {@code IOException} {@code getInputStream} throws on its own —
    * Elasticsearch explains itself well in that body (an unknown index answers 404 with an
    * {@code index_not_found_exception}) and the SPI's callers surface the message to a user.
    *
    * @param path already-encoded, and prefixed with {@code /}. This method does not itself
    *             validate an index name embedded in {@code path} — it trusts the caller. For
    *             {@link ElasticCatalog}, that trust is warranted only because
    *             {@link ElasticCatalog#validateIndexName} rejects a {@code datasetId} containing a
    *             character Elasticsearch itself forbids (space, {@code \/*?"<>|,#}), or shaped
    *             like {@code .}/{@code ..}/a leading {@code +}, before ever building a path from
    *             it. That check cannot and does not confirm the id came from this data source's
    *             own {@code listDatasets} — the SPI does not let a connector verify that, and
    *             {@code SharepointOnlineCatalog}'s class javadoc argues at length why that gap is
    *             accepted SPI-wide rather than fixed per-connector. Percent-encoding beyond what
    *             {@link #joinURL} already does is unnecessary only because of the character
    *             restriction above, not because of anything about the caller.
    */
   static String getMetadata(ElasticRestDataSource ds, String path) throws Exception {
      String urlString = joinURL(ds.getURL(), path);
      HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/json");
      applyBasicAuth(conn, ds);

      int status = conn.getResponseCode();

      if(status < 200 || status >= 300) {
         String body;

         try(InputStream error = conn.getErrorStream()) {
            body = error == null ? "" : IOUtils.toString(error, StandardCharsets.UTF_8);
         }
         catch(Exception ignore) {
            body = "";
         }

         throw new Exception("Elasticsearch answered HTTP " + status + " for " + urlString +
                             (body.isEmpty() ? "" : ": " + body));
      }

      try(InputStream input = conn.getInputStream()) {
         return IOUtils.toString(input, StandardCharsets.UTF_8);
      }
   }

   private static void applyBasicAuth(URLConnection conn, ElasticRestDataSource ds) {
      String user = ds.getUser();
      String password = ds.getPassword();

      if(user != null && password != null) {
         String credential = new String(
            Base64.encodeBase64((user + ":" + password).getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.US_ASCII);
         conn.setRequestProperty("Authorization", "Basic " + credential);
      }
   }

   private URLConnection getConnection(ElasticRestQuery query) throws Exception {
      ElasticRestDataSource ds = (ElasticRestDataSource) query.getDataSource();

      URL url = new URL(createURL(query));
      URLConnection conn = url.openConnection();

      applyBasicAuth(conn, ds);

      conn.setDoOutput(true);

      if(query.getFilter() != null) {
         conn.setRequestProperty("Content-Type", "application/json");
         conn.setRequestProperty("Accept", "application/json");

         OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
         wr.write(query.getFilter());
         wr.flush();
      }

      return conn;
   }

   private String createURL(ElasticRestQuery query) {
      ElasticRestDataSource ds = (ElasticRestDataSource) query.getDataSource();
      return joinURL(ds.getURL(), query.getSuffix());
   }

   /**
    * Appends a suffix to the data source URL without doubling or dropping the separating slash.
    * Extracted from {@link #createURL} so {@link #getMetadata} joins its paths the same way a query
    * does, rather than growing a second, subtly different copy of this rule.
    */
   private static String joinURL(String url, String suffix) {
      if(suffix != null) {
          if(url.endsWith("/") && suffix.startsWith("/")) {
             url += suffix.substring(1);
          }
          else if(!url.endsWith("/") && !suffix.startsWith("/")) {
             url += "/" + suffix;
          }
          else {
             url += suffix;
          }
      }

      // only encode space (which is never allowed in url) but other characters
      // would need to be encoded explicitly to avoid confusion
      url = url.replaceAll(" ", "%20");
      return url;
   }

   // $.hits.hits / $['hits']['hits'] / $["hits"]["hits"], alone or followed by [ or .
   private static final Pattern HITS_PATH = Pattern.compile(
      "^\\$(\\.hits\\.hits|\\['hits']\\['hits']|\\[\"hits\"]\\[\"hits\"])($|[.\\[])");
   private static final JsonProvider jsonProvider = new JacksonJsonProvider();
   private static final Logger LOG = LoggerFactory.getLogger(ElasticRestRuntime.class.getName());
}
