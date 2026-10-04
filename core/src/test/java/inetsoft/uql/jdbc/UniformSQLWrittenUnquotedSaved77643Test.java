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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static inetsoft.uql.jdbc.UniformSQLUnquotedTwin77643Test.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77643. The text of a searched case is built when the sql is parsed, it keeps the names
 * as written (the stored text doesn't depend on db.foldUnquotedIdentifiers), and its names
 * written unquoted are folded when it's generated, not the ones written quoted. A query saved
 * and loaded is resolved by the metadata step with the names written unquoted it saved.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLUnquotedTwin77643Test.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLWrittenUnquotedSaved77643Test {
   /**
    * B4: the quoted then operand keeps its case, the unquoted when and else operands are
    * folded. Stored as before the fix, also when parsed with the folding off.
    */
   @Test
   void searchedCaseFoldsOnlyTheNamesWrittenUnquoted() throws Exception {
      String query = "select case when MixedCase > 2 then \"MixedCase\" else MixedCase end as v from t";
      String stored = "case when \"MixedCase\" > 2 then \"MixedCase\" else \"MixedCase\" END";
      String folded = "select case when \"mixedcase\" > 2 then \"MixedCase\" else \"mixedcase\" END as \"v\" from \"t\"";
      String before = "select " + stored + " as \"v\" from \"t\"";

      assertCase(query, stored, folded, before);
   }

   /**
    * C1: a searched case of one name written unquoted.
    */
   @Test
   void searchedCaseOfOneName() throws Exception {
      String query = "select case when MixedCase > 2 then 1 else 0 end from t";
      String stored = "case when \"MixedCase\" > 2 then 1 else 0 END";

      assertCase(query, stored, "select case when \"mixedcase\" > 2 then 1 else 0 END from \"t\"",
                 "select " + stored + " from \"t\"");
   }

   private static void assertCase(String query, String stored, String folded, String before)
      throws Exception
   {
      List<Executable> checks = new ArrayList<>();
      UniformSQL sql = parse(query, helpers("postgresql"));
      String xml = toXML(sql);

      checks.add(() -> assertEquals(stored, sql.getSelection().getColumn(0)));
      checks.add(() -> assertEquals(folded, regenerate(sql)));
      checks.add(() -> assertEquals(folded, regenerate(load(xml, helpers("postgresql")))));

      // generated with the folding off, the text before the fix
      List<String> off = withProperty("db.foldUnquotedIdentifiers", "false", () -> {
         UniformSQL parsed = parse(query, helpers("postgresql"));
         // saved with the folding on, loaded with it off
         return List.of(parsed.getSelection().getColumn(0), regenerate(parsed),
                        regenerate(load(xml, helpers("postgresql"))));
      });

      checks.add(() -> assertEquals(stored, off.get(0)));
      checks.add(() -> assertEquals(before, off.get(1)));
      checks.add(() -> assertEquals(before, off.get(2)));
      assertAll(checks);
   }

   /**
    * A query saved and loaded is resolved by the metadata step (at runtime, by JDBCAgent and
    * SQLBoundTableAssembly) as the query parsed: the qualified column written unquoted is the
    * column of the folded case, in both orders of the twins.
    */
   @Test
   void savedQueryIsResolvedAsTheParsedOne() throws Exception {
      String[] queries = {
         "select t.\"MixedCase\", t.MixedCase as b from t",
         "select t.id, t.MixedCase from t order by t.MixedCase desc",
         "select t.id from t where t.MixedCase = 1",
         "select t.MixedCase, count(*) from t group by t.MixedCase",
      };
      List<Executable> checks = new ArrayList<>();

      for(String[] columns : new String[][] { PG_TWIN_FIRST, PG_TWIN_SECOND }) {
         for(String query : queries) {
            String parsed = fixed(parse(query, helpers("postgresql")), columns);
            String xml = toXML(parse(query, helpers("postgresql")));
            String loaded = fixed(load(xml, helpers("postgresql")), columns);

            checks.add(() -> assertEquals(parsed, loaded, columns[1] + ": " + query));
            checks.add(() -> assertTrue(loaded.contains("\"t\".mixedcase") ||
                                        loaded.contains("\"t\".\"mixedcase\""),
                                        columns[1] + ": " + loaded));
         }
      }

      assertAll(checks);
   }
}
