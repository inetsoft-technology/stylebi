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
package inetsoft.uql.rest.datasource.airtable;

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Turns Airtable Meta API JSON into the neutral {@link TabularCatalogProvider} types. This is the
 * only place that reads the Meta API's base/table/field shape; {@link AirtableMetaClient} does the
 * I/O.
 *
 * <h2>Scope: every base the token can see</h2>
 * An Airtable data source holds a token, not a base -- the base is a parameter of each query
 * ({@code Base ID}), and {@code Test Base ID} on the data source is only a connection-test input.
 * So the catalog is the tables of all bases the token is allowed to see. A base listed with
 * permission level {@code none} is skipped: the token cannot read its schema, and one such base
 * must not abort the catalog of all the others. Every other per-base failure aborts the call
 * naming the base, because a catalog that silently misses a base looks complete.
 *
 * <h2>Dataset id: {@code <Base ID>/<Table Name>}</h2>
 * It mirrors the two path parameters of the {@code Records} endpoint ({@code /v0/{Base ID}/{Table
 * Name}}), so a listed id maps straight onto {@code add_table}'s {@code Base ID} and {@code Table
 * Name}. A table name may hold anything, including {@code /}, so the id is not escaped; instead the
 * left half is guaranteed separator-free (a base id is letters and digits only -- {@link #BASE_ID}
 * is checked on every id emitted and every id decoded) and the id is split on the FIRST {@code /}.
 * That is injective over (base, table): two different pairs cannot give the same id because the
 * base id can never absorb the separator. Table names are unique within a base, so ids are unique.
 *
 * <h2>Cost</h2>
 * A listing is 1 request per 100 bases plus 1 per base (each returns that base's full schema,
 * the only form the Meta API offers). A describe is 1 request, and reuses a schema fetched for the
 * same base in the last 30 seconds. 429 and 502/503/504 are retried up to 3 times (honoring
 * Retry-After, capped at 30 s); everything else fails at once.
 *
 * <h2>Columns</h2>
 * Named as a {@code Records} query names them (checked against a real query run over a stubbed
 * response, see the test): {@code records.id}, {@code records.createdTime}, then {@code
 * records.fields.<field name>} per Airtable field, in the order Airtable reports them.
 *
 * <h2>Type mapping</h2>
 * Explicit, keyed on the field type names Airtable documents ({@link #KNOWN_FIELD_TYPES} pins the
 * count). A formula or rollup is mapped through its declared {@code options.result.type}. A type
 * outside the list takes the named default {@link #UNKNOWN_FIELD_DEFAULT} with a WARN -- the type
 * namespace is open on Airtable's side, and failing the whole catalog because one field of one
 * table is newer than this code would take down every other table too.
 *
 * <h2>Not done</h2>
 * No relationships: a linked-record field holds record ids, not values of a declared key column,
 * so an edge would have to guess its key columns. {@code keyColumns} is empty for the same
 * reason -- the primary field is a display field, not a unique key.
 */
final class AirtableCatalog {
   private AirtableCatalog() {
   }

   static TabularCatalog listDatasets(AirtableMetaClient client) throws Exception {
      List<TabularDatasetRef> datasets = new ArrayList<>();

      for(JsonNode base : client.listBases()) {
         String baseId = requireBaseId(text(base, "id"));

         if("none".equals(text(base, "permissionLevel"))) {
            LOG.info("Skipping Airtable base {}: the token has no access to it", baseId);
            continue;
         }

         for(JsonNode table : client.listTables(baseId)) {
            datasets.add(new TabularDatasetRef(datasetId(baseId, requireTableName(table, baseId))));
         }
      }

      return new TabularCatalog(List.copyOf(datasets), List.of());
   }

   static TabularDatasetSchema describeDataset(AirtableMetaClient client, String datasetId)
      throws Exception
   {
      int slash = datasetId == null ? -1 : datasetId.indexOf(SEPARATOR);

      if(slash < 0) {
         throw new IllegalArgumentException("'" + datasetId + "' is not an Airtable dataset id; " +
            "expected '<Base ID>/<Table Name>', for example 'appAbc123/Customers'.");
      }

      String baseId = requireBaseId(datasetId.substring(0, slash));
      String tableName = datasetId.substring(slash + 1);
      JsonNode table = null;
      List<String> names = new ArrayList<>();

      for(JsonNode candidate : client.listTables(baseId, true)) {
         String name = requireTableName(candidate, baseId);
         names.add(name);

         if(name.equals(tableName)) {
            table = candidate;
         }
      }

      if(table == null) {
         throw new IllegalArgumentException("Airtable base " + baseId + " has no table named '" +
            tableName + "'. Tables: " +
            String.join(", ", names.subList(0, Math.min(20, names.size()))) +
            (names.size() > 20 ? ", ..." : "") + ".");
      }

      List<TabularColumn> columns = new ArrayList<>();
      columns.add(new TabularColumn(ID_COLUMN, XSchema.STRING));
      columns.add(new TabularColumn(CREATED_TIME_COLUMN, XSchema.TIME_INSTANT));
      int fixedColumns = columns.size();

      for(JsonNode field : table.path("fields")) {
         String name = text(field, "name");

         if(name == null) {
            throw new IllegalStateException("Table '" + tableName + "' of Airtable base " + baseId +
               " has a field with no name.");
         }

         columns.add(new TabularColumn(FIELD_COLUMN_PREFIX + name, mapFieldType(tableName, field),
                                       text(field, "description"), null, null));
      }

      if(columns.size() == fixedColumns) {
         throw new IllegalStateException("Table '" + tableName + "' of Airtable base " + baseId +
            " reports no fields -- cannot annotate.");
      }

      String own = text(table, "description");
      String note = "Airtable base " + baseId + ", table '" + tableName + "'. Query it with " +
         "endpoint 'Records' and parameters Base ID = " + baseId + ", Table Name = " + tableName +
         ".";

      return new TabularDatasetSchema(datasetId, List.copyOf(columns), List.of(), Map.of(), false,
                                      own == null ? note : own + "\n\n" + note);
   }

   static String datasetId(String baseId, String tableName) {
      return baseId + SEPARATOR + tableName;
   }

   private static String requireBaseId(String baseId) {
      if(baseId == null || !BASE_ID.matcher(baseId).matches()) {
         throw new IllegalArgumentException("'" + baseId + "' is not an Airtable base id " +
            "(expected letters and digits only, like 'appAbc123').");
      }

      return baseId;
   }

   private static String requireTableName(JsonNode table, String baseId) {
      String name = text(table, "name");

      if(name == null) {
         throw new IllegalStateException(
            "Airtable base " + baseId + " reports a table with no name.");
      }

      return name;
   }

   // ===================================================================================
   // Type mapping
   // ===================================================================================

   static final String UNKNOWN_FIELD_DEFAULT = XSchema.STRING;

   /** Every field type name {@link #mapFieldType} maps explicitly. Pinned by a canary test. */
   static final Set<String> KNOWN_FIELD_TYPES = Set.of(
      "singleLineText", "email", "url", "multilineText", "phoneNumber", "richText", "singleSelect",
      "manualSort", "number", "percent", "currency", "duration", "count", "autoNumber", "rating",
      "checkbox", "date", "dateTime", "createdTime", "lastModifiedTime", "multipleSelects",
      "singleCollaborator", "multipleCollaborators", "createdBy", "lastModifiedBy",
      "multipleRecordLinks", "multipleAttachments", "multipleLookupValues", "barcode", "button",
      "aiText", "externalSyncSource", "formula", "rollup", "lookup");

   static String mapFieldType(String tableName, JsonNode field) {
      String type = text(field, "type");

      if(type == null) {
         LOG.warn("Airtable field '{}' of table '{}' declares no type; defaulting its column " +
            "type to {}", text(field, "name"), tableName, UNKNOWN_FIELD_DEFAULT);
         return UNKNOWN_FIELD_DEFAULT;
      }

      return switch(type) {
         // text on the wire
         case "singleLineText", "email", "url", "multilineText", "phoneNumber", "richText",
              "singleSelect", "manualSort", "externalSyncSource" -> XSchema.STRING;
         case "number", "percent", "currency", "duration" -> XSchema.DOUBLE;
         case "count", "autoNumber", "rating" -> XSchema.INTEGER;
         case "checkbox" -> XSchema.BOOLEAN;
         case "date" -> XSchema.DATE;
         case "dateTime", "createdTime", "lastModifiedTime" -> XSchema.TIME_INSTANT;
         // arrays or objects on the wire: there is no scalar to declare, so they read as text
         case "multipleSelects", "singleCollaborator", "multipleCollaborators", "createdBy",
              "lastModifiedBy", "multipleRecordLinks", "multipleAttachments",
              "multipleLookupValues", "lookup", "barcode", "button", "aiText" -> XSchema.STRING;
         // a computed field is whatever its declared result type is
         case "formula", "rollup" -> mapComputed(tableName, field);
         default -> {
            LOG.warn("Airtable field type '{}' (field '{}' of table '{}') is not one of the {} " +
               "types this connector maps; defaulting its column type to {}", type,
               text(field, "name"), tableName, KNOWN_FIELD_TYPES.size(), UNKNOWN_FIELD_DEFAULT);
            yield UNKNOWN_FIELD_DEFAULT;
         }
      };
   }

   private static String mapComputed(String tableName, JsonNode field) {
      JsonNode result = field.path("options").path("result");

      if(result.isMissingNode() || result.isNull()) {
         // an invalid formula declares no result
         return XSchema.STRING;
      }

      return mapFieldType(tableName, result);
   }

   private static String text(JsonNode node, String key) {
      JsonNode v = node.get(key);
      return v == null || v.isNull() || v.asText().isEmpty() ? null : v.asText();
   }

   // The column names a Records query really produces (probed against a stubbed response): the
   // JSON path of each value, "records" being the response array. Every record has the first two;
   // the rest are the table fields, under their display names.
   static final String ID_COLUMN = "records.id";
   static final String CREATED_TIME_COLUMN = "records.createdTime";
   static final String FIELD_COLUMN_PREFIX = "records.fields.";

   private static final char SEPARATOR = '/';
   private static final Pattern BASE_ID = Pattern.compile("[A-Za-z0-9]+");
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
