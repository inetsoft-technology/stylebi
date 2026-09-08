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
import inetsoft.sree.security.ResourceAction;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.DatabaseTableMeta;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for bug #76500 WSQ-003: {@code getTableDetails} used to return a 200 with
 * {@code columns: []} for a name that is not a real datasource table at all (e.g. a
 * worksheet-internal assembly name), because the only existing not-found guard
 * ({@code tableNode == null}) is structurally dead for this trigger --
 * {@code getTableColumns} always attaches a "Result" child before it knows whether any
 * columns exist. The fix adds an AND-gated check: throw only when the column fetch found
 * nothing <em>and</em> {@link DefaultMetaDataProvider#getTable} also can't find the name in
 * the JDBC catalog listing, so a real table that genuinely has zero columns (e.g. a
 * permission-filtered view) still succeeds as long as {@code getTable} finds it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceGetTableDetailsTest {

   private static final String DS_NAME = "Examples/Orders";

   private DefaultMetaDataProvider metaDataProvider;
   private JDBCDataSource jdbcDataSource;

   private MetadataApiService createService() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      jdbcDataSource = mock(JDBCDataSource.class);
      metaDataProvider = mock(DefaultMetaDataProvider.class);

      when(dataSourceService.checkPermission(eq(DS_NAME), eq(ResourceAction.READ), any()))
         .thenReturn(true);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(jdbcDataSource);
      when(dataSourceService.getDataSource(DS_NAME)).thenReturn(jdbcDataSource);
      when(jdbcDataSource.getFullName()).thenReturn(DS_NAME);
      when(jdbcDataSource.getDatabaseTypeString()).thenReturn("");
      // Short-circuits SQLHelper.getProductName's Config.getConfig() lookup, which needs a
      // Spring bean this test's minimal context doesn't provide.
      when(jdbcDataSource.getRuntimeProductName()).thenReturn("UnitTestDatabase");
      when(dataSourceService.getDataModel(DS_NAME)).thenReturn(mock(XDataModel.class));
      when(dataSourceService.getDefaultMetaDataProvider(eq(jdbcDataSource), any()))
         .thenReturn(metaDataProvider);

      XNode rootMetaData = new XNode();
      when(metaDataProvider.getRootMetaData(anyString())).thenReturn(rootMetaData);

      return new MetadataApiService(
         xrepository, dataSourceService, mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper());
   }

   /** Builds the "Result" node getTableColumns always attaches, with the given column names. */
   private static XNode tableDataWithColumns(String... columnNames) {
      XNode root = new XNode();
      XNode resultNode = new XNode("Result");

      for(String columnName : columnNames) {
         XNode column = new XNode(columnName);
         resultNode.addChild(column);
      }

      root.addChild(resultNode);
      return root;
   }

   @Test
   void realTableWithColumnsAndCatalogMatchSucceeds() throws Exception {
      MetadataApiService service = createService();

      when(metaDataProvider.getMetaData(any(XNode.class), eq(true)))
         .thenReturn(tableDataWithColumns("ORDER_ID", "CUSTOMER_ID"));
      when(metaDataProvider.getTable(any(), any(), eq("Orders"), eq(false)))
         .thenReturn(new XNode("Orders"));

      DatabaseTableMeta meta = service.getTableDetails(
         DS_NAME, "Orders", null, null, mock(Principal.class));

      assertEquals(2, meta.getColumns().size());
   }

   @Test
   void worksheetInternalNameWithNoColumnsAndNoCatalogMatchThrows() throws Exception {
      MetadataApiService service = createService();

      when(metaDataProvider.getMetaData(any(XNode.class), eq(true)))
         .thenReturn(tableDataWithColumns());
      when(metaDataProvider.getTable(any(), any(), eq("StateOrderSummary"), eq(false)))
         .thenReturn(null);

      Exception ex = assertThrows(Exception.class, () -> service.getTableDetails(
         DS_NAME, "StateOrderSummary", null, null, mock(Principal.class)));

      assertTrue(ex.getMessage().contains("StateOrderSummary"),
         "message should name the table that was not found");
      assertTrue(ex.getMessage().contains(DS_NAME),
         "message should name the data source it was not found in");
   }

   @Test
   void realTableWithZeroColumnsButCatalogMatchDoesNotThrow() throws Exception {
      MetadataApiService service = createService();

      when(metaDataProvider.getMetaData(any(XNode.class), eq(true)))
         .thenReturn(tableDataWithColumns());
      when(metaDataProvider.getTable(any(), any(), eq("PermissionFilteredView"), eq(false)))
         .thenReturn(new XNode("PermissionFilteredView"));

      DatabaseTableMeta meta = service.getTableDetails(
         DS_NAME, "PermissionFilteredView", null, null, mock(Principal.class));

      assertTrue(meta.getColumns().isEmpty());
   }

   /**
    * GitHub code review on stylebi#5052 caught this: {@code catalog}/{@code schema} being an
    * empty string (not null -- e.g. a caller that always sends {@code ""} rather than omitting
    * the field) must not be passed through to {@code getTable} literally, the same way the
    * existing {@code getTableMetaData} precedent (this file, {@code Tool.isEmptyString(catalog)
    * ? null : catalog}) already normalizes it -- otherwise {@code getTable} searches for a
    * catalog/schema node literally named {@code ""}, fails to match, and the new check
    * incorrectly throws "not found" for a real, zero-column table.
    */
   @Test
   void emptyStringCatalogAndSchemaAreNormalizedBeforeGetTableLookup() throws Exception {
      MetadataApiService service = createService();

      when(metaDataProvider.getMetaData(any(XNode.class), eq(true)))
         .thenReturn(tableDataWithColumns());
      // Only stub the null-catalog/null-schema form -- if the fix regresses and passes ""
      // straight through, this stub won't match, getTable() returns null (Mockito default),
      // and the test fails on the assertion below instead of a stray unstubbed-call error.
      when(metaDataProvider.getTable(eq(null), eq(null), eq("PermissionFilteredView"), eq(false)))
         .thenReturn(new XNode("PermissionFilteredView"));

      DatabaseTableMeta meta = service.getTableDetails(
         DS_NAME, "PermissionFilteredView", "", "", mock(Principal.class));

      assertTrue(meta.getColumns().isEmpty());
   }

   /**
    * GitHub code review on stylebi#5052 caught this: {@code tableName} may be a fully-qualified
    * {@code catalog.table} name (per {@code DefaultMetaDataProvider.getTable}'s own javadoc) with
    * {@code catalog}/{@code schema} left null -- exactly what {@code getQualifiedTableNode} (used
    * a few lines above the fix, for the column-fetch lookup) already strips down to the bare
    * table name. The fix must reuse that same stripped name for the {@code getTable} lookup too,
    * not the raw, still-qualified {@code tableName} parameter -- otherwise {@code getTable}'s own
    * matching (which compares against the bare catalog-node name) never matches a qualified
    * string, and the new check incorrectly throws "not found" for a real, zero-column table.
    */
   @Test
   void qualifiedTableNameIsStrippedBeforeGetTableLookup() throws Exception {
      MetadataApiService service = createService();

      // hasCatalog=true is what makes SQLTypes.getQualifiedTableNode's catalog-stripping branch
      // engage at all -- the existing tests above never set this, so they never exercise
      // stripping (their tableName has no "." in it and node.getName() == tableName trivially).
      XNode rootMetaData = new XNode();
      rootMetaData.setAttribute("hasCatalog", "true");
      when(metaDataProvider.getRootMetaData(anyString())).thenReturn(rootMetaData);

      when(metaDataProvider.getMetaData(any(XNode.class), eq(true)))
         .thenReturn(tableDataWithColumns());
      // Stub only the bare, stripped name -- if the fix regresses and passes the raw qualified
      // tableName straight through, this stub won't match and the test fails on the assertion
      // below (getTable() returns null via Mockito's default) instead of silently passing.
      when(metaDataProvider.getTable(eq(null), eq(null), eq("PermissionFilteredView"), eq(false)))
         .thenReturn(new XNode("PermissionFilteredView"));

      DatabaseTableMeta meta = service.getTableDetails(
         DS_NAME, "SOMECATALOG.PermissionFilteredView", null, null, mock(Principal.class));

      assertTrue(meta.getColumns().isEmpty());
   }
}
