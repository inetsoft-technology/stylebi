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
package inetsoft.uql.serverfile;

import inetsoft.uql.tabular.ColumnDefinition;
import inetsoft.uql.tabular.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A5/C2 (reuse DataType's existing XSchema mapping, no new inference) and D.4's
 * loud-on-null-type behavior.
 */
class ServerFileCatalogTypeCanaryTest {
   @Test
   void dataTypeHasExactlyTwelveConstants() {
      // A count canary, not a mapping test: ServerFileCatalog reuses DataType.type() directly
      // (already an XSchema constant on every DataType value) rather than writing a second switch
      // over it (C2). This only catches a new constant landing without review.
      assertEquals(12, DataType.values().length);
   }

   @Test
   void aColumnWithNoRecognizedTypeThrowsANamedError_insteadOfNpeingLater() {
      ServerFileDataSource ds = new ServerFileDataSource();
      ds.setName("type-test-ds");

      ColumnDefinition bad = new ColumnDefinition();
      bad.setName("weird");
      bad.setType(null);   // what DataType.fromType returns for an unrecognized type string

      Exception ex = assertThrows(Exception.class,
         () -> ServerFileCatalog.toTabularColumns(ds, "target.csv", new ColumnDefinition[]{ bad }));
      assertTrue(ex.getMessage().contains("weird"));
      assertTrue(ex.getMessage().contains("type-test-ds"));
   }
}
