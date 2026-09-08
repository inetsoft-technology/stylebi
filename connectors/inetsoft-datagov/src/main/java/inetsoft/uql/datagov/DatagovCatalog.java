/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Assembles {@link DatagovRuntime}'s {@link TabularCatalogProvider} answers out of two Socrata
 * endpoints: the centrally-hosted Discovery API ({@link #listDatasets}) and one site's own classic
 * Views API metadata ({@link #describeDataset}). Mirrors {@code ElasticCatalog}/{@code
 * MongoCatalog}'s role: package-private, static methods, no state of its own.
 *
 * <p>See {@code DatagovRuntime#requireSiteDomain}/{@code getDiscoveryMetadata}/
 * {@code getSiteMetadata} for why the Discovery API call carries no credentials while the classic
 * API call reuses {@code runQuery}'s existing Basic-Auth-if-configured behavior — they target two
 * different hosts with two different auth expectations.
 */
final class DatagovCatalog {
   private DatagovCatalog() {
   }

   /**
    * @param ds the data source whose {@code URL} host names the Socrata site to enumerate.
    * @return one dataset per Socrata "dataset"-typed asset the site's Discovery API reports,
    *         across every page. {@code relationships()} is always empty — the Discovery API is a
    *         search index over independently published assets, not a data dictionary.
    * @throws Exception if the Discovery API could not be read, answered with an unexpected shape,
    *                   or did not finish paging within {@link #MAX_DISCOVERY_REQUESTS} requests.
    */
   static TabularCatalog listDatasets(DatagovDataSource ds) throws Exception {
      String domain = DatagovRuntime.requireSiteDomain(ds);
      List<TabularDatasetRef> datasets = new ArrayList<>();
      int offset = 0;
      int resultSetSize = Integer.MAX_VALUE;
      int requests = 0;

      while(offset < resultSetSize) {
         if(++requests > MAX_DISCOVERY_REQUESTS) {
            throw new Exception("Socrata Discovery API for domain '" + domain + "' did not " +
               "finish enumerating after " + MAX_DISCOVERY_REQUESTS + " requests (" +
               datasets.size() + " datasets seen so far); refusing to keep paging.");
         }

         String url = "https://api.us.socrata.com/api/catalog/v1?domains=" +
            URLEncoder.encode(domain, StandardCharsets.UTF_8) + "&only=datasets&limit=" +
            DISCOVERY_PAGE_SIZE + "&offset=" + offset;
         String context = "Socrata Discovery API for domain '" + domain + "'";
         JsonObject page = parseObject(DatagovRuntime.getDiscoveryMetadata(url), context);

         Integer size = getInt(page, "resultSetSize");

         if(size == null) {
            throw new Exception(context + " answered with no numeric resultSetSize.");
         }

         resultSetSize = size;
         JsonArray results = page.getJsonArray("results");

         if(results == null) {
            throw new Exception(context + " answered with no results array.");
         }

         for(JsonValue v : results) {
            if(v.getValueType() != JsonValue.ValueType.OBJECT) {
               throw new Exception(context + " returned a result that is not a JSON object.");
            }

            JsonObject resource = v.asJsonObject().getJsonObject("resource");
            String id = resource == null ? null : getString(resource, "id");

            if(id == null || id.isBlank()) {
               throw new Exception(context + " returned a result with no resource.id.");
            }

            // Belt-and-suspenders: `only=datasets` is a real, server-side filter, but this is
            // cheap insurance against a response that includes a non-dataset asset (a chart, map,
            // filtered view, story...) despite the filter -- those are not describable via
            // rows.json, and emitting one here would only surface as a confusing describeDataset
            // failure later.
            String type = getString(resource, "type");

            if(!"dataset".equals(type)) {
               LOG.debug("Skipping Socrata catalog entry {} ({}): type is '{}', not 'dataset'",
                         id, getString(resource, "name"), type);
               continue;
            }

            datasets.add(new TabularDatasetRef(id));
         }

         offset += DISCOVERY_PAGE_SIZE;
      }

      return new TabularCatalog(datasets, List.of());
   }

   /**
    * @param datasetId a Socrata 4x4 id, as {@link #listDatasets} returned it.
    * @throws Exception if the id is not a legal Socrata 4x4 id, the dataset is unknown, or it
    *                   declares no columns.
    */
   static TabularDatasetSchema describeDataset(DatagovDataSource ds, String datasetId)
      throws Exception
   {
      validateDatasetId(datasetId);
      // Guards the same precondition listDatasets guards via requireSiteDomain -- an
      // in-progress/incompletely-configured data source (URL never set) must fail with a
      // message naming what's missing, not a bare NullPointerException out of ds.getURL().trim()
      // below. The returned domain itself is unused here; only the validation side effect matters.
      DatagovRuntime.requireSiteDomain(ds);

      URI base = URI.create(ds.getURL().trim());
      // An ABSOLUTE-PATH reference replaces base's whole path when resolved, keeping only
      // scheme+authority -- this reaches the site's classic API root regardless of whether the
      // operator's URL points at the site root or at one specific resource's full path (see A10).
      URI target = base.resolve("/api/views/" + datasetId + "/rows.json?max_rows=0");
      String context = "Socrata dataset '" + datasetId + "'";

      String json = DatagovRuntime.getSiteMetadata(ds, target.toString());
      JsonObject root = parseObject(json, context);
      JsonObject meta = root.getJsonObject("meta");
      JsonObject view = meta == null ? null : meta.getJsonObject("view");

      if(view == null) {
         throw new Exception(context + " answered with no meta.view block.");
      }

      JsonArray rawColumns = view.getJsonArray("columns");

      if(rawColumns == null || rawColumns.isEmpty()) {
         throw new Exception(context + " declares no columns.");
      }

      List<TabularColumn> columns = new ArrayList<>();
      List<String> unrecognizedTypes = new ArrayList<>();
      Long rowIdentifierColumnId = getLong(view, "rowIdentifierColumnId");
      String keyColumnName = null;

      for(JsonValue v : rawColumns) {
         if(v.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new Exception(context + " declares a column that is not a JSON object.");
         }

         JsonObject col = v.asJsonObject();
         String name = getString(col, "name");

         if(name == null || name.isBlank()) {
            throw new Exception(context + " declares a column with no name.");
         }

         String dataTypeName = getString(col, "dataTypeName");
         String xtype = TYPES.get(dataTypeName);

         if(xtype == null) {
            xtype = XSchema.STRING;
            unrecognizedTypes.add(name + " (" + dataTypeName + ")");
            LOG.warn("Unrecognized Socrata dataTypeName '{}' on column '{}' of dataset '{}'; " +
                     "reporting it as {}.", dataTypeName, name, datasetId, XSchema.STRING);
         }

         String description = getString(col, "description");

         if(description != null) {
            description = description.strip();

            if(description.isEmpty()) {
               description = null;
            }
         }

         columns.add(new TabularColumn(name, xtype, description, null, null));

         Long colId = getLong(col, "id");

         if(rowIdentifierColumnId != null && rowIdentifierColumnId.equals(colId)) {
            keyColumnName = name;
         }
      }

      Map<String, String> params = new LinkedHashMap<>();
      // Fully filled, with no empty value -- once the dataset id is known the exact suffix that
      // reproduces this dataset's rows is completely determined. The classic /api/views/{id}/
      // rows.json path is used deliberately, not the modern /resource/{id}.json path: DatagovTable's
      // constructor requires meta.view.columns + a data array-of-arrays, exactly and only what the
      // classic endpoint returns.
      params.put("suffix", "/api/views/" + datasetId + "/rows.json");

      return new TabularDatasetSchema(datasetId, columns,
         keyColumnName == null ? List.of() : List.of(keyColumnName), params, false,
         datasetDescription(datasetId, view, unrecognizedTypes));
   }

   /**
    * Rejects a {@code datasetId} that could not possibly be a real Socrata 4x4 id before it ever
    * reaches {@link DatagovRuntime#getSiteMetadata} and is embedded into a live request URL.
    * {@code TabularCatalogService.describeTable} passes a caller-supplied target straight through
    * with no prior {@link #listDatasets} check, so this is the only place that can refuse a shape
    * built to escape the URL path segment.
    *
    * <p>Every 4x4 id observed live while this connector was written
    * ({@code erm2-nwe9}, {@code 8wbx-tsch}, {@code w7w3-xahh}, {@code k397-673e}) matches this
    * pattern; Socrata's own docs and every third-party client describe the id as exactly this
    * shape (4 lowercase alphanumerics, a hyphen, 4 more).
    */
   private static void validateDatasetId(String datasetId) throws Exception {
      if(datasetId == null || datasetId.isBlank()) {
         throw new Exception("Datagov was asked to describe a blank dataset id.");
      }

      if(!SOCRATA_4X4.matcher(datasetId).matches()) {
         throw new Exception("Datagov was asked to describe dataset id '" + datasetId + "', " +
            "which is not a Socrata 4x4 id (exactly 4 lowercase letters/digits, a hyphen, 4 " +
            "more lowercase letters/digits) -- refusing to embed it into a request URL.");
      }
   }

   /**
    * The dataset-level description: the site's own {@code view.description}, verbatim, when there
    * is one, plus this connector's own structural note (row grain, how the columns were read, and
    * which columns fell back to a string because their Socrata type was not recognized).
    */
   private static String datasetDescription(String datasetId, JsonObject view,
                                            List<String> unrecognizedTypes)
   {
      StringBuilder text = new StringBuilder();
      String declared = getString(view, "description");

      if(declared != null && !declared.isBlank()) {
         text.append(declared.strip()).append("\n\n");
      }

      text.append("Socrata dataset '").append(datasetId)
         .append("'. One row per record, columns read from the dataset's own declared metadata " +
                 "(GET /api/views/").append(datasetId).append("/rows.json?max_rows=0) -- not a " +
                 "sample, so the column list is complete for the columns Socrata declares.");

      if(!unrecognizedTypes.isEmpty()) {
         text.append("\n\nThis connector does not recognize the Socrata dataTypeName declared " +
               "for ").append(unrecognizedTypes.size()).append(" column(s), so each is reported " +
               "as a string rather than its declared type: ")
            .append(String.join(", ", unrecognizedTypes)).append(".");
      }

      return text.toString();
   }

   private static JsonObject parseObject(String json, String context) throws Exception {
      JsonValue value;

      try(JsonReader reader = Json.createReader(new StringReader(json))) {
         value = reader.readValue();
      }
      catch(JsonException e) {
         throw new Exception(context + " answered with invalid JSON.", e);
      }

      if(value.getValueType() != JsonValue.ValueType.OBJECT) {
         throw new Exception(context + " answered with " + value.getValueType() +
                             " instead of a JSON object.");
      }

      return value.asJsonObject();
   }

   private static String getString(JsonObject obj, String key) {
      if(obj == null || !obj.containsKey(key) || obj.isNull(key)) {
         return null;
      }

      JsonValue v = obj.get(key);
      return v.getValueType() == JsonValue.ValueType.STRING ? ((JsonString) v).getString()
         : v.toString();
   }

   private static Long getLong(JsonObject obj, String key) {
      if(obj == null || !obj.containsKey(key) || obj.isNull(key)) {
         return null;
      }

      JsonValue v = obj.get(key);

      if(v.getValueType() == JsonValue.ValueType.NUMBER) {
         return ((JsonNumber) v).longValue();
      }

      try {
         return Long.parseLong(v.toString());
      }
      catch(NumberFormatException e) {
         return null;
      }
   }

   private static Integer getInt(JsonObject obj, String key) {
      Long l = getLong(obj, key);
      return l == null ? null : l.intValue();
   }

   /**
    * Socrata {@code dataTypeName} to {@link XSchema} constant, for the fields that become columns.
    * A {@code Map<String,String>} rather than an exhaustive {@code switch}, deliberately: this is a
    * string from an HTTP JSON response, not a Java enum the driver controls at compile time, so an
    * unrecognized value degrades to {@link XSchema#STRING} with a WARN log and a dataset-description
    * sentence naming it (see {@link #describeDataset}), rather than failing the whole call.
    *
    * <p>{@code number}/{@code money}/{@code percent} map to {@code DECIMAL}, not {@code DOUBLE}:
    * {@code DatagovTable}'s own per-value parse path (its {@code NUMBER} case) produces a
    * {@code BigDecimal} for these -- {@code getClassName}'s {@code Double.class} for the same names
    * is only the fallback type used when zero rows are returned, never what a populated column
    * actually yields. {@code calendar_date}/{@code fixed_timestamp}/{@code floating_timestamp} map
    * to {@code TIME_INSTANT} (a full timestamp), not a bare {@code DATE}.
    */
   private static final Map<String, String> TYPES = Map.ofEntries(
      Map.entry("text", XSchema.STRING),
      Map.entry("number", XSchema.DECIMAL),
      Map.entry("money", XSchema.DECIMAL),
      Map.entry("percent", XSchema.DECIMAL),
      Map.entry("checkbox", XSchema.BOOLEAN),
      Map.entry("calendar_date", XSchema.TIME_INSTANT),
      Map.entry("fixed_timestamp", XSchema.TIME_INSTANT),
      Map.entry("floating_timestamp", XSchema.TIME_INSTANT),
      Map.entry("point", XSchema.STRING),
      Map.entry("location", XSchema.STRING),
      Map.entry("phone", XSchema.STRING),
      Map.entry("url", XSchema.STRING),
      Map.entry("email", XSchema.STRING),
      Map.entry("document", XSchema.STRING),
      Map.entry("photo", XSchema.STRING),
      Map.entry("multipolygon", XSchema.STRING),
      Map.entry("line", XSchema.STRING),
      Map.entry("multiline", XSchema.STRING),
      Map.entry("multipoint", XSchema.STRING),
      Map.entry("polygon", XSchema.STRING),
      Map.entry("flag", XSchema.STRING),
      Map.entry("stars", XSchema.STRING),
      Map.entry("drop_down_list", XSchema.STRING),
      Map.entry("meta_data", XSchema.STRING));

   /**
    * Exactly 4 lowercase letters/digits, a hyphen, 4 more -- Socrata's own 4x4 id shape.
    */
   private static final Pattern SOCRATA_4X4 = Pattern.compile("[a-z0-9]{4}-[a-z0-9]{4}");

   /**
    * Confirmed live: {@code limit=10000} against a real, large Socrata site returned every
    * dataset in one page, HTTP 200.
    */
   private static final int DISCOVERY_PAGE_SIZE = 10000;

   /**
    * A sanity ceiling (up to 500,000 datasets before refusing), not a real-world limit. Hitting it
    * throws rather than truncating, per {@link TabularCatalogProvider}'s own "no limit and no
    * paging" contract -- a loud refusal, never a silent partial catalog.
    */
   private static final int MAX_DISCOVERY_REQUESTS = 50;

   private static final Logger LOG = LoggerFactory.getLogger(DatagovCatalog.class.getName());
}
