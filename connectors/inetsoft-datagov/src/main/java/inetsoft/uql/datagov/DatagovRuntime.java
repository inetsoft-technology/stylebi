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

import inetsoft.uql.*;

import inetsoft.uql.tabular.*;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.Base64;

/**
 * Runtime implementation for the data.gov data source.
 */
@SuppressWarnings("unused")
public class DatagovRuntime extends TabularRuntime implements TabularCatalogProvider {
   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      return DatagovCatalog.listDatasets((DatagovDataSource) dataSource);
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      return DatagovCatalog.describeDataset((DatagovDataSource) dataSource, datasetId);
   }

   @Override
   public XTableNode runQuery(TabularQuery query0, VariableTable params) {
      DatagovQuery query = (DatagovQuery) query0;
      JsonReader jsonReader = null;
      XTableNode table = null;

      try {
         URLConnection conn = getConnection(query);
         BufferedReader connReader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), "UTF-8"));

         // TODO: Might need to switch this to a streaming based implementation.
         jsonReader = Json.createReader(connReader);
         JsonObject jObj = jsonReader.readObject();
         table = new DatagovTable(jObj, query0.getMaxRows(), query);
      }
      catch(Exception ex) {
         LOG.warn("Error executing data.gov query: " +
            createURL(query), ex);
         handleError(params, ex, () -> null);
      }
      finally {
         IOUtils.closeQuietly(jsonReader);
      }

      return table;
   }

   @Override
   public void testDataSource(TabularDataSource ds0,
                              VariableTable params) throws Exception
   {
      URL url = new URL(((DatagovDataSource) ds0).getURL());
      url.openConnection();
   }

   /**
    * Opens a connection to the web service specified by a query.
    *
    * @param query the query definition.
    *
    * @return the URL connection.
    *
    * @throws Exception if the connection could not be established.
    */
   private URLConnection getConnection(DatagovQuery query) throws Exception {
      DatagovDataSource ds = (DatagovDataSource) query.getDataSource();
      String user = ds.getUser();
      String password = ds.getPassword();

      URL url = new URL(createURL(query));
      URLConnection conn = url.openConnection();

      if(user != null && password != null) {
         String credential = Base64.getEncoder().encodeToString(
            (user + ":" + password).getBytes(StandardCharsets.UTF_8));
         conn.setRequestProperty("Authorization", "Basic " + credential);
      }

      conn.setDoOutput(true);
      return conn;
   }

   /**
    * Creates the URL used to connect to the web service specified by a query.
    *
    * @param query the query definition.
    *
    * @return the URL.
    */
   private String createURL(DatagovQuery query) {
      DatagovDataSource ds = (DatagovDataSource) query.getDataSource();
      URI uri = URI.create(ds.getURL().trim());
      String suffix = query.getSuffix();

      if(suffix != null) {
         uri = uri.resolve(suffix.trim());
      }

      return uri.toString();
   }

   /**
    * The Socrata site domain the catalog SPI needs to reach this data source's site -- the
    * Discovery API ({@code listDatasets}) to enumerate it, the classic Views API
    * ({@code describeDataset}) to describe one dataset of it -- derived from the existing
    * {@code URL} property rather than a new one.
    *
    * <p>{@code createURL} already treats {@code URL} as a real base {@code URI} (a full address
    * with a scheme and host, not a bare hostname), whether the operator pointed it at the site's
    * root or at one specific resource's full path -- either shape shares the same host, so
    * extracting it works regardless of which convention an already-configured data source used.
    * This adds a new capability (catalog enumeration/description) without changing {@code URL}'s
    * existing meaning, so {@code runQuery}/{@code testDataSource} and every already-configured
    * data source are unaffected.
    */
   static String requireSiteDomain(DatagovDataSource ds) throws Exception {
      String url = ds.getURL();

      if(url == null || url.isBlank()) {
         throw new Exception("Datagov data source has no URL configured; the Socrata site's " +
            "domain is needed to reach it.");
      }

      String host;

      try {
         host = new URI(url.trim()).getHost();
      }
      catch(URISyntaxException e) {
         throw new Exception("Datagov data source URL '" + url + "' is not a valid URI; cannot " +
            "determine the Socrata site domain.", e);
      }

      if(host == null || host.isBlank()) {
         throw new Exception("Datagov data source URL '" + url + "' has no host component " +
            "(configure it as a full https://<site>/... address); cannot determine the Socrata " +
            "site domain.");
      }

      return host;
   }

   /**
    * Reads Socrata's centrally-hosted Discovery API -- a fixed host, unrelated to any one site,
    * and (confirmed live) anonymous-readable. Deliberately sends NO credentials:
    * {@code ds.getUser()}/{@code getPassword()} authenticate against the SITE the operator
    * configured ({@code ds.getURL()}'s host), and {@code api.us.socrata.com} is a different host
    * entirely -- forwarding site credentials to it would be sending them somewhere the operator
    * never pointed them at, for a call that does not need them.
    */
   static String getDiscoveryMetadata(String url) throws Exception {
      HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/json");
      return readMetadataResponse(conn, url);
   }

   /**
    * Reads one of the configured site's own classic-API endpoints (used only for
    * {@code describeDataset}'s {@code rows.json?max_rows=0} call). A SEPARATE method from
    * {@link #getConnection} rather than a reuse of it: {@code getConnection} is shaped around
    * {@code runQuery}'s POST-with-no-body semantics ({@code conn.setDoOutput(true)}
    * unconditionally) and this call must stay GET-only per {@link TabularCatalogProvider}'s "must
    * not mutate... beyond what a normal connection already does" contract -- a metadata read has no
    * business ever becoming a write. Applies the SAME Basic-Auth-if-configured behavior
    * {@link #getConnection} already applies, because this call targets the SAME host
    * {@code runQuery} targets -- reproducing the existing user/password-vs-App-Token mismatch
    * faithfully rather than fixing it (a residual gap, not this change's problem to solve).
    *
    * @param url the full, already-resolved request URL.
    */
   static String getSiteMetadata(DatagovDataSource ds, String url) throws Exception {
      HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/json");
      String user = ds.getUser();
      String password = ds.getPassword();

      if(user != null && password != null) {
         String credential = Base64.getEncoder().encodeToString(
            (user + ":" + password).getBytes(StandardCharsets.UTF_8));
         conn.setRequestProperty("Authorization", "Basic " + credential);
      }

      return readMetadataResponse(conn, url);
   }

   /**
    * A non-2xx response is turned into an exception carrying the status and the server's own
    * error body, rather than the bare {@code IOException} {@code getInputStream} throws on its
    * own -- this is what makes {@code listDatasets}/{@code describeDataset} throw rather than
    * degrade to an empty/fabricated result on a request failure: the failure propagates as an
    * exception through the whole call chain with no try/catch needed at the catalog layer.
    */
   private static String readMetadataResponse(HttpURLConnection conn, String urlString)
      throws Exception
   {
      int status = conn.getResponseCode();

      if(status < 200 || status >= 300) {
         String body;

         try(InputStream error = conn.getErrorStream()) {
            body = error == null ? "" : IOUtils.toString(error, StandardCharsets.UTF_8);
         }
         catch(Exception ignore) {
            body = "";
         }

         throw new Exception("Datagov site answered HTTP " + status + " for " + urlString +
                             (body.isEmpty() ? "" : ": " + body));
      }

      try(InputStream input = conn.getInputStream()) {
         return IOUtils.toString(input, StandardCharsets.UTF_8);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(DatagovRuntime.class.getName());
}
