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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import inetsoft.web.wiz.model.DatabaseTableInfo;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.model.osi.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serves {@code GET /datasource/tables} and {@code POST /datasource/table/meta} for tabular data
 * sources that are not JDBC (e.g. OData) but implement {@link TabularCatalogProvider} on their
 * {@link TabularRuntime}.
 *
 * This class deliberately knows nothing about any specific connector. Its only inputs are the
 * neutral {@code Tabular*} records and {@link XDataSource#getType()} (an opaque string it copies
 * through, never branches on). If a future change needs a {@code switch}/{@code if} on data
 * source type here, that is the signal that the change belongs in a connector instead.
 */
@Service
public class TabularCatalogService {
   @Autowired
   public TabularCatalogService(XRepository xrepository, ObjectMapper objectMapper) {
      this(xrepository, objectMapper, TabularUtil::createRuntime);
   }

   // Test seam: lets a fake connector be supplied without Config, a plugin, or Spring.
   TabularCatalogService(XRepository xrepository, ObjectMapper objectMapper,
                         Function<String, TabularRuntime> runtimeResolver)
   {
      this.xrepository = xrepository;
      this.objectMapper = objectMapper;
      this.runtimeResolver = runtimeResolver;
   }

   /**
    * The non-JDBC counterpart of the JDBC {@code getDatabaseTables} path.
    */
   public DatasourceTablesResponse listTables(String dsName) throws Exception {
      TabularCatalogProvider provider = resolveProvider(dsName);
      TabularDataSource<?> tds = resolveTabularDataSource(dsName);

      TabularCatalog catalog = provider.listDatasets(tds);

      if(catalog == null || catalog.datasets() == null || catalog.datasets().isEmpty()) {
         throw new Exception("Data source '" + dsName + "' reported no datasets to annotate.");
      }
      if(catalog.relationships() == null) {
         throw new Exception("Data source '" + dsName + "' returned a catalog with a null " +
            "relationships list; TabularCatalogProvider implementations must return an empty " +
            "list, not null, when there are no relationships.");
      }

      validateDatasetIds(dsName, catalog.datasets());
      validateRelationshipEndpoints(dsName, catalog);

      return toTablesResponse(dsName, catalog);
   }

   private static void validateDatasetIds(String dsName, List<TabularDatasetRef> datasets)
      throws Exception
   {
      Set<String> seen = new HashSet<>();

      for(TabularDatasetRef ref : datasets) {
         if(ref == null || ref.id() == null || ref.id().isBlank()) {
            throw new Exception("Data source '" + dsName + "' returned a dataset with a blank id.");
         }
         if(ref.id().contains(".")) {
            // TabularDatasetRef.id's javadoc: "Must not contain a '.' character" — wiz's own
            // bareTableName/sourceMatches split a non-FILE source on '.', so a dotted id would
            // silently collide two different datasets, or resolve to the wrong one, downstream.
            throw new Exception("Data source '" + dsName + "' returned a dataset id '" + ref.id() +
               "' containing '.', which TabularDatasetRef.id's contract forbids.");
         }
         if(!seen.add(ref.id())) {
            // TabularDatasetRef.id's javadoc: "must be non-blank and unique within one catalog."
            // A duplicate becomes two DatabaseTableInfo rows with an identical table field, and a
            // later describeTable(dsName, thatId) has no way to know which one was meant.
            throw new Exception("Data source '" + dsName + "' returned the dataset id '" +
               ref.id() + "' more than once; every dataset id must be unique within one catalog.");
         }
      }
   }

