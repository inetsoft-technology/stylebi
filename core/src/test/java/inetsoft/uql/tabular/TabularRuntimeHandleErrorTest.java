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
package inetsoft.uql.tabular;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78494: {@link TabularRuntime#handleError} must fail a scheduled run whether
 * {@code __is_scheduler__} is {@code Boolean.TRUE} (export time) or the String {@code "true"}
 * (open time), and must return the connector's value otherwise. The full query path through
 * {@code XSessionManager} and {@code TabularHandler} is covered by the slow
 * {@link TabularRuntimeSchedulerFlagTest}.
 */
@Tag("core")
class TabularRuntimeHandleErrorTest {
   @Test
   void exportTimeBooleanFlagThrowsWithTheConnectorError() {
      assertFails(Boolean.TRUE);
   }

   @Test
   void openTimeStringFlagThrowsWithTheConnectorError() {
      assertFails("true");
   }

   @Test
   void noFlagReturnsTheSupplierValue() {
      assertSame(fallback, runtime.handleError(new VariableTable(), error, () -> fallback));
   }

   @Test
   void nullVarsReturnTheSupplierValue() {
      assertSame(fallback, runtime.handleError(null, error, () -> fallback));
   }

   @ParameterizedTest
   @ValueSource(strings = { "false", "TRUE", "" })
   void otherStringValuesReturnTheSupplierValue(String flag) {
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", flag);
      assertSame(fallback, runtime.handleError(vars, error, () -> fallback));
   }

   @Test
   void booleanFalseReturnsTheSupplierValue() {
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", Boolean.FALSE);
      assertSame(fallback, runtime.handleError(vars, error, () -> fallback));
   }

   private void assertFails(Object flag) {
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", flag);

      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> runtime.handleError(vars, error, () -> fallback));
      assertSame(error, ex.getCause());
   }

   private final XTableNode fallback = mock(XTableNode.class);
   private final Exception error = new IOException("connector failure");
   private final TabularRuntime runtime = new TabularRuntime() {
      @Override
      public XTableNode runQuery(TabularQuery query, VariableTable params) {
         return null;
      }

      @Override
      public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
      }
   };
}
