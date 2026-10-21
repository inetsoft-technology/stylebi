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
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

   // Bug #78940: the AssetDataCache key is derived from this prepared clone, so an invalidation
   // keyed from the original assembly misses it whenever the clone differs -- unsorted columns,
   // a hidden column, or a removable alias. Pin the differences clearCache() must account for.
   @Test
   public void testPreparedColumnsAreSortedVisibleAndUnaliased78940() {
      ColumnRef b = new ColumnRef(new AttributeRef(null, "B"));
      ColumnRef a = new ColumnRef(new AttributeRef(null, "A"));
      a.setVisible(false);
      ColumnRef c = new ColumnRef(new AttributeRef(null, "C"));
      c.setAlias("C0");

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(b);
      cols.addAttribute(a);
      cols.addAttribute(c);

      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("T");
      when(table.getAggregateInfo()).thenReturn(new AggregateInfo());
      when(table.getColumnSelection(true)).thenReturn(cols);
      when(table.getColumnSelection(false)).thenReturn(cols);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getWorksheet()).thenReturn(new Worksheet());

      new AssetQueryCacheNormalizer(table, box, AssetQuerySandbox.RUNTIME_MODE);

      ArgumentCaptor<ColumnSelection> captor = ArgumentCaptor.forClass(ColumnSelection.class);
      verify(table).setColumnSelection(captor.capture(), eq(false));
      ColumnSelection prepared = captor.getValue();

      assertEquals(3, prepared.getAttributeCount());
      String[] expected = { "A", "B", "C" };

      for(int i = 0; i < 3; i++) {
         ColumnRef col = (ColumnRef) prepared.getAttribute(i);
         assertEquals(expected[i], col.getAttribute());
         assertTrue(col.isVisible());
         assertNull(col.getAlias());
      }
   }

   // Bug #78679 (problem 2): an expression that reads an aliased column must keep the alias,
   // whichever syntax it reads it with, or the formula table lens no longer finds the column.
   private static String aliasAfterNormalize(String expression) {
      return aliasAfterNormalize(expression, true);
   }

   private static String aliasAfterNormalize(String expression, boolean visible) {
      ColumnRef id = new ColumnRef(new AttributeRef(null, "CATEGORY_ID"));
      id.setAlias("CID");
      id.setVisible(visible);
      ColumnRef other = new ColumnRef(new AttributeRef(null, "OTHER"));
      ExpressionRef eref = new ExpressionRef(null, "Dbl");
      eref.setExpression(expression);
      ColumnRef dbl = new ColumnRef(eref);

      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(id);
      cols.addAttribute(other);
      cols.addAttribute(dbl);

      TableAssembly table = mock(TableAssembly.class);
      when(table.getName()).thenReturn("T");
      when(table.getAggregateInfo()).thenReturn(new AggregateInfo());
      when(table.getColumnSelection(true)).thenReturn(cols);
      when(table.getColumnSelection(false)).thenReturn(cols);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getWorksheet()).thenReturn(new Worksheet());

      new AssetQueryCacheNormalizer(table, box, AssetQuerySandbox.RUNTIME_MODE);

      ArgumentCaptor<ColumnSelection> captor = ArgumentCaptor.forClass(ColumnSelection.class);
      verify(table).setColumnSelection(captor.capture(), eq(false));

      for(int i = 0; i < captor.getValue().getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) captor.getValue().getAttribute(i);

         if("CATEGORY_ID".equals(col.getAttribute())) {
            return col.getAlias();
         }
      }

      throw new AssertionError("column not found");
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "field.CID * 2",
      "field . CID * 2",
      "field.cid * 2",
      "field['CID'] * 2",
      "field[\"CID\"] * 2",
      "field ['CID'] * 2",
      "field['cid'] * 2",
      "field[ 'CID' ] * 2",
      "// customer's id\nfield['CID'] * 2",
      "/* it's */ field['CID'] * 2",
      "'a\\' + field['CID']",
      "eval(\"field['CID']\")",
      "// it's\nfield.CID * 2"
   })
   public void testAliasKeptWhenExpressionReadsIt(String expression) {
      assertEquals("CID", aliasAfterNormalize(expression));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "field.OTHER * 2",
      "field.CID2 * 2",
      "Other.CID * 2",
      "1 + 1"
   })
   public void testAliasRemovedWhenExpressionDoesNotReadIt(String expression) {
      assertNull(aliasAfterNormalize(expression));
   }

   @Test
   public void testAliasKeptForHiddenColumnReadWithDot() {
      assertEquals("CID", aliasAfterNormalize("field.CID * 2", false));
   }

   // the text fallback is permissive: a read inside a string literal also keeps the alias
   @Test
   public void testStringLiteralMentionKeepsAliasConservatively() {
      assertEquals("CID", aliasAfterNormalize("'field.CID'.length"));
   }

   // the script tokenizer does not skip comments, so a comment-only mention conservatively keeps the
   // alias (harmless: it only skips an optional cache normalization)
   @Test
   public void testCommentOnlyMentionKeepsAliasConservatively() {
      assertEquals("CID", aliasAfterNormalize("/* field.CID */ 1 + 1"));
   }
}
