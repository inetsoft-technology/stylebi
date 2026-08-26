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

import inetsoft.uql.XNode;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.web.wiz.model.DatabaseTableInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@code getDatabaseTables}'s primary-key enrichment: each returned
 * {@link DatabaseTableInfo} should carry {@code primaryKeyColumns}, read the same way
 * {@code buildRelationships} already reads FK columns -- one {@code PRIMARYKEY} metadata query
 * per table via {@link DefaultMetaDataProvider#getPrimaryKeys}.
 */
@Tag("core")
class MetadataApiServicePrimaryKeyColumnsTest {

   private static DatabaseTableInfo table(String catalog, String schema, String name) {
      DatabaseTableInfo info = new DatabaseTableInfo();
      info.setCatalog(catalog);
      info.setSchema(schema);
      info.setTable(name);
      return info;
   }

   private static XNode pkResult(String... pkColumnNames) {
      XNode root = new XNode();

      for(String col : pkColumnNames) {
         XNode child = new XNode();
         child.setAttribute("pkColumnName", col);
         root.addChild(child);
      }

      return root;
   }

   @Test
   void populatesPrimaryKeyColumnsForDeclaredSingleColumnKey() throws Exception {
      DatabaseTableInfo orders = table(null, "dbo", "Orders");
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getPrimaryKeys(any(XNode.class))).thenReturn(pkResult("OrderID"));

      MetadataApiService.populatePrimaryKeys(provider, List.of(orders));

      assertEquals(List.of("OrderID"), orders.getPrimaryKeyColumns());
   }

   @Test
   void populatesCompositePrimaryKeyColumnsInResultOrder() throws Exception {
      DatabaseTableInfo lineItems = table(null, "dbo", "OrderLineItems");
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getPrimaryKeys(any(XNode.class)))
         .thenReturn(pkResult("OrderID", "LineNumber"));

      MetadataApiService.populatePrimaryKeys(provider, List.of(lineItems));

      assertEquals(List.of("OrderID", "LineNumber"), lineItems.getPrimaryKeyColumns());
   }

   @Test
   void queriesEachTableWithItsOwnCatalogAndSchema() throws Exception {
      DatabaseTableInfo orders = table("mydb", "dbo", "Orders");
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getPrimaryKeys(any(XNode.class))).thenReturn(pkResult("OrderID"));

      MetadataApiService.populatePrimaryKeys(provider, List.of(orders));

      org.mockito.ArgumentCaptor<XNode> captor = org.mockito.ArgumentCaptor.forClass(XNode.class);
      verify(provider).getPrimaryKeys(captor.capture());
      XNode query = captor.getValue();
      assertEquals("Orders", query.getName());
      assertEquals("mydb", query.getAttribute("catalog"));
      assertEquals("dbo", query.getAttribute("schema"));
   }

   @Test
   void tableWithNoDeclaredPrimaryKeyGetsEmptyList() throws Exception {
      DatabaseTableInfo noKeyTable = table(null, "dbo", "AuditLog");
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getPrimaryKeys(any(XNode.class))).thenReturn(pkResult());

      MetadataApiService.populatePrimaryKeys(provider, List.of(noKeyTable));

      assertTrue(noKeyTable.getPrimaryKeyColumns().isEmpty());
   }

   @Test
   void failedPrimaryKeyLookupLeavesEmptyListRatherThanThrowing() throws Exception {
      DatabaseTableInfo orders = table(null, "dbo", "Orders");
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getPrimaryKeys(any(XNode.class))).thenThrow(new RuntimeException("boom"));

      MetadataApiService.populatePrimaryKeys(provider, List.of(orders));

      assertTrue(orders.getPrimaryKeyColumns().isEmpty());
   }

   @Test
   void eachTableInTheListIsQueriedIndependently() throws Exception {
      DatabaseTableInfo orders = table(null, "dbo", "Orders");
      DatabaseTableInfo customers = table(null, "dbo", "Customers");
      DefaultMetaDataProvider provider = mock(DefaultMetaDataProvider.class);
      when(provider.getPrimaryKeys(any(XNode.class)))
         .thenReturn(pkResult("OrderID"), pkResult("CustomerID"));

      MetadataApiService.populatePrimaryKeys(provider, List.of(orders, customers));

      assertEquals(List.of("OrderID"), orders.getPrimaryKeyColumns());
      assertEquals(List.of("CustomerID"), customers.getPrimaryKeyColumns());
   }
}
