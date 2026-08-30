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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.TableAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #76350 PQE-001: AssetQuery.doGetTableLens() legitimately returns null in RUNTIME_MODE
 * (e.g. a table bound to a dead/misconfigured JDBC connection), but
 * transformTableLens() used to call tableLens.getColCount() unconditionally, turning that
 * into an unhandled NPE instead of the existing "table not found or produced no data"
 * message one layer up in WorksheetPreviewService.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AssetQueryCacheNormalizerTest {
   @Test
   public void testTransformTableLensReturnsNullForNullInput() {
      // isApplicable() would otherwise be true: not distinct, not design mode, no
      // ViewsheetSandbox, not an embedded/rotated/SQL-edit table.
      TableAssembly table = mock(TableAssembly.class);
      when(table.getColumnSelection(true)).thenReturn(new ColumnSelection());
      when(table.getColumnSelection(false)).thenReturn(new ColumnSelection());

      AssetQuerySandbox box = mock(AssetQuerySandbox.class);

      AssetQueryCacheNormalizer normalizer =
         new AssetQueryCacheNormalizer(table, box, AssetQuerySandbox.RUNTIME_MODE);

      assertNull(normalizer.transformTableLens(null));
   }
}
