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
package inetsoft.uql.elasticrest;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Assembles {@link ElasticRestRuntime}'s {@link TabularCatalogProvider} answers out of two of the
 * cluster's own REST endpoints. Mirrors {@code SharepointOnlineCatalog}'s role: package-private,
 * static methods, no state of its own.
 *
 * <h2>One data source is one whole cluster</h2>
 *
 * <p>{@link ElasticRestDataSource} carries only a URL and credentials — no index, no scope. So
 * unlike a connector whose target is pinned on the data source, this catalog genuinely enumerates:
 * {@link #listDatasets} reads {@code GET /_cat/indices} and each open index becomes one dataset.
 *
 * <p>No Elasticsearch client library is used, deliberately. The connector's whole dependency set is
 * {@code json-path} plus {@code inetsoft-tabular-util}, and requests go out through a bare
 * {@link java.net.HttpURLConnection}; that is what lets it work against any server version whose
 * REST API matches, and adding a client to implement the catalog would pin it to one.
 *
 * <h2>Why the mapping is authoritative</h2>
 *
 * <p>{@code GET /{index}/_mapping} returns a schema the user DECLARED when the index was created,
 * not a sample of rows, so {@code columnsMayBeIncomplete} stays false and the connector needs no
 * curated type table for names it could not otherwise classify — the server states each field's
 * type itself.
 *
 * <h2>No relationships</h2>
 *
 * <p>Elasticsearch declares no edges between indexes. Its one intra-document relation, the
 * {@code join} field type, relates a parent and a child document inside a SINGLE index, which this
 * SPI has no way to express and which is not an edge between two datasets. So
 * {@link TabularCatalog#relationships()} is always empty — legal, and the javadoc there says so.
 *
 * <h2>Three rules decide what becomes a column</h2>
 *
 * <p>All three were measured against Elasticsearch 7.9.2, not reasoned about, and each is a case of
 * the same principle: <b>a name that cannot address a real runtime column must not be reported as
 * one, and must still be described.</b> {@code JsonTable.walkRecord}
 * ({@code inetsoft-tabular-util}) recurses into a Map and names each leaf {@code prefix + "." +
 * key}; a List is not a Map, so it falls to the literal branch and the whole array lands in one
 * cell. That behaviour is what "addressable" means below.
 *
 * <ol>
 * <li><b>A {@code text} field's {@code .keyword} subfield is not a column.</b> Multi-fields live
 *     under a {@code fields} key and exist on the index side for sorting and aggregation; they are
 *     not in {@code _source}, so {@code runQuery} never produces a column by that name. The
 *     {@code fields} key is ignored outright.</li>
 * <li><b>A {@code type: "alias"} field is not a column</b> — same reason, measured: a field alias
 *     is resolved at query time and never appears in {@code _source}.</li>
 * <li><b>A field whose {@code _source} value is a JSON object the mapping does not describe is not
 *     a column</b> — see {@link #UNADDRESSABLE_TYPES}. Its runtime column names are its own
 *     sub-keys, dotted, and the mapping does not declare them, so no name derived from the mapping
 *     can reach it. These are named in the dataset description instead.</li>
 * </ol>
 *
 * <p>A field the mapping declares with sub-{@code properties} — a plain object, which carries no
 * {@code type} at all, or an explicit {@code "type": "object"} — IS addressable, because the mapping
 * declares the leaf names {@code walkRecord} will produce. It is flattened with dots.
 *
 * <h2>Arrays are one column, and that is one decision with {@code expanded}</h2>
 *
 * <p>A {@code nested} field is an array in {@code _source}, so it is reported as ONE column holding
 * the raw array, and {@link #describeDataset} declares {@code expanded=false} to match. The column
 * list and the {@code expanded} value are a single decision: with {@code expanded=true} the runtime
 * builds an {@code ExpandedJsonTable} instead, which produces {@code items.sku} / {@code items.qty}
 * columns and changes the grain from one row per document to one row per (document x array
 * element), duplicating every scalar field across those rows so that any total over them is
 * silently multiplied. {@code ExpandedJsonTable.expandMap} additionally starts every array in a row
 * at the same row index, so two arrays in one document line up positionally and invent a
 * relationship that is not there. The substructure still reaches the reader, through the column's
 * own description.
 *
 * <h2>The declared type is reported even where the runtime coarsens it</h2>
 *
 * <p>{@code JsonTable.getTypeClass} maps every JSON number to {@code Double.class}, so a field the
 * mapping calls {@code long} arrives in a worksheet as a Double. The type reported here is still
 * {@code long}: the field-level COMMON extension's {@code "type"} key is documented as "the column's
 * declared type as the source reports it", and the source reports {@code long}. The coarsening
 * changes nothing about dimension-versus-measure or about charts, and is not worked around — seeding
 * per-column types would mean an extra request inside every {@code runQuery} plus parsing the index
 * name back out of a free-form {@code suffix}.
 *
 * <h2>{@code isDimension} is null on every column</h2>
 *
 * <p>{@link TabularColumn#isDimension()} is three-valued and may only be set when the source itself
 * sorts a column into one of two disjoint dimension/measure lists — never inferred from a type. An
 * Elasticsearch mapping declares no such split, so null ("the source did not say") is the true
 * answer for every column. {@code TabularCatalogService.toDataset} separately marks a date-typed
 * column a dimension on wiz's behalf; that is the caller's inference, not a claim about the source.
 *
 * <h2>Index names contain dots, and {@link TabularDatasetRef} says ids must not</h2>
 *
 * <p>Reported verbatim anyway. The standard shipping-agent naming convention is
 * {@code logs-<something>-YYYY.MM.dd}, so a dot in an index name is normal rather than exceptional,
 * and it is not this connector's to rename: the id is echoed back to {@link #describeDataset} and
 * embedded in the {@code suffix} the binding runs, so any rewriting here would produce a dataset
 * that cannot be described or queried.
 *
 * <p>The observed consequence is confined to one misleading warning on the wiz side, and is
 * recorded rather than worked around. wiz reduces a table name to its last dot-separated segment
 * when checking whether two stored documents describe the same table
 * ({@code bareTableName} in {@code wiz-services/src/v1/services/tabularBinding.ts}), so
 * {@code logs-2026.09.04} and {@code metrics-2025.01.04} both reduce to {@code 04} and are reported
 * as duplicates of each other. Nothing branches on that result — it only prepends a sentence to a
 * table summary. Fixing it properly means letting a connector state whether its id is a qualified
 * name or an opaque one, which is a change to the shared contract and to every site that splits on
 * the dot; that is its own round.
 */
final class ElasticCatalog {
   private ElasticCatalog() {
   }

   /**
    * @param ds the cluster to enumerate.
    * @return one dataset per open, non-internal index, in index-name order.
    * @throws Exception if {@code _cat/indices} could not be read or did not answer with a JSON
    *                   array. Never an empty catalog on failure — an empty result would be
    *                   indistinguishable from a cluster that genuinely holds no indexes.
    */
   static TabularCatalog listDatasets(ElasticRestDataSource ds) throws Exception {
      Object parsed = parse(ElasticRestRuntime.getMetadata(ds, CAT_INDICES));

      if(!(parsed instanceof List<?> rows)) {
         throw new Exception("Elasticsearch answered " + CAT_INDICES + " with " +
                             describeJson(parsed) + " instead of a JSON array.");
      }

      List<TabularDatasetRef> datasets = new ArrayList<>();

      for(Object row : rows) {
         Map<?, ?> entry = asMap(row);

         if(entry == null) {
            throw new Exception("Elasticsearch answered " + CAT_INDICES +
                                " with an array entry that is not a JSON object.");
         }

         String index = str(entry.get("index"));

         if(index == null || index.isBlank()) {
            throw new Exception("Elasticsearch answered " + CAT_INDICES +
                                " with an entry carrying no index name.");
         }

         // The cluster's own bookkeeping indexes (.kibana_1, .security-7, ...). _cat/indices does
         // return them -- measured -- and leaving them in would put internal bookkeeping into the
         // annotation list alongside the user's own data.
         if(index.startsWith(".")) {
            LOG.debug("Skipping internal Elasticsearch index {}", index);
            continue;
         }

         // A closed index still answers _mapping, but POST /{index}/_search answers HTTP 400 with
         // an index_closed_exception -- measured. Emitting it would produce a dataset whose own
         // params are guaranteed to fail, so it is dropped and named in the log instead.
         if(!"open".equals(str(entry.get("status")))) {
            LOG.warn("Skipping Elasticsearch index {}: status is '{}', not 'open', so it cannot " +
                     "be queried.", index, str(entry.get("status")));
            continue;
         }

         datasets.add(new TabularDatasetRef(index));
      }

      return new TabularCatalog(datasets, List.of());
   }

   /**
    * @param datasetId a concrete index name, as {@link #listDatasets} returned it.
    * @throws Exception if the index is unknown, resolves to more than one index, or declares no
    *                   addressable fields.
    */
   static TabularDatasetSchema describeDataset(ElasticRestDataSource ds, String datasetId)
      throws Exception
   {
      if(datasetId == null || datasetId.isBlank()) {
         throw new Exception("Elasticsearch was asked to describe a blank dataset id.");
      }

      validateIndexName(datasetId);

      Map<?, ?> byIndex = asMap(parse(ElasticRestRuntime.getMetadata(
         ds, "/" + datasetId + "/_mapping")));

      if(byIndex == null || byIndex.isEmpty()) {
         throw new Exception("Elasticsearch returned no mapping for '" + datasetId + "'.");
      }

      // A wildcard or a multi-index alias answers with one key per concrete index -- measured, an
      // alias spanning two indexes comes back with both. Which one the caller meant is not
      // knowable, and taking the first would silently describe the wrong index, so this fails with
      // the names it did get.
      if(byIndex.size() > 1) {
         throw new Exception("Elasticsearch resolved '" + datasetId + "' to " + byIndex.size() +
            " indexes (" + String.join(", ", byIndex.keySet().stream().map(String::valueOf)
            .sorted().toList()) + "). describeDataset needs exactly one — pass a concrete index " +
            "name rather than an alias or a wildcard.");
      }

      Map<?, ?> mappings = asMap(asMap(byIndex.values().iterator().next()).get("mappings"));

      if(mappings == null) {
         throw new Exception("Elasticsearch returned a mapping for '" + datasetId +
                             "' with no 'mappings' block.");
      }

      Map<?, ?> properties = asMap(mappings.get("properties"));

      if(properties == null || properties.isEmpty()) {
         throw new Exception("Elasticsearch index '" + datasetId + "' declares no fields — an " +
            "index with an empty mapping has nothing to annotate.");
      }

      List<TabularColumn> columns = new ArrayList<>();
      List<String> unaddressable = new ArrayList<>();
      List<String> arrays = new ArrayList<>();
      List<String> unrecognizedTypes = new ArrayList<>();
      collect(properties, null, columns, unaddressable, arrays, unrecognizedTypes);

      if(columns.isEmpty()) {
         throw new Exception("Elasticsearch index '" + datasetId + "' declares " +
            properties.size() + " field(s), but none of them can be addressed as a column: " +
            String.join(", ", unaddressable) + ".");
      }

      // Fully filled, with no empty value. Once the index is known a runnable query is completely
      // determined, so there is nothing left for the caller to choose from the column list. wiz's
      // buildMetadataTableBinding filters queryParams for empty values and, finding none, takes the
      // branch that tells the caller queryParams is exactly this map -- emitting an empty value
      // here would turn that into an instruction to fill something that does not exist.
      Map<String, String> params = new LinkedHashMap<>();
      params.put("suffix", searchSuffix(datasetId));
      params.put("jsonPath", ROW_PATH);
      params.put("expanded", "false");

      // keyColumns empty: a document's _id is its key, and _id is not in _source, so it is not one
      // of the columns above and cannot be named here. columnsMayBeIncomplete false: _mapping is
      // the schema the index declares, not a bounded scan.
      return new TabularDatasetSchema(datasetId, columns, List.of(), params, false,
         datasetDescription(ds, datasetId, mappings, columns, unaddressable, arrays,
                            unrecognizedTypes));
   }

   /**
    * Rejects a {@code datasetId} that could not possibly be a real Elasticsearch index name,
    * before it ever reaches {@link ElasticRestRuntime#getMetadata} or {@link #searchSuffix} and is
    * embedded into a live URL.
    *
    * <p>{@code describeDataset} does not, and cannot, confirm {@code datasetId} came from this
    * data source's own {@link #listDatasets} — {@code TabularCatalogService.describeTable} passes
    * the caller-supplied target straight through, and {@code validateDatasetIdEchoed} only checks
    * self-consistency AFTER the connector already ran. That gap is not this connector's to close:
    * it is the same one {@code SharepointOnlineCatalog}'s own class javadoc argues at length is
    * deliberately accepted SPI-wide, since anyone who can author a query can already point
    * {@code runQuery} at any string through the connector's own unvalidated {@code setSuffix}. What
    * IS this connector's to fix is narrower: a name reaching {@link ElasticRestRuntime#getMetadata}
    * with a character its javadoc claims can't occur, embedded verbatim into a request URL.
    *
    * <p>The rejected characters and shapes below were checked against a live Elasticsearch 7.9.2,
    * not assumed: {@code PUT /<name>} for each one, reading back the server's own
    * {@code invalid_index_name_exception} message. A name containing a legal, non-leading
    * {@code .} — {@code logs-2026.09.04}, this round's own G1 fixture — is deliberately NOT
    * rejected here; only the exact strings {@code .}/{@code ..} and a leading {@code +} are.
    *
    * @throws Exception naming the offending id and which rule it broke.
    */
   private static void validateIndexName(String datasetId) throws Exception {
      String reason = null;

      if(datasetId.getBytes(StandardCharsets.UTF_8).length > 255) {
         reason = "longer than 255 bytes";
      }
      else if(".".equals(datasetId) || "..".equals(datasetId)) {
         reason = "must not be '.' or '..'";
      }
      else if(datasetId.startsWith("+")) {
         reason = "must not start with '+'";
      }
      else {
         for(int i = 0; i < datasetId.length(); i++) {
            char c = datasetId.charAt(i);

            if(FORBIDDEN_NAME_CHARS.indexOf(c) >= 0) {
               reason = "must not contain '" + c + "'";
               break;
            }
         }
      }

      if(reason != null) {
         throw new Exception("Elasticsearch was asked to describe dataset id '" + datasetId +
            "', which is not a legal Elasticsearch index name: " + reason + ".");
      }
   }

   /**
    * Walks one {@code properties} block, appending a column per addressable field and a
    * {@code name (type)} entry to {@code unaddressable} / {@code arrays} / {@code unrecognizedTypes}
    * for the kinds of field that deliberately do not become an ordinary column, or whose type this
    * connector does not recognize. Recurses into a declared object with dotted names, which is what
    * {@code JsonTable.walkRecord} produces for the same document.
    */
   private static void collect(Map<?, ?> properties, String prefix, List<TabularColumn> columns,
                               List<String> unaddressable, List<String> arrays,
                               List<String> unrecognizedTypes)
   {
      for(Map.Entry<?, ?> entry : properties.entrySet()) {
         String name = prefix == null ? String.valueOf(entry.getKey())
            : prefix + "." + entry.getKey();
         Map<?, ?> node = asMap(entry.getValue());

         if(node == null) {
            continue;
         }

         String type = str(node.get("type"));
         Map<?, ?> sub = asMap(node.get("properties"));

         // Resolved at query time, never present in _source -- measured. Same standing as the
         // .keyword subfield the 'fields' key holds, which is never read at all.
         if(ALIAS.equals(type)) {
            LOG.debug("Skipping Elasticsearch field alias {}", name);
            continue;
         }

         if(NESTED.equals(type)) {
            arrays.add(name);
            columns.add(new TabularColumn(name, XSchema.STRING,
                                          description(node, arrayNote(name, sub)), null, null));
            continue;
         }

         if(sub != null) {
            collect(sub, name, columns, unaddressable, arrays, unrecognizedTypes);
            continue;
         }

         if(type == null || UNADDRESSABLE_TYPES.contains(type)) {
            unaddressable.add(name + " (" + (type == null ? "object" : type) + ")");
            continue;
         }

         String xtype = TYPES.get(type);

         if(xtype == null) {
            // Not fatal, and deliberately so: Elasticsearch adds mapping types between versions,
            // and refusing to describe a whole index because one field uses a newer one would
            // break this connector against every future server. It is made loud instead -- a
            // warning in the log, and a sentence in the DATASET description (never the column's
            // own), so it reaches both an operator reading logs and whoever reads the annotated
            // table. It is kept off the column's own description on purpose: a field can carry
            // both a genuine source-declared meta.description AND an unrecognized type at once,
            // and appending connector text there would make TabularColumn.description stop being
            // verbatim, the one column-level place this connector keeps free of ambiguity about
            // source text versus connector text.
            xtype = XSchema.STRING;
            unrecognizedTypes.add(name + " (" + type + ")");
            LOG.warn("Unrecognized Elasticsearch field type '{}' on field '{}'; reporting it as " +
                     "{}.", type, name, XSchema.STRING);
         }

         columns.add(new TabularColumn(name, xtype, description(node, null), null, null));
      }
   }

   /**
    * The column's description: the {@code meta.description} the index itself declares, verbatim,
    * when there is one, plus this connector's own structural note when there is one.
    *
    * <p>Elasticsearch's field-level {@code meta} parameter is a real source-declared channel — it
    * round-trips through {@code _mapping} unchanged, measured — and {@link TabularColumn#description()}
    * asks for exactly that: the source's own words. Only {@code description} is read from it, since
    * that is the one key with a counterpart in this SPI.
    *
    * <p>The connector's own note is appended rather than substituted, and it never invents what a
    * field MEANS. It states only facts this connector knows for certain about its own reporting —
    * that an array is returned as a single value, or that a type name was not recognized — the same
    * boundary {@code FBAdInsightsCatalog}'s dataset description holds to.
    */
   private static String description(Map<?, ?> node, String connectorNote) {
      Map<?, ?> meta = asMap(node.get("meta"));
      String declared = meta == null ? null : str(meta.get("description"));

      if(declared != null && declared.isBlank()) {
         declared = null;
      }

      if(declared == null) {
         return connectorNote;
      }

      return connectorNote == null ? declared : declared + " " + connectorNote;
   }

   private static String arrayNote(String name, Map<?, ?> sub) {
      StringBuilder note = new StringBuilder("Elasticsearch 'nested' field, returned as one raw " +
                                             "JSON array value in a single cell per document");

      if(sub != null && !sub.isEmpty()) {
         List<String> elements = new ArrayList<>();

         for(Map.Entry<?, ?> entry : sub.entrySet()) {
            Map<?, ?> child = asMap(entry.getValue());
            String childType = child == null ? null : str(child.get("type"));
            elements.add(entry.getKey() + " (" +
                         (childType == null ? "object" : childType) + ")");
         }

         note.append("; each element has ").append(String.join(", ", elements));
      }

      note.append(". It is one column because this dataset's binding sets expanded=false; turning " +
                  "array expansion on would flatten it into ").append(name)
         .append(".<sub-field> columns and change the grain from one row per document to one row " +
                 "per (document x array element), duplicating every other column's value across " +
                 "those rows so that any total over them is multiplied.");
      return note.toString();
   }

   /**
    * The dataset-level description, which reaches wiz's annotation LLM as durable table context —
    * {@code applyAnnotationToDoc} never overwrites it.
    *
    * <p>Opens with the index's own mapping-level {@code _meta.description} when it declares one,
    * verbatim. Everything after it is this connector's own, and is confined to facts about the
    * shape of what it reports: the row grain, the document count, the fields deliberately left out
    * of the column list and why, and the page size the binding requests. It never describes what
    * the data means — that is the annotation model's job and the source did not say.
    */
   private static String datasetDescription(ElasticRestDataSource ds, String datasetId,
                                            Map<?, ?> mappings, List<TabularColumn> columns,
                                            List<String> unaddressable, List<String> arrays,
                                            List<String> unrecognizedTypes)
   {
      StringBuilder text = new StringBuilder();
      Map<?, ?> meta = asMap(mappings.get("_meta"));
      String declared = meta == null ? null : str(meta.get("description"));

      if(declared != null && !declared.isBlank()) {
         text.append(declared).append("\n\n");
      }

      text.append("Elasticsearch index '").append(datasetId).append("'");

      Long count = documentCount(ds, datasetId);

      if(count != null) {
         text.append(" (").append(count).append(" documents)");
      }

      text.append(". One row per document, ").append(columns.size())
         .append(" columns read from the index's own _mapping — the schema declared when the ")
         .append("index was created, not a sample, so the column list is complete for the fields ")
         .append("the mapping declares. Any field may hold an array in an individual document; ")
         .append("Elasticsearch does not declare which do, and such a value arrives as one raw ")
         .append("JSON array in its cell.");

      if(!arrays.isEmpty()) {
         text.append("\n\nDeclared array field(s) returned as a single raw JSON value: ")
            .append(String.join(", ", arrays))
            .append(". Each one's element structure is in that column's own description.");
      }

      if(!unaddressable.isEmpty()) {
         text.append("\n\nThe mapping also declares ").append(unaddressable.size())
            .append(" field(s) that are deliberately NOT columns: ")
            .append(String.join(", ", unaddressable))
            .append(". Each holds a JSON object in _source whose sub-keys the mapping does not ")
            .append("declare, so the runtime splits it into dotted leaf columns (a geo_point ")
            .append("written as an object becomes <field>.lat and <field>.lon; a range becomes ")
            .append("<field>.gte and <field>.lte) — and which of the several accepted input forms ")
            .append("a document used is a property of that document, not of the mapping. No ")
            .append("single column name derived from the mapping can address them, so they are ")
            .append("named here instead of reported as columns that would not exist.");
      }

      if(!unrecognizedTypes.isEmpty()) {
         text.append("\n\nThis connector does not recognize the Elasticsearch mapping type ")
            .append("declared for ").append(unrecognizedTypes.size()).append(" field(s), so ")
            .append("each is reported as a string rather than its declared type: ")
            .append(String.join(", ", unrecognizedTypes))
            .append(". This is recorded here rather than on the field's own column, so it never ")
            .append("collides with a verbatim source-declared description on the same field.");
      }

      text.append("\n\nThe binding reads this index with POST ").append(searchSuffix(datasetId))
         .append(" and takes rows from ").append(ROW_PATH).append(". The size= is required: ")
         .append("Elasticsearch returns 10 hits when a search does not ask for more, and nothing ")
         .append("in this connector raises that — TabularQuery's own max-rows setting caps the ")
         .append("table after the fact and is never sent to the server. ").append(SEARCH_SIZE)
         .append(" is the server's own default index.max_result_window, the largest a from+size ")
         .append("search can return at all; a larger value is rejected outright. Lower it in the ")
         .append("query's URL Suffix to read fewer rows, and use the Filter body to select rows ")
         .append("rather than raising it.");

      return text.toString();
   }

   /**
    * The index's searchable document count, or null if it could not be read.
    *
    * <p>Deliberately {@code _count} rather than the {@code docs.count} column {@code _cat/indices}
    * already carries. {@code docs.count} counts Lucene documents, which includes every {@code
    * nested} sub-document: measured on an index holding one document with a two-element nested
    * array, {@code docs.count} reports 3 and {@code _count} reports 1. Only the second is the
    * number of rows a query returns, and this text is read by a model that will reason about row
    * counts.
    *
    * <p>A failure here degrades to omitting the sentence rather than failing the whole call — the
    * column list is the substance of a schema, and losing it over a count would be the worse
    * trade.
    */
   private static Long documentCount(ElasticRestDataSource ds, String datasetId) {
      try {
         Object number = asMap(parse(ElasticRestRuntime.getMetadata(
            ds, "/" + datasetId + "/_count"))).get("count");
         return number instanceof Number n ? n.longValue() : null;
      }
      catch(Exception ex) {
         LOG.warn("Could not read the document count for Elasticsearch index {}", datasetId, ex);
         return null;
      }
   }

   private static String searchSuffix(String datasetId) {
      return "/" + datasetId + "/_search?size=" + SEARCH_SIZE;
   }

   private static Object parse(String json) {
      return JsonPath.using(PARSE_CONFIG).parse(json).json();
   }

   private static Map<?, ?> asMap(Object value) {
      return value instanceof Map<?, ?> map ? map : null;
   }

   private static String str(Object value) {
      return value == null ? null : String.valueOf(value);
   }

   private static String describeJson(Object value) {
      return value == null ? "null" : value.getClass().getSimpleName();
   }

   /**
    * Elasticsearch mapping type to {@link XSchema} constant, for the fields that become columns.
    *
    * <p>Every entry was checked against a live Elasticsearch 7.9.2 or is named in this SPI round's
    * own specification. A type absent from here is NOT silently a string: {@link #collect} reports
    * it as one but warns, so a server version that introduces a type reaches a human.
    */
   private static final Map<String, String> TYPES = Map.ofEntries(
      Map.entry("text", XSchema.STRING),
      Map.entry("keyword", XSchema.STRING),
      Map.entry("constant_keyword", XSchema.STRING),
      Map.entry("wildcard", XSchema.STRING),
      Map.entry("search_as_you_type", XSchema.STRING),
      Map.entry("ip", XSchema.STRING),
      // _source always carries the base64 string the document was indexed with -- the only form
      // Elasticsearch accepts for a binary field -- so unlike the geo types below this one has a
      // single, predictable representation. Measured.
      Map.entry("binary", XSchema.STRING),
      Map.entry("long", XSchema.LONG),
      Map.entry("integer", XSchema.INTEGER),
      Map.entry("short", XSchema.SHORT),
      Map.entry("byte", XSchema.BYTE),
      Map.entry("double", XSchema.DOUBLE),
      Map.entry("scaled_float", XSchema.DOUBLE),
      // A plain JSON number in _source -- measured. Its plural, rank_features, is a map and is in
      // UNADDRESSABLE_TYPES instead.
      Map.entry("rank_feature", XSchema.DOUBLE),
      Map.entry("float", XSchema.FLOAT),
      Map.entry("half_float", XSchema.FLOAT),
      Map.entry("boolean", XSchema.BOOLEAN),
      Map.entry("date", XSchema.TIME_INSTANT),
      Map.entry("date_nanos", XSchema.TIME_INSTANT));

   /**
    * Mapping types whose {@code _source} value is a JSON object whose sub-keys the mapping does not
    * declare. A field of one of these types is described, never reported as a column — see this
    * class's javadoc for the rule and the dataset description for where they surface.
    *
    * <p>{@code geo_point} is the clearest case and was measured both ways: written as
    * {@code {"lat":42.35,"lon":-71.05}} it becomes two runtime columns {@code <field>.lat} and
    * {@code <field>.lon}, and written as {@code "42.35,-71.05"} it becomes one column
    * {@code <field>}. Elasticsearch accepts both, plus a geohash string and a {@code [lon, lat]}
    * array, and {@code _source} preserves whichever the document used. The mapping says only
    * {@code geo_point}, so the runtime column shape is a property of each document rather than of
    * the schema, and no name taken from the mapping addresses it reliably. {@code geo_shape},
    * {@code point} and {@code shape} carry the same object-or-WKT-string choice; {@code flattened},
    * {@code histogram}, the {@code *_range} family, {@code percolator} and {@code rank_features}
    * are always objects; {@code join} is a string or an object depending on whether the document is
    * a parent or a child; and {@code completion} is a string or an
    * {@code {"input": [...], "weight": n}} object.
    *
    * <p>{@code object} is here for the case where a mapping declares the type but no
    * sub-{@code properties} — an object with {@code enabled: false}, say. An object that DOES
    * declare its properties never reaches this set: {@link #collect} recurses into it, because
    * there the mapping does name the leaves the runtime will produce.
    */
   private static final Set<String> UNADDRESSABLE_TYPES = Set.of(
      "object", "geo_point", "geo_shape", "point", "shape", "flattened", "histogram",
      "percolator", "join", "completion", "rank_features",
      "integer_range", "long_range", "float_range", "double_range", "date_range", "ip_range");

   private static final String ALIAS = "alias";
   private static final String NESTED = "nested";

   /**
    * Every character Elasticsearch 7.9.2 refuses in an index name, measured with
    * {@code PUT /<name>} against a live container: {@code \ / * ? " < > | , #} and a space. Used
    * by {@link #validateIndexName} — kept as one literal string rather than a
    * {@code Set<Character>} since every use is a single {@code indexOf} scan.
    */
   private static final String FORBIDDEN_NAME_CHARS = "\\/*?\"<>|,# ";

   /**
    * Sorted by index name so two calls against an unchanged cluster produce the same order —
    * {@code _cat/indices} follows cluster-state iteration otherwise, which is not stable. Only the
    * two columns this class reads are requested.
    */
   private static final String CAT_INDICES = "/_cat/indices?format=json&h=index,status&s=index";

   /**
    * The page size the binding's search asks for. Not arbitrary: it is Elasticsearch's own default
    * {@code index.max_result_window}, so it is the largest a {@code from}+{@code size} search can
    * return — 10001 is rejected with an illegal_argument_exception. Measured.
    */
   private static final int SEARCH_SIZE = 10000;

   private static final String ROW_PATH = "$.hits.hits[*]._source";

   /**
    * The same provider {@link ElasticRestRuntime} already parses query results with, so the catalog
    * adds no dependency of its own. It yields {@code LinkedHashMap}s, which is what preserves the
    * server's own field order through {@link #collect}.
    */
   private static final Configuration PARSE_CONFIG =
      Configuration.builder().jsonProvider(new JacksonJsonProvider()).build();

   private static final Logger LOG = LoggerFactory.getLogger(ElasticCatalog.class.getName());
}
