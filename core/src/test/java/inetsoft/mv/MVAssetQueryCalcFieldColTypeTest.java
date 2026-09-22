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
package inetsoft.mv;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Redmine bug #76912.
 * <p>
 * {@code MVAssetQuery.getPostBaseTableLens()} materializes MV-backed aggregate Calculated
 * Fields via a {@link FormulaTableLens}. Before the fix, it never called
 * {@code setColType()} on the new formula column, leaving its type {@code null}. When some
 * other part of the query graph later referenced that column via a plain pass-through
 * expression such as {@code field['CalcField1']} (e.g. an ad-hoc filter on a crosstab's
 * aggregate calc-field cell), {@link PostProcessor#formula} dereferenced that {@code null}
 * type and threw an NPE (stack trace in the ticket: {@code PostProcessor.formula():171}).
 * <p>
 * This test reproduces the exact defect shape directly against {@link FormulaTableLens} and
 * {@link PostProcessor#formula}, without needing a live MV/server: it builds a
 * {@code FormulaTableLens} the way {@code MVAssetQuery.getPostBaseTableLens()} used to
 * (pre-fix, column type left unset) and confirms the reported NPE reproduces, then builds
 * the same lens the way the fixed code now does (with {@code setColType()} called) and
 * confirms {@code PostProcessor.formula()} no longer throws.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class MVAssetQueryCalcFieldColTypeTest {
   /**
    * Pre-fix shape: a FormulaTableLens materializing a calc field column with no
    * registered column type (what {@code MVAssetQuery.getPostBaseTableLens()} produced
    * before bug #76912 was fixed). A later {@code field['CalcField1']} pass-through
    * reference through {@code PostProcessor.formula()} must NPE, matching the reported
    * stack trace at {@code PostProcessor.formula():171}.
    */
   @Test
   public void formulaAliasCheckNPEsWhenCalcFieldColTypeIsUnset() {
      TableLens calcLens = createUnregisteredCalcFieldLens();

      NullPointerException npe = assertThrows(NullPointerException.class, () ->
         PostProcessor.formula(calcLens, new String[] { "Alias1" },
                               new String[] { "field['CalcField1']" }, null, new Object(),
                               null, "test", null, List.of(Integer.class),
                               new boolean[] { false }));

      assertNotNull(npe);
   }

   /**
    * Post-fix shape: the same FormulaTableLens, but with the calc field column's type
    * registered via {@code setColType()} -- exactly what
    * {@code MVAssetQuery.getPostBaseTableLens()} now does after the fix for bug #76912.
    * The same {@code field['CalcField1']} pass-through reference must no longer NPE.
    */
   @Test
   public void formulaAliasCheckSucceedsWhenCalcFieldColTypeIsRegistered() {
      TableLens base = XTableUtil.getDefaultTableLens();
      int baseColCount = base.getColCount();
      FormulaTableLens calcLens = new FormulaTableLens(
         base, new String[] { "CalcField1" }, new String[] { "field['col2']" }, null,
         new Object());
      // Util.findColumn()/ColumnIndexMap resolve columns via column identifier (or the
      // header row) before ever touching data rows, so setting the identifier here lets
      // this test exercise the real PostProcessor.formula() alias/type-match path without
      // needing a live script engine to evaluate "field['col2']" against a data row.
      calcLens.setColumnIdentifier(baseColCount, "CalcField1");

      // this is exactly what MVAssetQuery.getPostBaseTableLens() now does for each
      // materialized calc-field column after the fix for bug #76912
      calcLens.setColType(baseColCount, Integer.class);

      assertEquals(Integer.class, calcLens.getColType(baseColCount));

      TableLens result = assertDoesNotThrow(() ->
         PostProcessor.formula(calcLens, new String[] { "Alias1" },
                               new String[] { "field['CalcField1']" }, null, new Object(),
                               null, "test", null, List.of(Integer.class),
                               new boolean[] { false }));

      assertNotNull(result);
   }

   private static TableLens createUnregisteredCalcFieldLens() {
      TableLens base = XTableUtil.getDefaultTableLens();
      FormulaTableLens calcLens = new FormulaTableLens(
         base, new String[] { "CalcField1" }, new String[] { "field['col2']" }, null,
         new Object());
      calcLens.setColumnIdentifier(base.getColCount(), "CalcField1");
      // deliberately no setColType() call -- reproduces the pre-fix MVAssetQuery behavior
      return calcLens;
   }
}