   private static void validateRelationshipEndpoints(String dsName, TabularCatalog catalog)
      throws Exception
   {
      Set<String> ids = catalog.datasets().stream().map(TabularDatasetRef::id)
         .collect(Collectors.toSet());
      Set<String> seenNames = new HashSet<>();

      for(TabularRelationship rel : catalog.relationships()) {
         if(rel == null) {
            throw new Exception("Data source '" + dsName + "' returned a null relationship entry.");
         }
         if(rel.name() == null || rel.name().isBlank()) {
            throw new Exception("Data source '" + dsName + "' declared a relationship with a " +
               "blank name.");
         }
         if(!seenNames.add(rel.name())) {
            // TabularRelationship.name's javadoc: "stable identifier for this edge within the
            // catalog" — a duplicate is the same "which one did the connector mean" ambiguity
            // the dataset-id uniqueness check above exists to close.
            throw new Exception("Data source '" + dsName + "' declared the relationship name '" +
               rel.name() + "' more than once; every relationship name must be unique within one " +
               "catalog.");
         }
         if(!ids.contains(rel.fromDataset()) || !ids.contains(rel.toDataset())) {
            throw new Exception("Data source '" + dsName + "' declared relationship '" + rel.name() +
               "' referencing an unknown dataset ('" + rel.fromDataset() + "' -> '" +
               rel.toDataset() + "'); every relationship endpoint must be one of the datasets " +
               "returned by listDatasets.");
         }
         if(rel.fromColumns() == null || rel.fromColumns().isEmpty() ||
            rel.toColumns() == null || rel.toColumns().isEmpty())
         {
            throw new Exception("Data source '" + dsName + "' declared relationship '" +
               rel.name() + "' with an empty fromColumns/toColumns list; both must be non-empty " +
               "and positionally paired.");
         }
         if(rel.fromColumns().size() != rel.toColumns().size()) {
            throw new Exception("Data source '" + dsName + "' declared relationship '" +
               rel.name() + "' with fromColumns/toColumns of different sizes (" +
               rel.fromColumns().size() + " vs " + rel.toColumns().size() +
               "); they must be positionally paired.");
         }
      }
   }

   /**
    * The non-JDBC counterpart of the JDBC {@code getMetaData} path.
    */
   public OsiDataset describeTable(String dsName, String target) throws Exception {
      TabularCatalogProvider provider = resolveProvider(dsName);
      TabularDataSource<?> tds = resolveTabularDataSource(dsName);

      TabularDatasetSchema schema = provider.describeDataset(tds, target);

      if(schema == null || schema.columns() == null || schema.columns().isEmpty()) {
         throw new Exception("Data source '" + dsName + "' target '" + target +
            "' returned no columns — cannot annotate.");
      }
      validateDatasetIdEchoed(dsName, target, schema);
      validateColumnNames(dsName, target, schema.columns());
      validateKeyColumns(dsName, target, schema);

      return toDataset(dsName, xrepository.getDataSource(dsName).getType(), schema, objectMapper);
   }

   private static void validateDatasetIdEchoed(String dsName, String target,
                                               TabularDatasetSchema schema) throws Exception
   {
      if(!target.equals(schema.datasetId())) {
         // TabularDatasetSchema.datasetId's javadoc: "echoes the id that was asked for, so a
         // result is self-identifying." toDataset() below uses schema.datasetId(), not target, to
         // set OsiDataset.name/.source — a connector that answers with a different dataset's
         // schema would silently corrupt which dataset the annotation ends up labeled as.
         throw new Exception("Data source '" + dsName + "' was asked to describe target '" +
            target + "' but returned a schema for '" + schema.datasetId() + "' instead.");
      }
   }

   private static void validateColumnNames(String dsName, String target, List<TabularColumn> columns)
      throws Exception
   {
      for(TabularColumn column : columns) {
         if(column == null || column.name() == null || column.name().isBlank()) {
            throw new Exception("Data source '" + dsName + "' target '" + target +
               "' returned a column with a blank name.");
         }
         if(!XSCHEMA_TYPE_CONSTANTS.contains(column.type())) {
            // TabularColumn.type's javadoc: "any XSchema type constant" — a closed vocabulary of
            // 21, not the handful the javadoc names as examples. This catches input
            // that never used the vocabulary at all ("varchar", a typo, null) — it cannot and does
            // not catch a semantically wrong but valid choice (a date column labeled STRING); no
            // whitelist can. Deliberately every declared constant, not a curated subset: a
            // narrower, hand-picked set would risk rejecting a legitimate future connector mapping
            // to e.g. XSchema.INTEGER or DECIMAL, which is the same "validator breaks a correct
            // connector" failure this check exists to avoid causing.
            throw new Exception("Data source '" + dsName + "' target '" + target +
               "' returned column '" + column.name() + "' with type '" + column.type() +
               "', which is not an XSchema type constant.");
         }
      }
   }

