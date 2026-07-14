/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.report.script;

import inetsoft.report.filter.CalcFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.script.ScriptUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for #75423: a calc-table cell formula reads bound columns as
 * {@code data['Col']}. GraalJS gates member reads on {@code hasMember} (unlike
 * Rhino, whose {@code get()} was always invoked), so {@code TableArray.hasMember}
 * must report dynamic column references as present — otherwise {@code data['Col']}
 * reads as undefined and calc-table group expansion collapses to zero rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableArrayTest {
   private static XTable table() {
      return new DefaultTableLens(new Object[][] {
         { "col1", "col2", "col3" },
         { "a", 1, 5.0 },
         { "b", 3, 10.0 },
         { "c", 1, 2.5 }
      });
   }

   @Test
   void hasMemberReportsExistingColumnsPresent() {
      TableArray arr = new TableArray(table());

      // dynamic columns must be present so GraalJS dispatches getMember
      assertTrue(arr.hasMember("col1"), "existing column 'col1' must be present");
      assertTrue(arr.hasMember("col2"));
      assertTrue(arr.hasMember("length"));
      assertTrue(arr.hasMember("size"));
   }

   @Test
   void hasMemberReportsCellRangeExpressionsPresent() {
      TableArray arr = new TableArray(table());

      // group / condition / subtable range expressions resolve lazily in getMember
      assertTrue(arr.hasMember("col1@col2"));
      assertTrue(arr.hasMember("col3?col2=1"));
      assertTrue(arr.hasMember("*@col1"));
   }

   @Test
   void hasMemberRejectsNonColumns() {
      TableArray arr = new TableArray(table());

      // non-columns (incl. JS-internal names) must not be over-claimed as present
      assertFalse(arr.hasMember("nonexistent"));
      assertFalse(arr.hasMember("toString"));
      assertFalse(arr.hasMember("then"));
   }

   /**
    * Minimal {@code CalcFilter} (e.g. a crosstab) stub exposing a fixed set of
    * measure/aggregate headers, without needing a full CrossTabFilter setup.
    */
   private static class MeasureTableLens extends DefaultTableLens implements CalcFilter {
      MeasureTableLens() {
         super(new Object[][] {
            { "col1", "col2" },
            { "a", 1 }
         });
      }

      @Override
      public void setMeasureNames(String[] names) {
      }

      @Override
      public List<String> getMeasureHeaders() {
         return Arrays.asList("Sum(category_id)", "Sum(customer_id)");
      }
   }

   @Test
   void hasMemberReportsBareMeasureNamesPresent() {
      // #75647: a calc table's grand-total cell has no row/col group, so its formula
      // is a bare reference like data['Sum(category_id)'] with no "@group:value"
      // qualifier. The measure name never appears as a literal column header (that
      // identity varies per data cell, not per fixed column), so hasMember must
      // special-case it the same way it already does for bare dimension names.
      TableArray arr = new TableArray(new MeasureTableLens());

      assertTrue(arr.hasMember("Sum(category_id)"),
         "bare measure name must be present so GraalJS dispatches getMember");
      assertTrue(arr.hasMember("Sum(customer_id)"));
      assertFalse(arr.hasMember("Sum(nonexistent)"),
         "a name that isn't a measure header must not be over-claimed as present");
   }

   @Test
   void unwrapReturnsUnderlyingXTable() {
      // #75576: ScriptUtil.unwrap must unwrap a TableArray to its XTable so host
      // code (toList/mapList/etc.) can process a data['*@...'] subtable reference
      // instead of leaving the non-serializable wrapper in a cell value.
      XTable xtable = table();
      Object unwrapped = ScriptUtil.unwrap(new TableArray(xtable));

      assertInstanceOf(XTable.class, unwrapped);
      assertSame(xtable, unwrapped);
   }
}
