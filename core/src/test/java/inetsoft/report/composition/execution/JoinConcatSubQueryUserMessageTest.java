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
import inetsoft.report.internal.XNodeMetaTable;
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
import java.util.regex.Pattern;

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

      // @by Bug #78972: index 0 now gets the same catch(ExecutionException) treatment as
      // index >= 1, so this is normalized into a MessageException instead of a raw
      // ExecutionException.
      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens(new VariableTable()));
      assertEquals("sub0 failed", ex.getMessage());
      assertCallerHas("sub0 warning");
   }

   @Test
   void joinFailingFirstSubQueryKeepsItsMessage() throws Exception {
      JoinQuery query = joinQuery(2, -1);
      AssetQuery[] subs = (AssetQuery[]) getField(JoinQuery.class, query, "queries");
      // doAnswer, so stubbing does not run the previous answer on this thread
      doAnswer(inv -> {
         Tool.addUserWarning("sub0 warning");
         throw new IllegalStateException("sub0 failed");
      }).when(subs[0]).getTableLens(any());
      assertNull(Tool.getUserMessage());

      // @by Bug #78972: index 0 now gets the same catch(ExecutionException) treatment as
      // index >= 1, so this is normalized into a MessageException instead of a raw
      // ExecutionException.
      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens0(new VariableTable()));
      assertEquals("sub0 failed", ex.getMessage());
      assertCallerHas("sub0 warning");
   }

   /**
    * Bug #78972: a sub-query failure that does NOT produce its own MessageException (e.g. a
    * raw SQL-execution failure, routed through XSessionManager.getXNodeTableLens's generic
    * catch(Exception) branch) leaves a dirty, class-name-carrying warning
    * (Tool.addUserWarning) on the sub-query's own pool thread, alongside a clean root-cause
    * text captured separately into XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE on that same
    * thread. Without draining the clean text too, the thrown MessageException's own message
    * still leaks the raw exception's fully-qualified class name.
    */
   @Test
   void concatFailingFirstSubQueryDoesNotLeakClassName() throws Exception {
      ConcatenatedQuery query = concatQuery(2, -1);
      AssetQuery[] subs = (AssetQuery[]) getField(ConcatenatedQuery.class, query, "queries");
      doAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set(
            "Multiple DISTINCT aggregates are not supported at this time.");
         Tool.addUserWarning("Failed to get table data for query: Q0: " +
            "java.sql.SQLSyntaxErrorException: Multiple DISTINCT aggregates are not " +
            "supported at this time.");
         return null;
      }).when(subs[0]).getTableLens(any());

      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens(new VariableTable()));
      assertNoLeakedClassName(ex.getMessage());
      assertTrue(ex.getMessage().contains(
         "Multiple DISTINCT aggregates are not supported at this time."),
         "missing the real cause text: " + ex.getMessage());
   }

   @Test
   void joinFailingFirstSubQueryDoesNotLeakClassName() throws Exception {
      JoinQuery query = joinQuery(2, -1);
      AssetQuery[] subs = (AssetQuery[]) getField(JoinQuery.class, query, "queries");
      doAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set(
            "Multiple DISTINCT aggregates are not supported at this time.");
         Tool.addUserWarning("Failed to get table data for query: Q0: " +
            "java.sql.SQLSyntaxErrorException: Multiple DISTINCT aggregates are not " +
            "supported at this time.");
         return null;
      }).when(subs[0]).getTableLens(any());

      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens0(new VariableTable()));
      assertNoLeakedClassName(ex.getMessage());
      assertTrue(ex.getMessage().contains(
         "Multiple DISTINCT aggregates are not supported at this time."),
         "missing the real cause text: " + ex.getMessage());
   }

   /**
    * Bug #78972 (round 2): the SERIAL execution path (a worksheet script/expression holding
    * this sandbox's script-execution lock, per the {@code @by Bug #75614}/{@code #77873}
    * comments on the {@code serial} flag) runs every sub-query's task synchronously on the
    * SAME calling thread, rather than fanning it out to a pool thread -- so the class-name
    * leak must be fixed there too, not only in the pool path the other two leak-check tests
    * above exercise.
    */
   @Test
   void concatFailingFirstSubQuerySerialModeDoesNotLeakClassName() throws Exception {
      ConcatenatedQuery query = concatQuery(2, -1);
      forceSerialMode(query);
      AssetQuery[] subs = (AssetQuery[]) getField(ConcatenatedQuery.class, query, "queries");
      doAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set(
            "Multiple DISTINCT aggregates are not supported at this time.");
         Tool.addUserWarning("Failed to get table data for query: Q0: " +
            "java.sql.SQLSyntaxErrorException: Multiple DISTINCT aggregates are not " +
            "supported at this time.");
         return null;
      }).when(subs[0]).getTableLens(any());

      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens(new VariableTable()));
      assertNoLeakedClassName(ex.getMessage());
      assertTrue(ex.getMessage().contains(
         "Multiple DISTINCT aggregates are not supported at this time."),
         "missing the real cause text: " + ex.getMessage());
   }

   @Test
   void joinFailingFirstSubQuerySerialModeDoesNotLeakClassName() throws Exception {
      JoinQuery query = joinQuery(2, -1);
      forceSerialMode(query);
      AssetQuery[] subs = (AssetQuery[]) getField(JoinQuery.class, query, "queries");
      doAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set(
            "Multiple DISTINCT aggregates are not supported at this time.");
         Tool.addUserWarning("Failed to get table data for query: Q0: " +
            "java.sql.SQLSyntaxErrorException: Multiple DISTINCT aggregates are not " +
            "supported at this time.");
         return null;
      }).when(subs[0]).getTableLens(any());

      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens0(new VariableTable()));
      assertNoLeakedClassName(ex.getMessage());
      assertTrue(ex.getMessage().contains(
         "Multiple DISTINCT aggregates are not supported at this time."),
         "missing the real cause text: " + ex.getMessage());
   }

   /**
    * Review round 1, Finding 1 (confidence 85): a BoundQuery-like member's clean cause
    * (captured into {@code XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE} by
    * {@code XSessionManager.getXNodeTableLens}'s own generic failure branch) must not survive,
    * unread and unremoved, to be misattributed to a LATER, unrelated member's own failure when
    * that later member is one of the six {@code AssetQuery} subclasses that never touch this
    * thread-local at all ({@code EmbeddedQuery}, {@code MirrorQuery}, {@code RotatedQuery},
    * {@code UnpivotQuery}, {@code DataQuery}, {@code MVAssetQuery}). This is reachable with as
    * few as 2 members in serial mode, where both run on the same thread.
    *
    * <p>Member 0 must SUCCEED overall (return a non-null lens, so its own
    * {@code results.get(0).get()} does not throw and processing reaches member 1) while still
    * leaving the thread-local dirty as a side effect -- mirroring
    * {@code XSessionManager.getXNodeTableLens}'s own structure, where {@code table} can already
    * be assigned a valid, non-null lens before an exception during later post-processing (still
    * inside the same try block) is caught by the generic {@code catch(Exception)} branch, which
    * sets the thread-local but does not touch the already-built {@code table} it returns. If
    * member 0 instead failed outright (its own {@code results.get(0).get()} throwing), its OWN
    * index-0 catch block would report ITS OWN (correctly attributed) cause and the method would
    * exit immediately, never reaching member 1 at all -- so that shape would not exercise this
    * finding.
    */
   @Test
   void concatSerialModeDoesNotMisattributeASecondMembersOwnFailureToAnEarlierSucceedingMembersStaleCause()
      throws Exception
   {
      ConcatenatedQuery query = concatQuery(2, -1);
      forceSerialMode(query);
      AssetQuery[] subs = (AssetQuery[]) getField(ConcatenatedQuery.class, query, "queries");

      // member 0: succeeds overall, but leaves XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE dirty
      // as a side effect.
      doAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set("member 0's own stale leftover");
         return new DefaultTableLens(new Object[][] { { "col" }, { 0 } });
      }).when(subs[0]).getTableLens(any());

      // member 1: a type that never touches XNodeMetaTable (e.g. MirrorQuery/EmbeddedQuery),
      // failing for a completely unrelated reason of its own.
      doAnswer(inv -> {
         throw new IllegalStateException("member 1's own unrelated failure");
      }).when(subs[1]).getTableLens(any());

      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens(new VariableTable()));
      assertEquals("member 1's own unrelated failure", ex.getMessage(),
         "member 1's own failure must not be misattributed to member 0's stale leftover cause");
   }

   @Test
   void joinSerialModeDoesNotMisattributeASecondMembersOwnFailureToAnEarlierSucceedingMembersStaleCause()
      throws Exception
   {
      JoinQuery query = joinQuery(2, -1);
      forceSerialMode(query);
      AssetQuery[] subs = (AssetQuery[]) getField(JoinQuery.class, query, "queries");

      doAnswer(inv -> {
         XNodeMetaTable.LAST_FAILED_QUERY_MESSAGE.set("member 0's own stale leftover");
         return new DefaultTableLens(new Object[][] { { "col" }, { 0 } });
      }).when(subs[0]).getTableLens(any());

      doAnswer(inv -> {
         throw new IllegalStateException("member 1's own unrelated failure");
      }).when(subs[1]).getTableLens(any());

      MessageException ex = assertThrows(MessageException.class,
         () -> query.getPostBaseTableLens0(new VariableTable()));
      assertEquals("member 1's own unrelated failure", ex.getMessage(),
         "member 1's own failure must not be misattributed to member 0's stale leftover cause");
   }

   /**
    * Forces {@code serial} (the {@code JoinQuery}/{@code ConcatenatedQuery} fan-out-vs-serial
    * flag) to true via {@code box.holdsScriptExecutionLock()}, the same alternative the real
    * code checks alongside {@code JavaScriptEngine.isScriptThread()} -- mirroring a caller that
    * holds this sandbox's script-execution lock outside an actual script evaluation
    * ({@code @by Bug #77873}).
    */
   private static void forceSerialMode(Object query) throws Exception {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.holdsScriptExecutionLock()).thenReturn(true);
      setField(PreAssetQuery.class, query, "box", box);
   }

   private static void assertOwnFailureMessage(Exception ex) {
      assertInstanceOf(MessageException.class, ex);
      assertFalse(ex.getMessage().contains("warning"),
                  "failure text leaked another task's message: " + ex.getMessage());
      assertEquals(Catalog.getCatalog().getString("common.table.getDataFailed"),
                   ex.getMessage());
   }

   // Bug #78972's own falsifiable claim 1: the cleaned message must not still carry a leaked
   // fully-qualified exception class name.
   private static final Pattern LEAKED_FQCN =
      Pattern.compile("\\b(?:inetsoft|java|javax)\\.[A-Za-z0-9_.$]+(?:Exception|Error)\\b");

   private static void assertNoLeakedClassName(String message) {
      assertFalse(LEAKED_FQCN.matcher(message).find(),
                  "message leaks a Java/inetsoft class name: " + message);
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
