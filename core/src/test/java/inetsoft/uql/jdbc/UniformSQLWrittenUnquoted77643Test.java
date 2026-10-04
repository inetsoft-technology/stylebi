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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static inetsoft.uql.jdbc.UniformSQLUnquotedTwin77643Test.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77643. On PostgreSQL, Snowflake and Exasol a name written unquoted in the sql is
 * folded by the database (lower case on PostgreSQL, upper case on Snowflake and Exasol), but
 * the parser stores it inside quotes in the case it was written, so the regenerated sql named
 * another column ("MixedCase" for MixedCase) or a column or table that doesn't exist. A name
 * written unquoted is now generated in the case the database folds it to, whether or not the
 * metadata step ran, and after the query is saved and loaded. The stored names don't change.
 *
 * The expected sql was checked against PostgreSQL 16 rows on t(id, "MixedCase", mixedcase,
 * myalias), u(id, mixedcase), mytab(id) and "MyTab"(id).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLUnquotedTwin77643Test.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLWrittenUnquoted77643Test {
   /**
    * A name written unquoted is generated folded when the sql is generated without the
    * metadata step (as the data cache normalizer does). The select columns are ordered by
    * their stored text there.
    */
   @Test
   void writtenUnquotedIsFoldedWithoutTheMetadataStep() {
      assertAll(Arrays.stream(SHAPES).map(shape -> (Executable) () ->
         assertEquals(shape[2], regenerate(parse(shape[1], helpers("postgresql"))), shape[0] + ": " + shape[1])));
   }

   /**
    * The names written unquoted are saved with the query, a loaded query is generated as
    * the parsed one.
    */
   @Test
   void writtenUnquotedIsFoldedAfterXmlRoundTrip() {
      assertAll(Arrays.stream(SHAPES).map(shape -> (Executable) () -> {
         String xml = toXML(parse(shape[1], helpers("postgresql")));
         assertEquals(shape[2], regenerate(load(xml, helpers("postgresql"))), shape[0] + ": " + shape[1]);
      }));
   }

   /**
    * The same after the metadata step, in both orders of the twin columns. The metadata step
    * may write a resolved name unquoted in its folded case ("t".mixedcase), so the sql is
    * compared as postgresql reads it.
    */
   @Test
   void writtenUnquotedIsFoldedAfterTheMetadataStepInBothOrders() {
      List<Executable> checks = new ArrayList<>();

      for(boolean twinFirst : new boolean[] { true, false }) {
         for(String[] shape : SHAPES) {
            checks.add(() -> {
               String label = shape[0] + " twin first " + twinFirst + ": " + shape[1];
               String generated = fixed(parse(shape[1], helpers("postgresql")), twinFirst);
               assertEquals(canonical(shape[3], false), canonical(generated, false), label + " => " + generated);
            });
         }
      }

      assertAll(checks);
   }

   /**
    * The unquoted twin of the function operands is folded, the quoted one keeps its case,
    * and the stored text of both stays as it was (the input of vpm scripts and the header of
    * the column).
    */
   @Test
   void functionOperandsKeepTheirStoredText() throws Exception {
      UniformSQL sql = parse("select abs(\"MixedCase\"), abs(MixedCase) as b from t", helpers("postgresql"));
      JDBCSelection select = (JDBCSelection) sql.getSelection();
      assertEquals("abs(\"MixedCase\")", select.getColumn(0));
      assertEquals("abs(\"MixedCase\")", select.getColumn(1));
      assertEquals("select abs(\"MixedCase\"), abs(\"mixedcase\") as \"b\" from \"t\"", regenerate(sql));
   }

   /**
    * Snowflake and exasol fold to upper case. Compared as the database reads the sql.
    */
   @Test
   void snowflakeAndExasolFoldToUpperCase() {
      List<Executable> checks = new ArrayList<>();

      for(String helper : new String[] { "snowflake", "exasol" }) {
         for(String[] shape : UPPER_SHAPES) {
            checks.add(() -> {
               String generated = regenerate(parse(shape[1], helpers(helper)));
               assertEquals(canonical(shape[2], true), canonical(generated, true),
                            helper + " " + shape[0] + ": " + shape[1] + " => " + generated);
            });
         }
      }

      assertAll(checks);
   }

   /**
    * A reference to a select alias is generated as the alias (which keeps the case it's
    * written in), also from a query outside the derived table that has the alias. The labels
    * of the columns don't change. Green before the fix.
    */
   @Test
   void aliasReferencesAreGeneratedAsTheAlias() {
      List<Executable> checks = new ArrayList<>();

      for(String[] shape : ALIAS_SHAPES) {
         checks.add(() -> assertEquals(shape[2], regenerate(parse(shape[1], helpers("postgresql"))),
                                       shape[0] + ": " + shape[1]));
         checks.add(() -> assertEquals(
            shape[2], regenerate(load(toXML(parse(shape[1], helpers("postgresql"))), helpers("postgresql"))),
            shape[0] + " xml: " + shape[1]));

         for(boolean twinFirst : new boolean[] { true, false }) {
            checks.add(() -> assertEquals(shape[3], fixed(parse(shape[1], helpers("postgresql")), twinFirst),
                                          shape[0] + " twin first " + twinFirst + ": " + shape[1]));
         }
      }

      assertAll(checks);
   }

   /**
    * A name the query editor writes inside quotes in the case of its column wasn't written
    * unquoted, so it's that column, without the metadata step, with it and after the query
    * is saved and loaded. Green before the fix.
    */
   @Test
   void editorBuiltQuotedNameIsTheColumnOfItsCase() {
      List<Executable> checks = new ArrayList<>();
      checks.add(() -> {
         UniformSQL sql = parse("select t.id from t", helpers("postgresql"));
         sql.getSelection().addColumn("\"t\".\"MixedCase\"");
         assertEquals("select \"t\".\"MixedCase\", \"t\".\"id\" from \"t\"", regenerate(sql));
         assertEquals("select \"t\".\"MixedCase\", \"t\".\"id\" from \"t\"",
                      regenerate(load(toXML(sql), helpers("postgresql"))));
      });

      for(boolean twinFirst : new boolean[] { true, false }) {
         checks.add(() -> {
            UniformSQL sql = parse("select t.id from t", helpers("postgresql"));
            fix(sql, twinFirst);
            sql.getSelection().addColumn("\"t\".\"MixedCase\"");
            fix(sql, twinFirst);
            String generated = regenerate(sql);
            assertTrue(generated.contains("\"t\".\"MixedCase\""), twinFirst + ": " + generated);
            assertFalse(generated.contains("mixedcase"), twinFirst + ": " + generated);
         });
      }

      assertAll(checks);
   }

   /**
    * The column written unquoted is folded, the one of the same text the editor adds isn't.
    */
   @Test
   void writtenUnquotedIsPerOccurrence() throws Exception {
      UniformSQL sql = parse("select t.MixedCase as a from t", helpers("postgresql"));
      sql.getSelection().addColumn("\"t\".\"MixedCase\"");
      assertEquals("select \"t\".\"mixedcase\" as \"a\", \"t\".\"MixedCase\" from \"t\"", regenerate(sql));
   }

   /**
    * A query saved before the fix doesn't say which names were written unquoted, it's
    * generated as before (the stored xml was written by the code before the fix). Green
    * before the fix.
    */
   @Test
   void querySavedBeforeTheFixIsGeneratedAsBefore() throws Exception {
      Map<String, String> saved = savedBeforeTheFix();
      List<Executable> checks = new ArrayList<>();

      for(String[] shape : SAVED_BEFORE) {
         String xml = saved.get(shape[0]);
         assertNotNull(xml, shape[0]);
         checks.add(() -> assertEquals(shape[1], regenerate(load(xml, helpers("postgresql"))), shape[0]));

         for(boolean twinFirst : new boolean[] { true, false }) {
            checks.add(() -> assertEquals(shape[2], fixed(load(xml, helpers("postgresql")), twinFirst),
                                          shape[0] + " twin first " + twinFirst));
         }
      }

      assertAll(checks);
   }

   /**
    * A query saved by v1.1.0 (no quoted flags, every name in quotes in its stored case) is
    * generated as before. Green before the fix.
    */
   @Test
   void querySavedByVersion110IsGeneratedAsBefore() throws Exception {
      String xml = savedBeforeTheFix().get("V110");
      assertNotNull(xml);
      String before = "select \"m\".\"id\", \"t\".\"MixedCase\" as \"b\" " +
         "from \"MyTab\" m, \"t\" where \"t\".\"MixedCase\" > 1 group by \"m\".\"id\", \"t\".\"MixedCase\" " +
         "order by \"t\".\"MixedCase\" desc";
      assertEquals(before, regenerate(load(xml, helpers("postgresql"))));
      assertEquals(before, regenerate(load(toXML(load(xml, helpers("postgresql"))), helpers("postgresql"))));

      for(boolean twinFirst : new boolean[] { true, false }) {
         assertEquals(before, fixed(load(xml, helpers("postgresql")), twinFirst), "twin first " + twinFirst);
      }
   }

   /**
    * db.foldUnquotedIdentifiers=false generates the sql as before the fix, also for a query
    * parsed and saved with the folding on. Green before the fix.
    */
   @Test
   void optOutGeneratesTheSqlAsBefore() throws Exception {
      Map<String, String> xmls = new HashMap<>();

      for(String[] shape : SHAPES) {
         xmls.put(shape[0], toXML(parse(shape[1], helpers("postgresql"))));
      }

      withProperty("db.foldUnquotedIdentifiers", "false", () -> {
         List<Executable> checks = new ArrayList<>();

         for(String[] shape : SHAPES) {
            checks.add(() -> assertEquals(shape[4], regenerate(parse(shape[1], helpers("postgresql"))),
                                          shape[0] + ": " + shape[1]));
            checks.add(() -> assertEquals(shape[4], regenerate(load(xmls.get(shape[0]), helpers("postgresql"))),
                                          shape[0] + " xml: " + shape[1]));
         }

         assertAll(checks);
         return null;
      });
   }

   // id, sql, generated without the metadata step, generated after it, generated before the fix
   private static final String[][] SHAPES = {
      { "S1", "select \"MixedCase\", MixedCase as b from t",
        "select \"mixedcase\" as \"b\", \"MixedCase\" from \"t\"",
        "select \"mixedcase\" as \"b\", \"t\".\"MixedCase\" from \"t\"",
        "select \"MixedCase\" as \"b\", \"MixedCase\" from \"t\"" },
      { "S1q", "select t.\"MixedCase\", t.MixedCase as b from t",
        "select \"t\".\"mixedcase\" as \"b\", \"t\".\"MixedCase\" from \"t\"",
        "select \"t\".\"MixedCase\", \"t\".\"mixedcase\" as \"b\" from \"t\"",
        "select \"t\".\"MixedCase\" as \"b\", \"t\".\"MixedCase\" from \"t\"" },
      { "S2", "select MixedCase from u",
        "select \"mixedcase\" from \"u\"",
        "select \"mixedcase\" from \"u\"",
        "select \"MixedCase\" from \"u\"" },
      { "S3", "select MixedCase, count(*) from t group by MixedCase",
        "select \"mixedcase\", count(*) from \"t\" group by \"mixedcase\"",
        "select \"mixedcase\", count(*) from \"t\" group by \"mixedcase\"",
        "select \"MixedCase\", count(*) from \"t\" group by \"MixedCase\"" },
      { "S4", "select id from t order by MixedCase desc",
        "select \"id\" from \"t\" order by \"mixedcase\" desc",
        "select \"id\" from \"t\" order by \"t\".mixedcase desc",
        "select \"id\" from \"t\" order by \"MixedCase\" desc" },
      { "S4q", "select t.id, t.MixedCase from t order by t.MixedCase desc",
        "select \"t\".\"mixedcase\", \"t\".\"id\" from \"t\" order by \"t\".\"mixedcase\" desc",
        "select \"t\".\"id\", \"t\".\"mixedcase\" from \"t\" order by \"t\".\"mixedcase\" desc",
        "select \"t\".\"MixedCase\", \"t\".\"id\" from \"t\" order by \"t\".\"MixedCase\" desc" },
      { "S5", "select id from t where MixedCase = 1",
        "select \"id\" from \"t\" where \"mixedcase\" = 1",
        "select \"id\" from \"t\" where \"mixedcase\" = 1",
        "select \"id\" from \"t\" where \"MixedCase\" = 1" },
      { "S6", "select abs(\"MixedCase\"), abs(MixedCase) as b from t",
        "select abs(\"MixedCase\"), abs(\"mixedcase\") as \"b\" from \"t\"",
        "select abs(\"MixedCase\"), abs(\"mixedcase\") as \"b\" from \"t\"",
        "select abs(\"MixedCase\"), abs(\"MixedCase\") as \"b\" from \"t\"" },
      { "S7a", "select id from MyTab",
        "select \"id\" from \"mytab\"",
        "select \"id\" from \"mytab\"",
        "select \"id\" from \"MyTab\"" },
      { "S7", "select a.id, b.id as bid from MyTab a, \"MyTab\" b",
        "select \"a\".\"id\", \"b\".\"id\" as \"bid\" from \"mytab\" a, \"MyTab\" b",
        "select \"a\".\"id\", \"b\".\"id\" as \"bid\" from \"mytab\" a, \"MyTab\" b",
        "select \"a\".\"id\", \"b\".\"id\" as \"bid\" from \"MyTab\" a, \"MyTab\" b" },
      { "S7c", "select mytab.id from MyTab, \"MyTab\"",
        "select \"mytab\".\"id\" from \"mytab\", \"MyTab\"",
        "select \"mytab\".\"id\" from \"mytab\", \"MyTab\"",
        "select \"mytab\".\"id\" from \"MyTab\"" },
      { "S8", "select MyT.id from t MyT where MyT.MixedCase > 1",
        "select \"myt\".\"id\" from \"t\" MyT where \"myt\".\"mixedcase\" > 1",
        "select \"myt\".\"id\" from \"t\" MyT where \"myt\".\"mixedcase\" > 1",
        "select \"MyT\".\"id\" from \"t\" MyT where \"MyT\".\"MixedCase\" > 1" },
      { "S13", "select s.MixedCase from (select t.MixedCase from t) s",
        "select \"s\".\"mixedcase\" from ( select \"t\".\"mixedcase\" from \"t\") s",
        "select \"s\".\"mixedcase\" from ( select \"t\".\"mixedcase\" from \"t\") s",
        "select \"s\".\"MixedCase\" from ( select \"t\".\"MixedCase\" from \"t\") s" },
   };

   // id, sql, generated on snowflake and exasol without the metadata step
   private static final String[][] UPPER_SHAPES = {
      { "S1", "select \"MixedCase\", MixedCase as b from t", "select MIXEDCASE as b, \"MixedCase\" from T" },
      { "S2", "select MixedCase from u", "select MIXEDCASE from U" },
      { "S5", "select id from t where MixedCase = 1", "select ID from T where MIXEDCASE = 1" },
      { "S4q", "select t.id, t.MixedCase from t order by t.MixedCase desc",
        "select T.MIXEDCASE, T.ID from T order by T.MIXEDCASE desc" },
      { "S9", "select id as MyAlias from t order by MyAlias desc",
        "select ID as MyAlias from T order by MyAlias desc" },
   };

   // id, sql, generated without the metadata step and after it (postgresql, as before the fix)
   private static final String[][] ALIAS_SHAPES = {
      { "S9", "select id as MyAlias from t order by MyAlias desc",
        "select \"id\" as \"MyAlias\" from \"t\" order by \"MyAlias\" desc",
        "select \"id\" as \"MyAlias\" from \"t\" order by \"MyAlias\" desc" },
      { "S10", "select id as MyAlias, count(*) from u group by MyAlias",
        "select \"id\" as \"MyAlias\", count(*) from \"u\" group by \"MyAlias\"",
        "select \"id\" as \"MyAlias\", count(*) from \"u\" group by \"MyAlias\"" },
      { "S11", "select s.MyAlias from (select id as MyAlias from t) s order by s.MyAlias desc",
        "select \"s\".\"MyAlias\" from ( select \"id\" as \"MyAlias\" from \"t\") s order by \"s\".\"MyAlias\" desc",
        "select \"s\".\"MyAlias\" from ( select \"id\" as \"MyAlias\" from \"t\") s order by \"s\".\"MyAlias\" desc" },
      { "S12", "select MyAlias from (select id as MyAlias from t) s",
        "select \"MyAlias\" from ( select \"id\" as \"MyAlias\" from \"t\") s",
        "select \"MyAlias\" from ( select \"id\" as \"MyAlias\" from \"t\") s" },
      { "S12b", "select * from (select id as MyAlias from t) s",
        "select * from ( select \"id\" as \"MyAlias\" from \"t\") s",
        "select s.\"MyAlias\" from ( select \"id\" as \"MyAlias\" from \"t\") s" },
      { "S14", "select id + 1 as MyAlias from t order by MyAlias desc",
        "select \"id\"+1 as \"MyAlias\" from \"t\" order by \"MyAlias\" desc",
        "select \"id\"+1 as \"MyAlias\" from \"t\" order by \"MyAlias\" desc" },
   };

   // id in the saved xml, generated without the metadata step and after it
   private static final String[][] SAVED_BEFORE = {
      { "S1", "select \"MixedCase\" as \"b\", \"MixedCase\" from \"t\"",
        "select \"MixedCase\" as \"b\", \"t\".\"MixedCase\" from \"t\"" },
      { "S2", "select \"MixedCase\" from \"u\"", "select \"MixedCase\" from \"u\"" },
      { "S3", "select \"MixedCase\", count(*) from \"t\" group by \"MixedCase\"",
        "select \"MixedCase\", count(*) from \"t\" group by \"MixedCase\"" },
      { "S4", "select \"id\" from \"t\" order by \"MixedCase\" desc",
        "select \"id\" from \"t\" order by \"t\".mixedcase desc" },
      { "S5", "select \"id\" from \"t\" where \"MixedCase\" = 1", "select \"id\" from \"t\" where \"MixedCase\" = 1" },
      { "S6", "select abs(\"MixedCase\"), abs(\"MixedCase\") as \"b\" from \"t\"",
        "select abs(\"MixedCase\"), abs(\"MixedCase\") as \"b\" from \"t\"" },
      { "S7a", "select \"id\" from \"MyTab\"", "select \"id\" from \"MyTab\"" },
      { "S7", "select \"a\".\"id\", \"b\".\"id\" as \"bid\" from \"MyTab\" a, \"MyTab\" b",
        "select \"a\".\"id\", \"b\".\"id\" as \"bid\" from \"MyTab\" a, \"MyTab\" b" },
      { "S7c", "select \"mytab\".\"id\" from \"MyTab\"", "select \"MyTab\".\"id\" from \"MyTab\"" },
      { "S8", "select \"MyT\".\"id\" from \"t\" MyT where \"MyT\".\"MixedCase\" > 1",
        "select \"MyT\".\"id\" from \"t\" MyT where \"MyT\".\"MixedCase\" > 1" },
      { "S9", "select \"id\" as \"MyAlias\" from \"t\" order by \"MyAlias\" desc",
        "select \"id\" as \"MyAlias\" from \"t\" order by \"MyAlias\" desc" },
      { "S13", "select \"s\".\"MixedCase\" from ( select \"t\".\"MixedCase\" from \"t\") s",
        "select \"s\".\"MixedCase\" from ( select \"t\".\"MixedCase\" from \"t\") s" },
      { "Q", "select \"t\".\"MixedCase\", \"t\".\"id\" from \"t\" order by \"t\".\"MixedCase\" desc",
        "select \"t\".\"MixedCase\", \"t\".\"id\" from \"t\" order by \"t\".\"MixedCase\" desc" },
   };

   // the xml of each query, saved by the code before the fix
   private static Map<String, String> savedBeforeTheFix() throws Exception {
      Map<String, String> xmls = new HashMap<>();

      try(InputStream input = UniformSQLWrittenUnquoted77643Test.class.getResourceAsStream(
         "UniformSQLWrittenUnquoted77643Test-prefix.xml"))
      {
         Document doc = Tool.parseXML(input);
         NodeList cases = doc.getElementsByTagName("case");

         for(int i = 0; i < cases.getLength(); i++) {
            Element element = (Element) cases.item(i);
            Element sql = Tool.getChildNodeByTagName(element, "uniform_sql");
            UniformSQL loaded = new UniformSQL();
            loaded.parseXML(sql);
            xmls.put(element.getAttribute("id"), toXML(loaded));
         }
      }

      return xmls;
   }

   /**
    * The sql as the database reads it: a name in quotes in the folded case is unquoted, and
    * a name not in quotes is folded.
    */
   static String canonical(String sql, boolean upper) {
      StringBuilder result = new StringBuilder();
      Matcher matcher = Pattern.compile("\"([^\"]*)\"|([^\"]+)").matcher(sql);
      Pattern folded = Pattern.compile(upper ? "[A-Z_][A-Z0-9_$]*" : "[a-z_][a-z0-9_$]*");

      while(matcher.find()) {
         String name = matcher.group(1);

         if(name != null) {
            result.append(folded.matcher(name).matches() ? name : "\"" + name + "\"");
         }
         else {
            String text = matcher.group(2);
            result.append(upper ? text.toUpperCase(Locale.ROOT) : text.toLowerCase(Locale.ROOT));
         }
      }

      return result.toString();
   }

   // the regenerated sql after the metadata step
   private static String fixed(UniformSQL sql, boolean twinFirst) throws Exception {
      fix(sql, twinFirst);
      return regenerate(sql);
   }

   private static void fix(UniformSQL sql, boolean twinFirst) throws Exception {
      // a derived table gets the data source when the sql is generated
      regenerate(sql);
      // the table metadata is cached by data source, use another one
      JDBCDataSource ds = (JDBCDataSource) sql.getDataSource().clone();
      ds.setName(ds.getName() + "_w" + (++sources));
      sql.setDataSource(ds);
      JDBCUtil.fixUniformSQLInfo(sql, repository(twinFirst), null, ds);
   }

   // the columns of each table of postgresql, in the stored case
   private static XRepository repository(boolean twinFirst) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         String[] columns;

         switch(mtype.getName().replace("\"", "")) {
         case "t":
            columns = twinFirst ? new String[] { "id", "MixedCase", "mixedcase", "myalias" } :
               new String[] { "id", "mixedcase", "MixedCase", "myalias" };
            break;
         case "u":
            columns = new String[] { "id", "mixedcase" };
            break;
         default:
            columns = new String[] { "id" };
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   private static int sources;
}
