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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80785 (item 3): a query whose select list is fully typed by the parser but whose WHERE /
 * ORDER BY names an unknown column was accepted, because nothing ever touched the datasource.
 * applySqlResultTypes now runs a prepare-only probe in that case and surfaces a driver
 * "unknown column" error through the query's last error, where missingTableRefusal reads it.
 */
@Tag("core")
class SqlUnknownColumnProbe80785Test {
   private static final String DERBY_UNKNOWN_COLUMN =
      "Column 'NO_SUCH_COL_ZZ' is either not in any table in the FROM list or appears " +
      "within a join specification and is outside the scope of the join specification " +
      "or appears in a HAVING clause and is not in the GROUP BY list.";

   // ---- the widened classifier ----

   @Test
   void unknownColumnWordingsOfTheCommonDriversRefuse() {
      for(String msg : List.of(
         "Unknown column 'NO_SUCH_COL_ZZ' in 'where clause'",
         "ORA-00904: \"NO_SUCH_COL_ZZ\": invalid identifier",
         "Invalid column name 'NO_SUCH_COL_ZZ'.",
         "no such column: NO_SUCH_COL_ZZ"))
      {
         String r = WorksheetMutationSupport.missingTableRefusal(
            new SQLException(msg), "SELECT a FROM t WHERE NO_SUCH_COL_ZZ = 1");
         assertNotNull(r, msg);
         assertTrue(r.contains(msg), r);
      }
   }

   @Test
   void genericFailuresAreNotWidenedIntoRefusals() {
      for(String msg : List.of("Invalid parameter value", "Unknown error", "invalid",
                               "Connection refused", "Parameter 1 is not set"))
      {
         assertNull(WorksheetMutationSupport.missingTableRefusal(
            new SQLException(msg), "SELECT a FROM t"), msg);
      }
   }

   // ---- the probe in applySqlResultTypes ----

   @Test
   void unknownWhereColumnOnAFullyTypedSelectSurfacesTheDriverError() throws Exception {
      SQLException error = new SQLException(DERBY_UNKNOWN_COLUMN);
      JDBCQuery query = typedQuery("SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE NO_SUCH_COL_ZZ = 1");
      XRepository repo = mock(XRepository.class);
      when(repo.getMetaData(any(), any(), any(), anyBoolean(), any())).thenThrow(error);

      try(MockedStatic<XRepository> st = repository(repo)) {
         assertEquals(List.of(), run(query));

         assertSame(error, lastError(query));
         String refusal = WorksheetMutationSupport.missingTableRefusal(error, "x");
         assertNotNull(refusal);
         assertTrue(refusal.contains("NO_SUCH_COL_ZZ"), refusal);
         assertFalse(refusal.contains("could not be parsed"), refusal);

         // prepare-only: the execute fallback must never run
         verify(query, never()).getOutputTypeForNonParseableSQL(any(), any(), any());
         verify(repo, never()).execute(any(), any(inetsoft.uql.XQuery.class), any(), any(),
                                       anyBoolean(), any());
      }
   }

   @Test
   void unknownOrderByColumnOnAFullyTypedSelectSurfacesTheDriverError() throws Exception {
      SQLException error = new SQLException("Unknown column 'NO_SUCH_COL_ZZ' in 'order clause'");
      JDBCQuery query = typedQuery("SELECT CATEGORY_ID FROM SA.CATEGORIES ORDER BY NO_SUCH_COL_ZZ");
      XRepository repo = mock(XRepository.class);
      when(repo.getMetaData(any(), any(), any(), anyBoolean(), any())).thenThrow(error);

      try(MockedStatic<XRepository> st = repository(repo)) {
         run(query);
         assertSame(error, lastError(query));
      }
   }