   // Every XSchema type constant, derived directly from XSchema's own declarations rather than
   // XSchema.isPrimitiveType (checked first, per review guidance: it doesn't fit — it excludes
   // NULL/COLOR/UNKNOWN and separately includes the non-constant legacy alias "bigdecimal" plus
   // the UI/role constants ENUM/USER_DEFINED/USER/ROLE, so its shape answers a different question
   // than "is this any declared XSchema constant").
   private static final Set<String> XSCHEMA_TYPE_CONSTANTS = Set.of(
      XSchema.NULL, XSchema.STRING, XSchema.BOOLEAN, XSchema.FLOAT, XSchema.DOUBLE,
      XSchema.DECIMAL, XSchema.CHAR, XSchema.CHARACTER, XSchema.BYTE, XSchema.SHORT,
      XSchema.INTEGER, XSchema.LONG, XSchema.TIME_INSTANT, XSchema.DATE, XSchema.TIME,
      XSchema.ENUM, XSchema.USER_DEFINED, XSchema.ROLE, XSchema.USER, XSchema.COLOR,
      XSchema.UNKNOWN);

   private static void validateKeyColumns(String dsName, String target, TabularDatasetSchema schema)
      throws Exception
   {
      if(schema.keyColumns() == null) {
         // Stated residual: TabularDatasetSchema.keyColumns is documented "Never null", but a null
         // here is tolerated rather than rejected. Unlike TabularCatalog.relationships() — also
         // documented "Never null", and enforced above (C1), because a null there is a real NPE in
         // toTablesResponse's for-each — a null keyColumns is fully absorbed by toDataset below:
         // `schema.keyColumns() == null || schema.keyColumns().isEmpty() ? null : ...` produces an
         // identical OsiDataset.primaryKey either way. Rejecting it would only punish a connector
         // that passed null instead of List.of() to no observable difference.
         return;
      }

      Set<String> columnNames = schema.columns().stream().map(TabularColumn::name)
         .collect(Collectors.toSet());

      for(String keyColumn : schema.keyColumns()) {
         if(!columnNames.contains(keyColumn)) {
            // TabularDatasetSchema.keyColumns' javadoc: "names, from columns, that the source
            // declares as this dataset's key." A dangling name mislabels the reported primary key
            // rather than causing an ambiguous lookup, but it is still a documented invariant this
            // validation layer should not silently trust.
            throw new Exception("Data source '" + dsName + "' target '" + target +
               "' declared key column '" + keyColumn + "', which is not one of its own reported " +
               "columns.");
         }
      }
   }

   /**
    * Resolves the data source's {@link TabularCatalogProvider}, or throws
    * {@link UnsupportedDatasourceException} for every way it can fail to be one: the source
    * doesn't exist, isn't tabular, or its runtime doesn't implement the SPI.
    */
   private TabularCatalogProvider resolveProvider(String dsName) throws Exception {
      XDataSource ds = xrepository.getDataSource(dsName);

      if(ds == null) {
         throw new Exception("Data source " + dsName + " not found.");
      }

      TabularRuntime runtime = runtimeResolver.apply(dsName);

      if(!(runtime instanceof TabularCatalogProvider provider)) {
         throw new UnsupportedDatasourceException(dsName, ds.getType());
      }

      return provider;
   }

