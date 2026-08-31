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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

      return toTablesResponse(dsName, catalog);
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

      return toDataset(dsName, xrepository.getDataSource(dsName).getType(), schema, objectMapper);
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