   @Test
   void aResolvableQueryLeavesNoError() throws Exception {
      JDBCQuery query = typedQuery("SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE CATEGORY_ID > 1");
      XRepository repo = mock(XRepository.class);
      when(repo.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenReturn(new XTypeNode("table"));

      try(MockedStatic<XRepository> st = repository(repo)) {
         assertEquals(List.of(), run(query));
         verify(repo).getMetaData(any(), any(), any(), anyBoolean(), any());
         verify(query, never()).setLastQueryError(isA(Exception.class));
      }
   }

   @Test
   void aProbeFailureThatIsNotAnUnknownColumnIsSwallowedAndAccepted() throws Exception {
      SQLException error = new SQLException("Connection reset by peer");
      String sqlText = "SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE CATEGORY_ID > 1";
      JDBCQuery query = typedQuery(sqlText);
      XRepository repo = mock(XRepository.class);
      when(repo.getMetaData(any(), any(), any(), anyBoolean(), any())).thenThrow(error);

      try(MockedStatic<XRepository> st = repository(repo)) {
         assertEquals(List.of(), run(query));
         assertNull(WorksheetMutationSupport.missingTableRefusal(lastError(query), sqlText));
      }
   }

   @Test
   void aProbeThatThrowsAnUncheckedExceptionNeverFailsTheCaller() throws Exception {
      JDBCQuery query = typedQuery("SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE CATEGORY_ID > 1");
      XRepository repo = mock(XRepository.class);
      when(repo.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenThrow(new IllegalStateException("boom"));

      try(MockedStatic<XRepository> st = repository(repo)) {
         assertDoesNotThrow(() -> run(query));
      }
   }

   @Test
   void parameterisedSqlIsNotProbed() throws Exception {
      for(String sql : List.of(
         "SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE NAME = $(name)",
         "SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE NAME = ${name}",
         "SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE NAME = ?"))
      {
         JDBCQuery query = typedQuery(sql);
         XRepository repo = mock(XRepository.class);
         when(repo.getMetaData(any(), any(), any(), anyBoolean(), any()))
            .thenThrow(new SQLException(DERBY_UNKNOWN_COLUMN));

         try(MockedStatic<XRepository> st = repository(repo)) {
            run(query);
            verify(repo, never()).getMetaData(any(), any(), any(), anyBoolean(), any());
            verify(query, never()).setLastQueryError(isA(Exception.class));
         }
      }
   }

   @Test
   void aQueryWithDeclaredVariablesIsNotProbed() throws Exception {
      JDBCQuery query = typedQuery("SELECT CATEGORY_ID FROM SA.CATEGORIES WHERE NAME = x");
      when(query.getVariableNames()).thenReturn(new Vector<>(List.of("name")).elements());
      XRepository repo = mock(XRepository.class);

      try(MockedStatic<XRepository> st = repository(repo)) {
         run(query);
         verify(repo, never()).getMetaData(any(), any(), any(), anyBoolean(), any());
      }
   }

   @Test
   void aMockedQueryWithoutSqlTextIsNeverProbedAndDoesNotNpe() {
      JDBCQuery query = mock(JDBCQuery.class);
      assertDoesNotThrow(() -> run(query));
   }

   // ---- helpers ----

   /** One column CATEGORY_ID, typed by the parser, so the result-metadata call never runs. */
   private static List<String> run(JDBCQuery query) {
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn("CATEGORIES.CATEGORY_ID");
      selection.setType("CATEGORIES.CATEGORY_ID", XSchema.INTEGER);
      selection.setTable("CATEGORIES.CATEGORY_ID", "CATEGORIES");
      UniformSQL sql = mock(UniformSQL.class);
      when(sql.getParseResult()).thenReturn(UniformSQL.PARSE_SUCCESS);
      when(sql.getSelection()).thenReturn(selection);
      when(sql.getTableName("CATEGORIES")).thenReturn("CATEGORIES");
      ColumnSelection columns = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef("CATEGORY_ID"));
      ref.setDataType(XSchema.INTEGER);
      columns.addAttribute(ref);
      return WorksheetMutationSupport.applySqlResultTypes(columns, query, sql, "session");
   }

   private static JDBCQuery typedQuery(String sqlText) {
      JDBCQuery query = mock(JDBCQuery.class);
      when(query.getSQLAsString()).thenReturn(sqlText);
      when(query.getVariableNames()).thenReturn(Collections.emptyEnumeration());
      return query;
   }

   private static MockedStatic<XRepository> repository(XRepository repo) {
      MockedStatic<XRepository> st = mockStatic(XRepository.class);
      st.when(XRepository::getRepository).thenReturn(repo);
      return st;
   }

   private static Exception lastError(JDBCQuery query) {
      ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
      verify(query, atLeastOnce()).setLastQueryError(captor.capture());
      return captor.getValue();
   }
}
