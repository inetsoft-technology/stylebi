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

         Object rc = JsonPath.using(Configuration.builder()
                           .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                           .jsonProvider(jsonProvider)
                           .build())
            .parse(input)
            .read(jsonpath);
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
    * @param path already-encoded, and prefixed with {@code /}. Index names are safe to embed
    *             directly: Elasticsearch forbids a space and {@code \/*?"<>|,#} in an index name,
    *             so nothing reaching here needs percent-encoding beyond what {@link #joinURL} does.
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

   private static final JsonProvider jsonProvider = new JacksonJsonProvider();
   private static final Logger LOG = LoggerFactory.getLogger(ElasticRestRuntime.class.getName());
}
