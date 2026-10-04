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
   /**
    * I1: with db.foldUnquotedIdentifiers=false, the twins MyTab and "MyTab" without aliases
    * are one table, as before the fix, also on the ansi from path and in the metadata step.
    */
   @Test
   void optOutTwinsAreOneTable() throws Exception {
      String[] queries = {
         "select mytab.id from MyTab, \"MyTab\"",
         "select \"MyTab\".id, mytab.id as b from MyTab, \"MyTab\"",
      };
      // generated before the fix, without and with the metadata step
      String[][] expected = {
         { "select \"mytab\".\"id\" from \"MyTab\"", "select \"MyTab\".\"id\" from \"MyTab\"" },
         { "select \"MyTab\".\"id\", \"mytab\".\"id\" as \"b\" from \"MyTab\"",
           "select \"MyTab\".\"id\", \"MyTab\".\"id\" as \"b\" from \"MyTab\"" },
      };
      List<Executable> checks = new ArrayList<>();

      for(boolean ansi : new boolean[] { false, true }) {
         for(int i = 0; i < queries.length; i++) {
            String query = queries[i];
            String[] texts = expected[i];
            String xml = toXML(parse(query, source(ansi)));
            List<String> off = withProperty("db.foldUnquotedIdentifiers", "false", () -> List.of(
               regenerate(parse(query, source(ansi))), regenerate(load(xml, source(ansi))),
               fixed(parse(query, source(ansi)), "id"), fixed(load(xml, source(ansi)), "id")));
            String label = "ansi " + ansi + ": " + query;

            checks.add(() -> assertEquals(texts[0], off.get(0), label));
            checks.add(() -> assertEquals(texts[0], off.get(1), label + " xml"));
            checks.add(() -> assertEquals(texts[1], off.get(2), label + " metadata"));
            checks.add(() -> assertEquals(texts[1], off.get(3), label + " xml metadata"));
         }

         // folded, two tables
         String folded = regenerate(parse(queries[0], source(ansi)));
         checks.add(() -> assertEquals("select \"mytab\".\"id\" from \"mytab\", \"MyTab\"", folded,
                                       "ansi " + ansi));
      }

      assertAll(checks);
   }

   /**
    * I2: a qualifier written quoted in a nested query names the table of that query, it isn't
    * generated as the table written unquoted of the outer query.
    */
   @Test
   void quotedQualifierInANestedQueryIsKept() throws Exception {
      for(boolean ansi : new boolean[] { false, true }) {
         String generated = regenerate(parse(
            "select (select max(\"MyTab\".id) from \"MyTab\") as x, id from MyTab", source(ansi)));
         assertTrue(generated.contains("max(\"MyTab\".\"id\") from \"MyTab\""), generated);
         assertTrue(generated.endsWith(" from \"mytab\""), generated);
      }
   }

   /**
    * I3: an order by name written unquoted is the alias only if the alias was written unquoted
    * too. Against a quoted alias it's folded as any name: t.myalias for "MyAlias", the alias
    * for "myalias".
    */
   @Test
   void referenceToAQuotedAliasIsFolded() throws Exception {
      List<Executable> checks = new ArrayList<>();
      String[][] cases = {
         { "select id as \"MyAlias\" from t order by MyAlias desc",
           "select \"id\" as \"MyAlias\" from \"t\" order by \"myalias\" desc" },
         { "select id as \"myalias\" from t order by MyAlias desc",
           "select \"id\" as \"myalias\" from \"t\" order by \"myalias\" desc" },
         { "select s.MyAlias from (select id as \"MyAlias\" from t) s",
           "select \"s\".\"myalias\" from ( select \"id\" as \"MyAlias\" from \"t\") s" },
      };

      for(String[] c : cases) {
         checks.add(() -> assertEquals(c[1], regenerate(parse(c[0], helpers("postgresql"))), c[0]));
         checks.add(() -> assertEquals(c[1], regenerate(load(toXML(parse(c[0], helpers("postgresql"))),
                                                             helpers("postgresql"))), c[0] + " xml"));
      }

      // the metadata step: the column myalias of t, and the alias
      checks.add(() -> {
         String generated = fixed(parse(cases[0][0], helpers("postgresql")), "id", "myalias");
         assertTrue(generated.endsWith("order by \"t\".myalias desc") ||
                    generated.endsWith("order by \"t\".\"myalias\" desc"), generated);
      });
      checks.add(() -> {
         // the alias, or its column, which sorts the same (#6190)
         String generated = fixed(parse(cases[1][0], helpers("postgresql")), "id");
         assertTrue(generated.endsWith("order by \"myalias\" desc") ||
                    generated.endsWith("order by \"id\" desc"), generated);
      });

      assertAll(checks);
   }

   /**
    * I4: the names of a long text are matched without a quadratic cost: the common start and
    * end first, and greedily over a bound.
    */
   @Test
   void longTextsAreMatchedQuickly() {
      StringBuilder stored = new StringBuilder("case");
      StringBuilder generated = new StringBuilder("case");
      int[] names = new int[3000];

      for(int i = 0; i < 3000; i++) {
         stored.append(" when \"MixedCase\" = ").append(i).append(" then ").append(i);
         generated.append(" when \"MixedCase\" = ").append(i).append(" then ").append(i);
         names[i] = i;
      }

      WrittenUnquoted record = new WrittenUnquoted(stored.toString(), names, null);
      String quote = "\"";
      // names folded, and every name of a text generated in another order
      String folded = assertTimeoutPreemptively(java.time.Duration.ofSeconds(30), () ->
         record.apply(generated + " end", quote, n -> n.toLowerCase(), n -> null));
      assertEquals(3000, folded.split("\"mixedcase\"", -1).length - 1);

      String reversed = "\"x\", " + String.join(", ", java.util.Collections.nCopies(3000, "\"MixedCase\""));
      String result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(30), () ->
         record.apply(reversed, quote, n -> n.toLowerCase(), n -> null));
      assertEquals(3000, result.split("\"mixedcase\"", -1).length - 1);
   }

   // a postgresql data source, with ansi joins or not
   private static JDBCDataSource source(boolean ansi) {
      JDBCDataSource ds = helpers("postgresql");
      ds.setAnsiJoin(ansi);
      return ds;
   }
}
