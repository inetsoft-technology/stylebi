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
package inetsoft.web.wiz.controller;

import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The harness of the logical model bridge tests: the REAL data source registry and the REAL
 * {@code LogicalModelService}, with the permission checks, the metadata provider, the dependency
 * handlers and the tree service mocked (the pattern of {@code DataModelUnknownsTest}). A model
 * written through the bridge therefore goes through the production service code and is read back
 * from the registry, so a test fact is a fact about StyleBI, not about a fake.
 *
 * <p>The data source {@code ds} has a physical view {@code pv} over table {@code T1}
 * (columns {@code ID}, {@code AMOUNT}, {@code NAME}), a second view {@code pv2} over table
 * {@code T2}, and a data model folder {@code F}.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizLogicalModelTestBase.DependencyStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
abstract class WizLogicalModelTestBase {
   /** {@code DependencyTransformer.getDependencies} reaches this bean through a static lookup. */
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }

      /** Renaming a model reaches these through their static lookups. */
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      @Bean
      public DependencyHandler dependencyHandler() {
         return mock(DependencyHandler.class);
      }
   }

   private static int count = 0;
   protected final Principal bob = () -> "bob";
   @org.springframework.beans.factory.annotation.Autowired
   protected DependencyStorageService dependencyStorage;
   protected String ds;
   protected XDataModel dataModel;
   protected XRepository repository;
   protected DataSourceService dataSourceService;
   protected AssetRepository assetRepository;
   @org.springframework.beans.factory.annotation.Autowired
   protected DependencyHandler dependencyHandler;
   @org.springframework.beans.factory.annotation.Autowired
   protected RenameTransformHandler transformHandler;
   protected LogicalModelTreeService treeService;
   protected AssetDataCache assetDataCache;
   protected LogicalModelService modelService;
   protected DefaultMetaDataProvider metaProvider;
   protected JDBCDataSource jdbcSource;
   protected WizLogicalModelController controller;
   /** The physical metadata the mocked provider reports: table to column to data type. */
   protected Map<String, Map<String, String>> physical;

   @BeforeEach
   void setUpHarness() throws Exception {
      reset(dependencyStorage);
      DataSourceRegistry.getRegistry().init();
      ds = "wizlm_" + (++count);
      dataModel = new XDataModel(ds);
      XPartition view = new XPartition("pv");
      view.addTable("T1", null, null);
      dataModel.addPartition(view);
      XPartition view3 = new XPartition("pv3");
      view3.addTable("T1", null, null);
      dataModel.addPartition(view3);
      XPartition view2 = new XPartition("pv2");
      view2.addTable("T2", null, null);
      dataModel.addPartition(view2);
      dataModel.addFolder("F");
      DataSourceRegistry.getRegistry().clearCache();

      physical = new LinkedHashMap<>();
      physical.put("T1", new LinkedHashMap<>(Map.of("ID", "integer")));
      physical.get("T1").put("AMOUNT", "double");
      physical.get("T1").put("NAME", "string");
      physical.put("T2", new LinkedHashMap<>(Map.of("CODE", "string")));

      jdbcSource = mock(JDBCDataSource.class);
      when(jdbcSource.getDataSource("conn")).thenReturn(mock(JDBCDataSource.class));
      repository = mock(XRepository.class);
      when(repository.getDataModel(anyString())).thenAnswer(i -> dataModel);
      when(repository.getDataSource(anyString())).thenReturn(jdbcSource);

      metaProvider = mock(DefaultMetaDataProvider.class);
      when(metaProvider.getMetaData(any(XPartition.class), anyBoolean(), any()))
         .thenAnswer(i -> metadataNode());
      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class),
                                             any(Principal.class))).thenReturn(true);
      when(dataSourceService.checkPermission(any(), any(), any(), any())).thenReturn(true);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      when(dataSourceService.getDataSource(anyString())).thenReturn(jdbcSource);
      when(dataSourceService.getDataSource(anyString(), any())).thenReturn(jdbcSource);
      when(dataSourceService.getDefaultMetaDataProvider(any(), any())).thenReturn(metaProvider);
      assetRepository = mock(AssetRepository.class);
      reset(dependencyHandler, transformHandler);
      treeService = mock(LogicalModelTreeService.class);
      assetDataCache = mock(AssetDataCache.class);
      modelService = new LogicalModelService(
         mock(SecurityEngine.class), repository, dataSourceService, null, treeService,
         assetRepository, dependencyHandler, transformHandler);
      controller = newController(modelService);
   }

   protected WizLogicalModelController newController(LogicalModelService service) {
      return new WizLogicalModelController(repository, dataSourceService, service, treeService,
                                           assetDataCache);
   }

   /** Records an outer dependency on an element (or the model) and stores the model again. */
   protected void seedOuter(String model, String entity, String attribute, Object dependent) {
      XLogicalModel stored = dataModel.getLogicalModel(model);

      if(attribute != null) {
         stored.getEntity(entity).getAttribute(attribute).addOuterDependency(dependent);
      }
      else {
         stored.addOuterDependency(dependent);
      }

      dataModel.addLogicalModel(stored);
      DataSourceRegistry.getRegistry().clearCache();
   }

   /** A fresh view of the registry, as another request would see it. */
   protected XDataModel fresh() {
      DataSourceRegistry.getRegistry().clearCache();
      return dataModel;
   }

   private XNode metadataNode() {
      XNode root = new XNode("root");

      for(Map.Entry<String, Map<String, String>> table : physical.entrySet()) {
         XNode tableNode = new XNode(table.getKey());

         for(Map.Entry<String, String> column : table.getValue().entrySet()) {
            XNode columnNode = new XNode(column.getKey());
            columnNode.setValue(column.getValue());
            tableNode.addChild(columnNode);
         }

         root.addChild(tableNode);
      }

      return root;
   }

   // -- definition builders ------------------------------------------------------------------

   protected static Definition def(String name, Entity... entities) {
      return new Definition(name, null, "pv", null, null, null, null,
                            new ArrayList<>(List.of(entities)));
   }

   protected static Definition inFolder(Definition d, String folder) {
      return new Definition(d.name(), d.description(), d.view(), folder, d.connection(),
                            d.parent(), d.extended(), d.entities());
   }

   protected static Definition withView(Definition d, String view) {
      return new Definition(d.name(), d.description(), view, d.folder(), d.connection(),
                            d.parent(), d.extended(), d.entities());
   }

   protected static Entity entity(String name, Attribute... attributes) {
      return entity(name, null, attributes);
   }

   protected static Entity entity(String name, String oldName, Attribute... attributes) {
      return new Entity(name, oldName, null, null, null, null,
                        new ArrayList<>(List.of(attributes)));
   }

   /** A column attribute with no marker (absent oldName). */
   protected static Attribute col(String name, String table, String column) {
      return col(name, null, table, column);
   }

   protected static Attribute col(String name, String oldName, String table, String column) {
      return new Attribute(name, oldName, null, null, null, table, column, null, null, null,
                           null, null, null, null, null, null, null, null, null);
   }

   protected static Attribute exp(String name, String expression, String dataType) {
      return new Attribute(name, null, null, null, null, null, null, expression, null, null,
                           dataType, null, null, null, null, null, null, null, null);
   }

   protected static Attribute withDescription(Attribute a, String description) {
      return new Attribute(a.name(), a.oldName(), description, a.visible(), a.type(), a.table(),
                           a.column(), a.expression(), a.aggregate(), a.parseable(),
                           a.dataType(), a.refType(), a.formula(), a.browseData(),
                           a.browseQuery(), a.format(), a.drill(), a.baseElement(),
                           a.errorMessage());
   }

   /** Creates a model through the controller and returns the read-back. */
   protected Definition create(Definition definition) throws Exception {
      Object result = controller.create(new CreateRequest(ds, definition, null), bob);
      return ((CreateResponse) result).definition();
   }

   protected Object update(String name, Definition definition, Boolean dryRun) throws Exception {
      return controller.update(new UpdateRequest(ds, name, definition, dryRun), bob);
   }

   protected GetResponse get(String name) throws Exception {
      return (GetResponse) controller.get(new GetRequest(ds, name, null), bob);
   }

   /** The problem codes of an {@code INVALID} failure. */
   protected static List<String> codes(Object failure) {
      return ((WizDataModelWire.Failure) failure).errors().stream()
         .map(WizDataModelWire.Err::code).toList();
   }
}