   private TabularDataSource<?> resolveTabularDataSource(String dsName) throws Exception {
      XDataSource ds = xrepository.getDataSource(dsName);

      if(!(ds instanceof TabularDataSource<?> tds)) {
         throw new UnsupportedDatasourceException(dsName, ds == null ? null : ds.getType());
      }

      return tds;
   }

   static DatasourceTablesResponse toTablesResponse(String dsName, TabularCatalog catalog) {
      List<DatabaseTableInfo> tables = new ArrayList<>();

      for(TabularDatasetRef ref : catalog.datasets()) {
         DatabaseTableInfo info = new DatabaseTableInfo();
         info.setDatabase(dsName);
         info.setTable(ref.id());
         tables.add(info);
      }

      List<OsiRelationship> relationships = new ArrayList<>();

      for(TabularRelationship rel : catalog.relationships()) {
         OsiRelationship osiRelationship = new OsiRelationship();
         osiRelationship.setName(rel.name());
         osiRelationship.setFrom(rel.fromDataset());
         osiRelationship.setTo(rel.toDataset());
         osiRelationship.setFromColumns(rel.fromColumns());
         osiRelationship.setToColumns(rel.toColumns());
         relationships.add(osiRelationship);
      }

      DatasourceTablesResponse response = new DatasourceTablesResponse();
      response.setTables(tables);
      response.setRelationships(relationships);
      return response;
   }

   static OsiDataset toDataset(String dsName, String datasourceSubtype,
                               TabularDatasetSchema schema, ObjectMapper objectMapper)
   {
      List<OsiField> fields = new ArrayList<>();

      for(TabularColumn column : schema.columns()) {
         OsiField field = new OsiField();
         field.setName(column.name());

         if(XSchema.isDateType(column.type())) {
            field.setDimension(new OsiDimension(true));
         }

         field.setCustomExtensions(List.of(buildFieldExtension(column.type(), objectMapper)));
         fields.add(field);
      }

      OsiDataset dataset = new OsiDataset();
      dataset.setName(schema.datasetId());
      dataset.setSource(schema.datasetId());
      dataset.setPrimaryKey(schema.keyColumns() == null || schema.keyColumns().isEmpty() ?
         null : schema.keyColumns());
      dataset.setFields(fields);
      dataset.setCustomExtensions(
         List.of(buildDatasetExtension(dsName, datasourceSubtype, objectMapper)));
      return dataset;
   }

   private static OsiCustomExtension buildFieldExtension(String type, ObjectMapper objectMapper) {
      try {
         Map<String, Object> extData = new LinkedHashMap<>();
         extData.put("type", type);

         OsiCustomExtension ext = new OsiCustomExtension();
         ext.setVendorName("COMMON");
         ext.setData(objectMapper.writeValueAsString(extData));
         return ext;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to serialize field custom extension", e);
      }
   }

   /**
    * The dataset COMMON extension that tells wiz (and every StyleBI reader of
    * {@code datasourceType}) that this is not a SQL table — see charter assertion B7. The literal
    * {@code "tabular"} must match wiz's {@code TABULAR_DATASOURCE_TYPE} exactly; there is no shared
    * constant between the two repositories.
    */
   private static OsiCustomExtension buildDatasetExtension(String dsName, String datasourceSubtype,
                                                            ObjectMapper objectMapper)
   {
      try {
         Map<String, Object> extData = new LinkedHashMap<>();
         extData.put("dsName", dsName);
         extData.put("path", dsName);
         extData.put("datasourceType", "tabular");
         extData.put("datasourceSubtype", datasourceSubtype);

         OsiCustomExtension ext = new OsiCustomExtension();
         ext.setVendorName("COMMON");
         ext.setData(objectMapper.writeValueAsString(extData));
         return ext;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to serialize dataset custom extension", e);
      }
   }

   private final XRepository xrepository;
   private final ObjectMapper objectMapper;
   private final Function<String, TabularRuntime> runtimeResolver;
}
