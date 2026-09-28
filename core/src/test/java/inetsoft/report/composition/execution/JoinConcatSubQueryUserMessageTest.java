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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77248: JoinQuery and ConcatenatedQuery run their sub-queries on a thread pool.
 * User messages a sub-query raises there must reach the calling thread, and a reused
 * pool thread must not leak one task's messages into another task's MessageException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class JoinConcatSubQueryUserMessageTest {
   @BeforeEach
   void clearMessages() {
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
   }

   @Test
   void joinPooledSubQueryMessagesReachCaller() throws Exception {
      JoinQuery query = joinQuery(2, -1);
      TableLens result = query.getPostBaseTableLens0(new VariableTable());

      assertNotNull(result);
      assertCallerHas("sub0 warning", "sub1 warning");
   }

   @Test
   void concatPooledSubQueryMessagesReachCaller() throws Exception {
      ConcatenatedQuery query = concatQuery(2, -1);
      TableLens result = query.getPostBaseTableLens(new VariableTable());

      assertNotNull(result);
      assertCallerHas("sub0 warning", "sub1 warning");
   }

   @Test
   void joinNullSubQueryDoesNotUseOtherTasksMessages() throws Exception {
      JoinQuery query = joinQuery(7, 6);
      Exception ex = assertThrows(Exception.class,
         () -> query.getPostBaseTableLens0(new VariableTable()));

      assertOwnFailureMessage(ex);
      assertCallerHas("sub0 warning", "sub5 warning");
   }

   @Test
   void concatNullSubQueryDoesNotUseOtherTasksMessages() throws Exception {
      ConcatenatedQuery query = concatQuery(7, 6);
      Exception ex = assertThrows(Exception.class,
         () -> query.getPostBaseTableLens(new VariableTable()));

      assertOwnFailureMessage(ex);
      assertCallerHas("sub0 warning", "sub5 warning");
   }

   @Test
   void concatFailingFirstSubQueryKeepsItsMessage() throws Exception {
      ConcatenatedQuery query = concatQuery(2, -1);
      AssetQuery[] subs = (AssetQuery[]) getField(ConcatenatedQuery.class, query, "queries");
      // doAnswer, so stubbing does not run the previous answer on this thread
      doAnswer(inv -> {
         Tool.addUserWarning("sub0 warning");
         throw new IllegalStateException("sub0 failed");
      }).when(subs[0]).getTableLens(any());
      assertNull(Tool.getUserMessage());

      assertThrows(ExecutionException.class,
         () -> query.getPostBaseTableLens(new VariableTable()));
      assertCallerHas("sub0 warning");
   }

   private static void assertOwnFailureMessage(Exception ex) {
      assertInstanceOf(MessageException.class, ex);
      assertFalse(ex.getMessage().contains("warning"),
                  "failure text leaked another task's message: " + ex.getMessage());
      assertEquals(Catalog.getCatalog().getString("common.table.getDataFailed"),
                   ex.getMessage());
   }

   private static void assertCallerHas(String... texts) {
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message, "no user message on the calling thread");

      for(String text : texts) {
         assertTrue(message.getMessage().contains(text),
                    "missing '" + text + "' in: " + message.getMessage());
      }
   }

   private static JoinQuery joinQuery(int count, int nullIndex) throws Exception {
      JoinQuery query = mock(JoinQuery.class, CALLS_REAL_METHODS);
      AbstractJoinTableAssembly table = mock(AbstractJoinTableAssembly.class);
      when(table.getOperator(anyString(), anyString()))
         .thenReturn(operator(TableAssemblyOperator.MERGE_JOIN));
      setField(JoinQuery.class, query, "table", table);
      setField(JoinQuery.class, query, "tables", tables(count));
      setField(JoinQuery.class, query, "queries", subQueries(count, nullIndex));
      return query;
   }

   private static ConcatenatedQuery concatQuery(int count, int nullIndex) throws Exception {
      ConcatenatedQuery query = mock(ConcatenatedQuery.class, CALLS_REAL_METHODS);
      ConcatenatedTableAssembly table = mock(ConcatenatedTableAssembly.class);
      when(table.getOperator(anyString(), anyString()))
         .thenReturn(operator(TableAssemblyOperator.UNION));
      setField(ConcatenatedQuery.class, query, "table", table);
      setField(ConcatenatedQuery.class, query, "tables", tables(count));
      setField(ConcatenatedQuery.class, query, "queries", subQueries(count, nullIndex));
      return query;
   }

   private static TableAssemblyOperator operator(int operation) {
      TableAssemblyOperator top = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(operation);
      top.addOperator(op);
      return top;
   }

   private static TableAssembly[] tables(int count) {
      TableAssembly[] tables = new TableAssembly[count];

      for(int i = 0; i < count; i++) {
         tables[i] = mock(TableAssembly.class);
         when(tables[i].getName()).thenReturn("T" + i);
      }

      return tables;
   }

   // each sub-query adds its own warning and returns one row, except nullIndex,
   // which adds nothing and returns null
   private static AssetQuery[] subQueries(int count, int nullIndex) throws Exception {
      AssetQuery[] queries = new AssetQuery[count];

      for(int i = 0; i < count; i++) {
         final int index = i;
         queries[i] = mock(AssetQuery.class);
         when(queries[i].getTableLens(any())).thenAnswer(inv -> {
            if(index == nullIndex) {
               return null;
            }

            Tool.addUserWarning("sub" + index + " warning");
            return new DefaultTableLens(new Object[][] { { "col" }, { index } });
         });
      }

      return queries;
   }

   private static void setField(Class<?> cls, Object target, String name, Object value)
      throws Exception
   {
      Field field = cls.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   private static Object getField(Class<?> cls, Object target, String name) throws Exception {
      Field field = cls.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
   }
}
