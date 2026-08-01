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
package inetsoft.uql.jdbc;

import inetsoft.uql.XNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for the FK_NAME column being dropped from KEYRELATION meta-data.
 *
 * {@code getKeyRelationship} read only columns 1-8 of {@link DatabaseMetaData#getImportedKeys},
 * never column 12 (FK_NAME). Its sole consumer, {@code MetadataApiService.buildRelationships},
 * groups rows by (fkTable, pkTable, pkCatalog, pkSchema, fkName) so that two distinct constraints
 * between the same pair of tables stay distinct. With fkName always absent, that key degraded to
 * the table pair alone and INDEPENDENT single-column foreign keys were merged into one bogus
 * composite relationship — e.g. Odoo's sale_order.partner_id, .partner_invoice_id and
 * .partner_shipping_id, three separate constraints all referencing res_partner.id, collapsed into
 * a single three-column relationship pointing at a one-column key.
 *
 * The driver does supply FK_NAME (verified against PostgreSQL 42.7.x); the loss was entirely in
 * this reader. These tests pin the WIRING — that column 12 is read and surfaced as the
 * {@code fkName} attribute — so reverting the fix fails them.
 */
@Tag("core")
class JDBCHandlerKeyRelationshipTest {
   /** One row of a getImportedKeys result, in JDBC column order for the fields we read. */
   private record ImportedKeyRow(String pkTableName, String pkColumnName, String fkTableName,
                                 String fkColumnName, String fkName) {}

   /**
    * Invokes the real private getKeyRelationship against a mocked driver, returning the XNode tree
    * it builds.
    */
   private static XNode keyRelationship(List<ImportedKeyRow> rows) throws Exception {
      ResultSet importedKeys = mock(ResultSet.class);
      // next() walks the row list; the column getters read whichever row it landed on.
      final int[] cursor = { -1 };

      when(importedKeys.next()).thenAnswer(inv -> ++cursor[0] < rows.size());
      when(importedKeys.getString(anyInt())).thenAnswer(inv -> {
         ImportedKeyRow row = rows.get(cursor[0]);

         return switch((Integer) inv.getArgument(0)) {
            case 1, 2, 5, 6 -> null;          // pk/fk catalog + schema, unused here
            case 3 -> row.pkTableName();
            case 4 -> row.pkColumnName();
            case 7 -> row.fkTableName();
            case 8 -> row.fkColumnName();
            case 12 -> row.fkName();          // FK_NAME — the column that used to be dropped
            default -> null;
         };
      });

      ResultSet schemas = mock(ResultSet.class);
      when(schemas.next()).thenReturn(false);

      DatabaseMetaData meta = mock(DatabaseMetaData.class);
      when(meta.getImportedKeys(any(), any(), any())).thenReturn(importedKeys);
      when(meta.getSchemas()).thenReturn(schemas);

      JDBCHandler handler = new JDBCHandler();
      // Mocked rather than constructed: JDBCDataSource's constructor resolves a CredentialService
      // Spring bean, which does not exist in a plain unit test.
      JDBCDataSource xds = mock(JDBCDataSource.class);
      when(xds.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_POSTGRESQL);
      // The handler's datasource is normally installed by connect(); getUser() reads its type.
      Field xdsField = JDBCHandler.class.getDeclaredField("xds");
      xdsField.setAccessible(true);
      xdsField.set(handler, xds);

      XNode query = new XNode("sale_order");
      query.setAttribute("type", "KEYRELATION");
      // Supplying the schema keeps getUser() off the DBPROPERTIES round-trip.
      query.setAttribute("schema", "public");

      Method m = JDBCHandler.class
         .getDeclaredMethod("getKeyRelationship", DatabaseMetaData.class, XNode.class);
      m.setAccessible(true);
      return (XNode) m.invoke(handler, meta, query);
   }

   private static List<String> attributes(XNode root, String attribute) {
      List<String> values = new ArrayList<>();

      for(int i = 0; i < root.getChildCount(); i++) {
         values.add((String) root.getChild(i).getAttribute(attribute));
      }

      return values;
   }

   @Test
   void fkNameIsReadFromColumn12() throws Exception {
      XNode root = keyRelationship(List.of(new ImportedKeyRow(
         "res_partner", "id", "sale_order", "partner_id", "sale_order_partner_id_fkey")));

      assertEquals(1, root.getChildCount());
      // THE BUG: this attribute was never set, so it read back null for every row.
      assertEquals("sale_order_partner_id_fkey", root.getChild(0).getAttribute("fkName"));
   }

   @Test
   void distinctConstraintsToTheSameTableKeepDistinctNames() throws Exception {
      // The Odoo case that motivated the fix: three separate FKs, one target table.
      XNode root = keyRelationship(List.of(
         new ImportedKeyRow("res_partner", "id", "sale_order", "partner_id",
                            "sale_order_partner_id_fkey"),
         new ImportedKeyRow("res_partner", "id", "sale_order", "partner_invoice_id",
                            "sale_order_partner_invoice_id_fkey"),
         new ImportedKeyRow("res_partner", "id", "sale_order", "partner_shipping_id",
                            "sale_order_partner_shipping_id_fkey")));

      assertEquals(3, root.getChildCount());
      // Distinct names are what let buildRelationships keep these three relationships apart
      // instead of merging them into one three-column composite.
      assertEquals(List.of("sale_order_partner_id_fkey",
                           "sale_order_partner_invoice_id_fkey",
                           "sale_order_partner_shipping_id_fkey"),
                   attributes(root, "fkName"));
      assertEquals(List.of("partner_id", "partner_invoice_id", "partner_shipping_id"),
                   attributes(root, "fkColumnName"));
   }

   @Test
   void compositeConstraintSharesOneName() throws Exception {
      // Both columns of a genuine composite FK carry the same FK_NAME, which is what tells
      // buildRelationships to fold them into ONE multi-column relationship.
      XNode root = keyRelationship(List.of(
         new ImportedKeyRow("target", "key_a", "source", "col_a", "source_composite_fkey"),
         new ImportedKeyRow("target", "key_b", "source", "col_b", "source_composite_fkey")));

      assertEquals(List.of("source_composite_fkey", "source_composite_fkey"),
                   attributes(root, "fkName"));
   }

   @Test
   void aDriverThatOmitsFkNameStillProducesRows() throws Exception {
      // Not every driver populates FK_NAME. A null must degrade to the table-pair grouping,
      // never break the read.
      XNode root = keyRelationship(List.of(
         new ImportedKeyRow("res_partner", "id", "sale_order", "partner_id", null)));

      assertEquals(1, root.getChildCount());
      assertNotNull(root.getChild(0).getAttribute("fkColumnName"));
      assertEquals(null, root.getChild(0).getAttribute("fkName"));
   }
}
