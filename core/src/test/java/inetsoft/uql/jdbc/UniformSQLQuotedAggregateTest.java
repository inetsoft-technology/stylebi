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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77578, a quoted column that is the only argument of an aggregate
 * ({@code sum(q."MixedCase")}) keeps its quotes and its case in the select list and the
 * order by of the SQL regenerated from the parsed {@link UniformSQL}, on every SQL helper.
 * On PostgreSQL, Snowflake and Exasol the parser quotes every segment, so the stored text of
 * {@code sum(q."MixedCase")} and {@code sum(q.MixedCase)} is the same, and
 * {@code SQLHelper.getValidAggregate} gave both the metadata case repair of an unquoted name
 * (the first column ignoring case, e.g. MIXEDCASE). The parser now records the quoted column
 * of the aggregate by select position (and by order by field), so two aggregates of the same
 * column with different spellings don't share it. An aggregate without the record (an
 * unquoted one, or a query saved before this change) is generated as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLQuotedAggregateTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLQuotedAggregateTest {
   private static final String SELECT = "select q.id, sum(q.\"MixedCase\") from t q group by q.id";
   private static final String UNQUOTED = "select q.id, sum(q.MixedCase) from t q group by q.id";

   @Test
   void reportedShapeKeepsTheQuotedColumn() throws Exception {
      String pg = "select \"q\".\"id\", sum(q.\"MixedCase\") from \"t\" q group by \"q\".\"id\"";

      // was sum(q."MIXEDCASE"), another column
      assertEquals(pg, aggregate("postgresql", SELECT, TWIN_FIRST));
      assertEquals(pg, aggregate("postgresql", SELECT, TWIN_SECOND));
      assertEquals(pg, aggregate("postgresql", SELECT, "mixedcase", "MixedCase", "id"));
      assertEquals(pg, aggregate("postgresql", SELECT));

      // was sum(q.MixedCase) or sum(q.MIXEDCASE), unquoted, which the database folds to MIXEDCASE
      for(String key : new String[] { "snowflake", "exasol" }) {
         assertEquals(upper(pg), aggregate(key, SELECT, TWIN_FIRST), key);
         assertEquals(upper(pg), aggregate(key, SELECT, TWIN_SECOND), key);
         assertEquals(upper(pg), aggregate(key, SELECT, "MixedCase", "id"), key);
         assertEquals(upper(pg), aggregate(key, SELECT), key);
      }

      // the parser records the column of the aggregate, the stored text is unchanged
      UniformSQL sql = parse(SELECT, source("postgresql"));
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertEquals("sum(\"q\".\"MixedCase\")", selection.getColumn(1));
      assertEquals(selection.getColumn(1),
                   parse(UNQUOTED, source("postgresql")).getSelection().getColumn(1));
      assertEquals("MixedCase", selection.getQuotedAggregate(1));
      assertNull(selection.getQuotedAggregate(0));
      assertFalse(selection.isQuoted(selection.getColumn(1)));
   }

   @Test
   void everyHelperKeepsTheQuotedColumn() throws Exception {
      String[] queries = {
         SELECT,
         "select q.id, count(distinct q.\"MixedCase\") from t q group by q.id",
         SELECT + " order by sum(q.\"MixedCase\")",
         "select q.id from t q group by q.id order by sum(q.\"MixedCase\")",
         "select sum(\"my q\".\"MixedCase\") from t \"my q\"",
         "select sum(\"order\".\"MixedCase\") from t \"order\"",
      };
      Pattern quoted = Pattern.compile("\\((distinct )?\"?[a-z ]+\"?\\.\"MixedCase\"\\)");
      Pattern other = Pattern.compile("MIXEDCASE|(?<!\")MixedCase");

      // the aggregates of each query in the generated sql, -1 if only in the order by
      int[] counts = { 1, 1, 2, -1, 1, 1 };

      for(String key : helpers().keySet()) {
         for(int q = 0; q < queries.length; q++) {
            String query = queries[q];
            String[][] metadata = key.equals("default") ? new String[][] { {} } :
               new String[][] { {}, TWIN_FIRST, TWIN_SECOND };

            for(String[] columns : metadata) {
               UniformSQL sql = fixed(key, query, columns);
               String generated = regenerate(sql);
               String label = key + " " + Arrays.toString(columns) + ": " + query + " -> " + generated;

               // an order by aggregate not in the select list is dropped by the metadata step,
               // before and after this change
               int expected = counts[q] < 0 ? (columns.length > 0 ? 0 : 1) : counts[q];

               assertEquals(expected, count(quoted, generated), label);
               assertFalse(other.matcher(generated).find(), label);
            }
         }
      }
   }

   /**
    * The output of an unquoted aggregate is the output before this change on every helper,
    * including the metadata case repair to the first column ignoring case.
    */
   @Test
   void unquotedAggregatesAreUnchanged() throws Exception {
      String pg = "select \"q\".\"id\", sum(q.\"%s\") from \"t\" q group by \"q\".\"id\"";
      String folding = "select \"q\".\"id\", sum(q.%s) from \"t\" q group by \"q\".\"id\"";

      assertEquals(String.format(pg, "MIXEDCASE"), aggregate("postgresql", UNQUOTED, TWIN_FIRST));
      // the metadata spelling is the written one, kept as written: postgresql reads MixedCase
      // written unquoted as mixedcase (Bug #77643)
      assertEquals(String.format(pg, "mixedcase"), aggregate("postgresql", UNQUOTED, TWIN_SECOND));
      assertEquals(String.format(pg, "mixedcase"), aggregate("postgresql", UNQUOTED, "mixedcase", "id"));
      // without the metadata step, the column postgresql reads for MixedCase written unquoted
      // (Bug #77643)
      assertEquals(String.format(pg, "mixedcase"), aggregate("postgresql", UNQUOTED));

      // snowflake and exasol read q, id and t written unquoted as Q, ID and T (Bug #77643)
      for(String key : new String[] { "snowflake", "exasol" }) {
         assertEquals(upper(String.format(folding, "MIXEDCASE")), aggregate(key, UNQUOTED, TWIN_FIRST), key);
         assertEquals(upper(String.format(folding, "MixedCase")), aggregate(key, UNQUOTED, TWIN_SECOND), key);
         assertEquals(upper(String.format(folding, "MixedCase")), aggregate(key, UNQUOTED), key);
         assertEquals(upper(String.format(folding, "MIXEDCASE")) + " order by sum(q.MIXEDCASE) asc",
                      aggregate(key, UNQUOTED + " order by sum(q.MixedCase)", TWIN_FIRST), key);
      }

      assertEquals("select q.id, sum(q.MIXEDCASE) from t q group by q.id", aggregate("h2", UNQUOTED, TWIN_FIRST));
      assertEquals("select q.id, sum(q.MixedCase) from t q group by q.id", aggregate("h2", UNQUOTED, TWIN_SECOND));
      // was sum(q."MIXEDCASE"), an unquoted column is quoted on oracle only if needed (#77646)
      assertEquals("select q.id, sum(q.MIXEDCASE) from T q group by q.id",
                   aggregate("oracle", UNQUOTED, TWIN_FIRST));
      assertEquals("select q.id, sum(q.MixedCase) from t q group by q.id", aggregate("default", UNQUOTED));

      for(String key : helpers().keySet()) {
         UniformSQL sql = parse(UNQUOTED + " order by sum(q.MixedCase)", source(key));
         assertNull(((JDBCSelection) sql.getSelection()).getQuotedAggregate(1), key);
         assertNull(sql.getQuotedAggregate(sql.getOrderByFields()[0]), key);
      }
   }

   /**
    * sum(q.MixedCase) and sum(q."MixedCase") are stored with the same text on PostgreSQL,
    * Snowflake and Exasol, the record is kept by position so each keeps its own spelling.
    */
   @Test
   void bothSpellingsOfOneColumnKeepTheirOwnQuotes() throws Exception {
      String query = "select q.id, sum(q.MixedCase) a, sum(q.\"MixedCase\") b from t q group by q.id";

      assertEquals("select \"q\".\"id\", sum(q.\"MIXEDCASE\") as \"a\", sum(q.\"MixedCase\") as \"b\" from \"t\" q " +
                   "group by \"q\".\"id\"", aggregate("postgresql", query, TWIN_FIRST));
      assertEquals(upper("select \"q\".\"id\", sum(q.MIXEDCASE) as a, sum(q.\"MixedCase\") as b from \"t\" q " +
                   "group by \"q\".\"id\""), aggregate("snowflake", query, TWIN_FIRST));
      assertEquals(upper("select \"q\".\"id\", sum(q.MixedCase) as a, sum(q.\"MixedCase\") as b from \"t\" q " +
                   "group by \"q\".\"id\""), aggregate("exasol", query, TWIN_SECOND));
      assertEquals("select q.id, sum(q.\"MixedCase\") as b, sum(q.MIXEDCASE) as a from t q group by q.id",
                   aggregate("h2", query, TWIN_FIRST));

      UniformSQL sql = parse(query, source("postgresql"));
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertEquals(selection.getColumn(1), selection.getColumn(2));
      assertNull(selection.getQuotedAggregate(1));
      assertEquals("MixedCase", selection.getQuotedAggregate(2));
   }

   @Test
   void orderByKeepsTheQuotedColumn() throws Exception {
      String pg = "select \"q\".\"id\", sum(q.\"%s\") from \"t\" q group by \"q\".\"id\" order by sum(q.\"%s\") asc";

      assertEquals(String.format(pg, "MixedCase", "MixedCase"),
                   aggregate("postgresql", SELECT + " order by sum(q.\"MixedCase\")", TWIN_FIRST));
      // the select list and the order by are recorded separately
      assertEquals(String.format(pg, "MIXEDCASE", "MixedCase"),
                   aggregate("postgresql", UNQUOTED + " order by sum(q.\"MixedCase\")", TWIN_FIRST));
      assertEquals(String.format(pg, "MixedCase", "MIXEDCASE"),
                   aggregate("postgresql", SELECT + " order by sum(q.MixedCase)", TWIN_FIRST));
      assertEquals(upper("select \"q\".\"id\", sum(q.\"MixedCase\") from \"t\" q group by \"q\".\"id\" " +
                   "order by sum(q.\"MixedCase\") asc"),
                   aggregate("snowflake", SELECT + " order by sum(q.\"MixedCase\")", TWIN_SECOND));
      assertEquals(upper("select \"q\".\"id\" from \"t\" q group by \"q\".\"id\" order by sum(q.\"MixedCase\") asc"),
                   aggregate("exasol", "select q.id from t q group by q.id order by sum(q.\"MixedCase\")"));
      // an order by alias is generated as the alias, as before
      assertEquals("select \"q\".\"id\", sum(q.\"MixedCase\") as \"s\" from \"t\" q group by \"q\".\"id\" " +
                   "order by \"s\" asc",
                   aggregate("postgresql", "select q.id, sum(q.\"MixedCase\") s from t q group by q.id order by s"));

      UniformSQL sql = parse(UNQUOTED + " order by sum(q.\"MixedCase\")", source("postgresql"));
      assertEquals("MixedCase", sql.getQuotedAggregate(sql.getOrderByFields()[0]));
      assertNull(((JDBCSelection) sql.getSelection()).getQuotedAggregate(1));
   }

   /**
    * HAVING doesn't go through getValidAggregate, it keeps the parsed text, unchanged by this
    * fix. The unquoted spelling was quoted there on PostgreSQL, Snowflake and Exasol, a
    * pre-existing defect out of the scope of #77578. Since #77643 it is generated in the case
    * the database reads it.
    */
   @Test
   void havingIsUnchanged() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         boolean pg = "postgresql".equals(key);

         for(String arg : new String[] { "q.\"MixedCase\"", "q.MixedCase" }) {
            String query = "select q.id from t q group by q.id having sum(" + arg + ") > 0";
            String column = arg.contains("\"") ? "MixedCase" : pg ? "mixedcase" : "MIXEDCASE";
            String expected = "select \"q\".\"id\" from \"t\" q group by \"q\".\"id\" having sum(\"q\".\"" +
               column + "\") > 0";
            expected = pg ? expected : upper(expected);
            assertEquals(expected, aggregate(key, query), key);
            assertEquals(expected, aggregate(key, query, TWIN_FIRST), key);
         }
      }

      assertEquals("select q.id from t q group by q.id having sum(q.\"MixedCase\") > 0",
                   aggregate("h2", "select q.id from t q group by q.id having sum(q.\"MixedCase\") > 0", TWIN_FIRST));
      assertEquals("select q.id from t q group by q.id having sum(q.MixedCase) > 0",
                   aggregate("h2", "select q.id from t q group by q.id having sum(q.MixedCase) > 0", TWIN_FIRST));
   }

   @Test
   void qualifierShapes() throws Exception {
      // a special qualifier, also on helpers that keep the text of a name (was MIXEDCASE)
      assertEquals("select sum(\"my q\".\"MixedCase\") from t \"my q\"",
                   aggregate("h2", "select sum(\"my q\".\"MixedCase\") from t \"my q\"", TWIN_FIRST));
      assertEquals("select sum(\"my q\".\"MixedCase\") from T \"my q\"",
                   aggregate("oracle", "select sum(\"my q\".\"MixedCase\") from t \"my q\"", TWIN_FIRST));
      assertEquals("select sum(\"my q\".\"MixedCase\") from \"t\" \"my q\"",
                   aggregate("postgresql", "select sum(\"my q\".\"MixedCase\") from t \"my q\"", TWIN_FIRST));
      // unaliased, already correct before this change
      assertEquals("select sum(\"t\".\"MixedCase\") from \"t\"",
                   aggregate("postgresql", "select sum(t.\"MixedCase\") from t", TWIN_FIRST));
      assertEquals("select sum(t.\"MixedCase\") from T t",
                   aggregate("oracle", "select sum(t.\"MixedCase\") from t", TWIN_FIRST));
      // a derived table, the subquery keeps both columns
      assertEquals("select sum(q.\"MixedCase\") from ( select \"MIXEDCASE\", \"T\".\"MixedCase\" from \"T\") q",
                   aggregate("snowflake", "select sum(q.\"MixedCase\") from (select \"MixedCase\", MIXEDCASE from t) q",
                             TWIN_FIRST));
      // a bare quoted argument isn't recorded, it keeps its text as before
      UniformSQL sql = parse("select sum(\"MixedCase\") from t", source("postgresql"));
      assertNull(((JDBCSelection) sql.getSelection()).getQuotedAggregate(0));
      assertEquals("select sum(\"MixedCase\") from \"t\"", regenerate(sql));
   }

   /**
    * Only an aggregate that is the whole select column, with one quoted column as its only
    * argument, is recorded. Other expressions keep their parsed text, as before.
    */
   @Test
   void onlyAWholeAggregateOfOneColumnIsRecorded() throws Exception {
      String[] queries = {
         "select sum(q.\"MixedCase\") + 1 from t q",
         "select round(sum(q.\"MixedCase\")) from t q",
         "select coalesce(q.\"MixedCase\", 0) from t q",
         "select max(q.\"MixedCase\" + 1) from t q",
         "select q.\"MixedCase\" from t q",
      };

      for(String key : helpers().keySet()) {
         for(String query : queries) {
            UniformSQL sql = parse(query, source(key));
            assertNull(((JDBCSelection) sql.getSelection()).getQuotedAggregate(0), key + ": " + query);
         }

         UniformSQL sql = parse("select q.id, sum(q.\"MixedCase\") from t q group by q.id " +
                                "order by sum(q.\"MixedCase\") + 1", source(key));
         assertEquals("MixedCase", ((JDBCSelection) sql.getSelection()).getQuotedAggregate(1), key);
         assertNull(sql.getQuotedAggregate(sql.getOrderByFields()[0]), key);
      }
   }

   @Test
   void xmlRoundTripKeepsTheRecord() throws Exception {
      String query = "select q.id, sum(q.MixedCase) a, sum(q.\"MixedCase\") b from t q group by q.id " +
         "order by sum(q.\"MixedCase\")";

      for(String key : helpers().keySet()) {
         JDBCDataSource ds = source(key);
         UniformSQL sql = parse(query, ds);
         String xml = toXML(sql);

         assertEquals(1, count(xml, "<quotedAggregate column=\"MixedCase\"/>"), key + ": " + xml);
         assertEquals(1, count(xml, " quotedAggregate=\"MixedCase\""), key + ": " + xml);

         UniformSQL loaded = load(xml, ds);
         assertNull(((JDBCSelection) loaded.getSelection()).getQuotedAggregate(1), key);
         assertEquals("MixedCase", ((JDBCSelection) loaded.getSelection()).getQuotedAggregate(2), key);
         assertEquals("MixedCase", loaded.getQuotedAggregate(loaded.getOrderByFields()[0]), key);
         assertEquals(regenerate(parse(query, ds)), regenerate(loaded), key);
         assertEquals(sql.getSelection(), loaded.getSelection(), key);
      }

      // after the metadata step
      UniformSQL sql = fixed("postgresql", query, TWIN_FIRST);
      String expected = regenerate(sql);
      assertEquals("select \"q\".\"id\", sum(q.\"MIXEDCASE\") as \"a\", sum(q.\"MixedCase\") as \"b\" from \"t\" q " +
                   "group by \"q\".\"id\" order by \"b\" asc", expected);
      // the metadata isn't saved, the loaded sql gets it again
      UniformSQL loaded = reload(sql);
      assertEquals("MixedCase", ((JDBCSelection) loaded.getSelection()).getQuotedAggregate(2));
      JDBCUtil.fixUniformSQLInfo(loaded, repository(TWIN_FIRST), null, loaded.getDataSource());
      assertEquals(expected, regenerate(loaded));
   }

   /**
    * A version before this change ignores the new element and attribute, and a query saved
    * before this change has no record: a missing record is not "unquoted", the aggregate is
    * generated as before this change, with the metadata case repair.
    */
   @Test
   void xmlWithoutTheRecordIsGeneratedAsBefore() throws Exception {
      String query = SELECT + " order by sum(q.\"MixedCase\")";
      UniformSQL sql = parse(query, source("postgresql"));
      String old = toXML(sql).replaceAll("<quotedAggregate column=\"[^\"]*\"/>", "")
         .replaceAll(" quotedAggregate=\"[^\"]*\"", "");
      assertFalse(old.contains("quotedAggregate"), old);

      JDBCDataSource ds = source("postgresql");
      UniformSQL loaded = load(old, ds);
      assertNull(((JDBCSelection) loaded.getSelection()).getQuotedAggregate(1));
      JDBCUtil.fixUniformSQLInfo(loaded, repository(TWIN_FIRST), null, ds);
      // the output before this change
      assertEquals("select \"q\".\"id\", sum(q.\"MIXEDCASE\") from \"t\" q group by \"q\".\"id\" " +
                   "order by sum(q.\"MIXEDCASE\") asc", regenerate(loaded));
   }

   @Test
   void cloneCopyAndEqualityKeepTheRecord() throws Exception {
      String query = SELECT + " order by sum(q.\"MixedCase\")";

      for(String key : helpers().keySet()) {
         UniformSQL sql = parse(query, source(key));
         String expected = regenerate(sql);
         UniformSQL clone = (UniformSQL) sql.clone();
         UniformSQL copy = new UniformSQL();
         copy.read(sql);
         copy.setDataSource(sql.getDataSource());
         JDBCSelection selection = (JDBCSelection) sql.getSelection();
         JDBCSelection selectionCopy = new JDBCSelection(selection);

         assertEquals(expected, regenerate(clone), key);
         assertEquals(expected, regenerate(copy), key);
         assertEquals("MixedCase", selectionCopy.getQuotedAggregate(1), key);
         assertEquals(selection, selection.clone(), key);
         assertEquals(selection.hashCode(), selection.clone().hashCode(), key);
         assertTrue(sql.equalsStructure(clone), key);

         // without the record, as parsed before this change
         JDBCSelection unrecorded = selection.clone();
         unrecorded.setQuotedAggregate(1, null);
         assertNotEquals(selection, unrecorded, key);
         UniformSQL unrecordedOrder = (UniformSQL) sql.clone();
         unrecordedOrder.setQuotedAggregate((String) sql.getOrderByFields()[0], null);
         assertFalse(sql.equalsStructure(unrecordedOrder), key);
      }
   }

   @Test
   void columnChangesKeepTheRecordAtItsColumn() throws Exception {
      // removing a column moves the records after it
      UniformSQL sql = parse("select q.id, sum(q.MixedCase) a, sum(q.\"MixedCase\") b from t q group by q.id",
                             source("postgresql"));
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      selection.removeColumn(1);
      assertEquals("MixedCase", selection.getQuotedAggregate(1));
      assertNull(selection.getQuotedAggregate(2));
      selection.removeColumn(0);
      assertEquals("MixedCase", selection.getQuotedAggregate(0));
      selection.clear();
      assertNull(selection.getQuotedAggregate(0));

      // expanding * rebuilds the selection
      sql = fixed("postgresql", "select q.*, sum(q.\"MixedCase\") from t q group by q.id", TWIN_FIRST);
      selection = (JDBCSelection) sql.getSelection();
      int index = selection.indexOf("sum(\"q\".\"MixedCase\")");
      assertTrue(index > 0, selection.toString());
      assertEquals("MixedCase", selection.getQuotedAggregate(index));
      assertTrue(regenerate(sql).contains(" sum(q.\"MixedCase\") "), regenerate(sql));

      // renaming the table alias rewrites the text of the aggregate, the record stays at its
      // column. On PostgreSQL, Snowflake and Exasol the rewritten text of both spellings is
      // broken (sum("x.."MixedCase")), before this change too, so only the record is checked
      String renamed = "select r.id, sum(r.MixedCase) a, sum(r.\"MixedCase\") b from t r group by r.id";

      for(String key : new String[] { "h2", "h2-ansi", "oracle", "postgresql", "snowflake", "exasol" }) {
         sql = fixed(key, renamed, TWIN_FIRST);
         renameAlias(sql, "r", "x");
         selection = (JDBCSelection) sql.getSelection();
         assertNull(selection.getQuotedAggregate(1), key);
         assertEquals("MixedCase", selection.getQuotedAggregate(2), key);

         if(!Set.of("postgresql", "snowflake", "exasol").contains(key)) {
            String generated = regenerate(sql);
            assertTrue(generated.contains("sum(x.\"MixedCase\")"), key + ": " + generated);
            assertTrue(generated.contains("MIXEDCASE"), key + ": " + generated);
         }
      }
   }

   /**
    * Parsed by one helper, generated by another: the record is kept, so the quoted column keeps
    * its case on H2 and Oracle too. An unquoted one keeps the repair (#77558).
    */
   @Test
   void crossHelperKeepsTheQuotedColumn() throws Exception {
      for(String from : new String[] { "postgresql", "snowflake", "exasol" }) {
         for(String to : new String[] { "h2", "oracle" }) {
            JDBCDataSource ds = source(to);
            UniformSQL sql = load(toXML(parse(SELECT, source(from))), ds);
            JDBCUtil.fixUniformSQLInfo(sql, repository(TWIN_FIRST), null, ds);
            String generated = regenerate(sql);
            assertTrue(generated.contains("sum(q.\"MixedCase\")"), from + " " + to + ": " + generated);

            ds = source(to);
            sql = load(toXML(parse(UNQUOTED, source(from))), ds);
            JDBCUtil.fixUniformSQLInfo(sql, repository(TWIN_FIRST), null, ds);
            generated = regenerate(sql);
            assertTrue(generated.contains("MIXEDCASE"), from + " " + to + ": " + generated);
         }
      }
   }

   @Test
   void regeneratedSqlRegeneratesToItself() throws Exception {
      String[] queries = {
         SELECT + " order by sum(q.\"MixedCase\")",
         "select q.id, sum(q.MixedCase) a, sum(q.\"MixedCase\") b from t q group by q.id",
         "select q.id, count(distinct q.\"MixedCase\") from t q group by q.id",
         "select sum(\"my q\".\"MixedCase\") from t \"my q\"",
      };

      for(String key : helpers().keySet()) {
         for(String query : queries) {
            String generated = regenerate(parse(query, source(key)));
            // the generated sql has the folded name of a column written unquoted (#77643), which
            // is parsed again as another stored text, and the select list is generated sorted by
            // its stored text, so the columns may come in another order
            assertEquals(sortedSelect(generated), sortedSelect(regenerate(parse(generated, source(key)))),
                         key + ": " + query);
         }

         // a select list whose order can't change is generated exactly again
         String generated = regenerate(parse(queries[0], source(key)));
         assertEquals(generated, regenerate(parse(generated, source(key))), key + ": " + queries[0]);
      }
   }

   /**
    * The regenerated sql returns the rows of the sql as written. Derby folds an unquoted name
    * to upper case, as Snowflake and Exasol do, and the unaliased tables are stored quoted on
    * PostgreSQL, Snowflake and Exasol, so the table is created as both T and "t".
    */
   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] quoted = {
         SELECT,
         SELECT + " order by sum(q.\"MixedCase\")",
         "select q.id, count(distinct q.\"MixedCase\") from t q group by q.id",
         "select sum(\"my q\".\"MixedCase\") from t \"my q\"",
      };
      // an unquoted name too, which derby folds to upper case as these helpers do. PostgreSQL
      // folds to lower case, and oracle quotes the repaired column of an unquoted aggregate
      // (sum(q."MixedCase") for MixedCase before MIXEDCASE), before and after this change
      String[] unquoted = {
         UNQUOTED,
         UNQUOTED + " order by sum(q.MixedCase)",
         "select q.id, sum(q.MixedCase) a, sum(q.\"MixedCase\") b from t q group by q.id",
         "select q.id, sum(q.MixedCase) from t q group by q.id order by sum(q.\"MixedCase\")",
         "select q.id, sum(q.MixedCase) a, sum(q.\"MixedCase\") b from t q group by q.id " +
            "order by sum(q.\"MixedCase\")",
         "select q.id, sum(q.\"MixedCase\") b, sum(q.MixedCase) a from t q group by q.id " +
            "order by sum(q.MixedCase)",
      };
      Set<String> folding = Set.of("default", "h2", "h2-ansi", "snowflake", "exasol");

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77578;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table t (id int, MIXEDCASE int, \"MixedCase\" int)");
         stmt.execute("create table \"t\" (id int, MIXEDCASE int, \"MixedCase\" int)");

         for(String table : new String[] { "t", "\"t\"" }) {
            stmt.execute("insert into " + table + " values (1, 10, 100), (1, 20, 200), (2, 30, 300)");
         }

         for(String key : helpers().keySet()) {
            List<String> queries = new ArrayList<>(Arrays.asList(quoted));

            if(folding.contains(key)) {
               queries.addAll(Arrays.asList(unquoted));
            }

            for(String query : queries) {
               List<String> expected = rows(stmt, query);
               String[][] metadata = key.equals("default") ? new String[][] { {} } :
                  new String[][] { {}, TWIN_FIRST, TWIN_SECOND };

               for(String[] columns : metadata) {
                  // PostgreSQL, Snowflake and Exasol quote the group column, "q"."id", which
                  // derby folds differently from the unquoted alias q. It isn't an aggregate
                  String generated = aggregate(key, query, columns).replace("\"q\".\"id\"", "q.id");
                  assertEquals(expected, rows(stmt, generated),
                               key + " " + Arrays.toString(columns) + ": " + query + " -> " + generated);
               }
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77578;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   /**
    * db.caseSensitive=true makes any helper case-sensitive, so its parser quotes every segment
    * as on PostgreSQL: the quoted column keeps its case, an unquoted one keeps the metadata
    * case repair of the output before this change.
    */
   @Test
   void dbCaseSensitiveHelpersKeepTheQuotedColumn() throws Exception {
      String old = SreeEnv.getProperty("db.caseSensitive");
      SreeEnv.setProperty("db.caseSensitive", "true");

      try {
         String text = "select \"q\".\"id\", sum(q.%s) from \"t\" q group by \"q\".\"id\"";

         for(String key : new String[] { "default", "h2", "h2-ansi" }) {
            assertEquals(String.format(text, "\"MixedCase\""), aggregate(key, SELECT), key);
            assertEquals(String.format(text, "MixedCase"), aggregate(key, UNQUOTED), key);

            if(!key.equals("default")) {
               // was sum(q.MIXEDCASE) and sum(q.MixedCase), unquoted
               assertEquals(String.format(text, "\"MixedCase\""), aggregate(key, SELECT, TWIN_FIRST), key);
               assertEquals(String.format(text, "\"MixedCase\""), aggregate(key, SELECT, TWIN_SECOND), key);
               assertEquals(String.format(text, "MIXEDCASE"), aggregate(key, UNQUOTED, TWIN_FIRST), key);
               assertEquals(String.format(text, "MixedCase"), aggregate(key, UNQUOTED, TWIN_SECOND), key);
            }
         }

         String oracle = "select \"q\".\"id\", sum(q.\"%s\") from \"T\" q group by \"q\".\"id\"";
         // was sum(q."MIXEDCASE")
         assertEquals(String.format(oracle, "MixedCase"), aggregate("oracle", SELECT, TWIN_FIRST));
         assertEquals(String.format(oracle, "MIXEDCASE"), aggregate("oracle", UNQUOTED, TWIN_FIRST));
         assertEquals(String.format(oracle, "MixedCase"), aggregate("oracle", UNQUOTED, TWIN_SECOND));
      }
      finally {
         if(old == null) {
            SreeEnv.remove("db.caseSensitive");
         }
         else {
            SreeEnv.setProperty("db.caseSensitive", old);
         }
      }
   }

   /**
    * The sort pane sets an order by on the text of a select column, without a record. A missing
    * record is not "unquoted": the order by keeps the alias of the select column, as before this
    * change, also when that column has a record (tester's repro in round 1).
    */
   @Test
   void sortPaneOrderByOfAnAliasedQuotedAggregateKeepsTheAlias() throws Exception {
      String query = "select q.id, sum(q.\"MixedCase\") a from t q group by q.id";
      String[][] metadata = { {}, TWIN_FIRST, TWIN_SECOND, { "mixedcase", "MixedCase", "id" } };
      String old = SreeEnv.getProperty("db.caseSensitive");

      try {
         for(boolean caseSensitive : new boolean[] { false, true }) {
            String[] keys = caseSensitive ? new String[] { "default", "h2", "h2-ansi", "oracle" } :
               new String[] { "postgresql", "snowflake", "exasol" };
            SreeEnv.setProperty("db.caseSensitive", caseSensitive + "");

            for(String key : keys) {
               for(String[] columns : metadata) {
                  if(key.equals("default") && columns.length > 0) {
                     continue;
                  }

                  UniformSQL sql = sortPane(fixed(key, query, columns));
                  assertNull(sql.getQuotedAggregate(sql.getSelection().getColumn(1)));

                  String label = key + " " + caseSensitive + " " + Arrays.toString(columns);
                  String generated = regenerate(sql);
                  // was order by sum(q."MIXEDCASE"), sum(q.MixedCase), ..., not the alias. Oracle
                  // doesn't sort by an alias, it sorts by the aliased select column
                  String sort = key.equals("oracle") ? "sum\\(q\\.\"MixedCase\"\\)" : "\"?a\"?";
                  assertTrue(generated.matches(".* order by " + sort + " asc"), label + ": " + generated);
                  // the select list is not changed by the order by
                  assertTrue(generated.startsWith(aggregate(key, query, columns) + " order by "),
                             label + ": " + generated);
               }
            }
         }

         SreeEnv.setProperty("db.caseSensitive", "false");
         assertEquals("select \"q\".\"id\", sum(q.\"MixedCase\") as \"a\" from \"t\" q " +
                      "group by \"q\".\"id\" order by \"a\" asc",
                      regenerate(sortPane(fixed("postgresql", query, TWIN_FIRST))));
      }
      finally {
         if(old == null) {
            SreeEnv.remove("db.caseSensitive");
         }
         else {
            SreeEnv.setProperty("db.caseSensitive", old);
         }
      }
   }

   /**
    * A removed order by field drops its record, so the same text added later (from the sort
    * pane, without a record) is generated as before this change and equalsStructure doesn't
    * see the removed sort. A replaced field keeps the record at the new field.
    */
   @Test
   void removedOrderByFieldsDropTheirRecords() throws Exception {
      String query = SELECT + " order by sum(q.\"MixedCase\")";
      String unquoted = UNQUOTED + " order by sum(q.MixedCase)";

      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         UniformSQL sql = fixed(key, query, TWIN_FIRST);
         String field = (String) sql.getOrderByFields()[0];
         assertEquals("MixedCase", sql.getQuotedAggregate(field), key);

         // re-added without a record: the same output as an unquoted order by
         sql.removeAllOrderByFields();
         assertNull(sql.getQuotedAggregate(field), key);
         sql.setOrderBy(field, "asc");
         String expected = aggregate(key, unquoted, TWIN_FIRST);
         assertEquals(expected.substring(expected.indexOf(" order by ")),
                      regenerate(sql).substring(regenerate(sql).indexOf(" order by ")), key);

         UniformSQL removed = fixed(key, query, TWIN_FIRST);
         removed.removeOrderBy(field);
         assertNull(removed.getQuotedAggregate(field), key);
         UniformSQL none = fixed(key, query, TWIN_FIRST);
         none.removeAllOrderByFields();
         assertTrue(removed.equalsStructure(none), key);

         UniformSQL replaced = fixed(key, query, TWIN_FIRST);
         replaced.replaceOrderBy(field, "asc", "sum(\"q\".\"x\")", "desc");
         assertNull(replaced.getQuotedAggregate(field), key);
         assertEquals("MixedCase", replaced.getQuotedAggregate("sum(\"q\".\"x\")"), key);
         replaced.replaceOrderBy("sum(\"q\".\"x\")", "desc", field, "asc");
         assertEquals(regenerate(fixed(key, query, TWIN_FIRST)), regenerate(replaced), key);
      }
   }

   // applies an order by on the text of select column 2, as QueryManagerService applies the sort pane
   private static UniformSQL sortPane(UniformSQL sql) {
      String column = sql.getSelection().getColumn(1);
      sql.removeAllOrderByFields();
      sql.clearOrderDBFields();
      sql.addOrderDBField(column);
      sql.setOrderBy(column, "asc");
      return sql;
   }

   // renames a table alias as QueryGraphModelService does
   private static void renameAlias(UniformSQL sql, String from, String to) {
      Hashtable<String, String> aliasMap = new Hashtable<>();

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable table = sql.getSelectTable(i);
         aliasMap.put(table.getAlias(), table.getAlias());

         if(from.equals(table.getAlias())) {
            aliasMap.put(from, to);
            table.setAlias(to);
         }
      }

      sql.syncTableAlias(aliasMap);
      sql.clearSQLString();
   }

   // the rows, with the values of each row sorted, since the generated sql may reorder columns
   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = executeQuery(stmt, query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            Collections.sort(row);
            rows.add(row.toString());
         }
      }

      // the order by is checked by the generated text
      Collections.sort(rows);
      return rows;
   }

   private static ResultSet executeQuery(Statement stmt, String query) throws SQLException {
      try {
         return stmt.executeQuery(query);
      }
      catch(SQLException ex) {
         throw new SQLException(query, ex);
      }
   }

   // the name the database reads for the quoted names q, id and t written unquoted, on snowflake
   // and exasol (Bug #77643)
   private static String upper(String sql) {
      return sql.replace("\"q\"", "\"Q\"").replace("\"id\"", "\"ID\"").replace("\"t\"", "\"T\"");
   }

   // the sql with the columns of its select list sorted
   private static String sortedSelect(String sql) {
      int from = sql.indexOf(" from ");

      if(!sql.startsWith("select ") || from < 0) {
         return sql;
      }

      String[] columns = sql.substring(7, from).split(", ");
      Arrays.sort(columns);
      return "select " + String.join(", ", columns) + sql.substring(from);
   }

   private static int count(Pattern pattern, String str) {
      int count = 0;

      for(java.util.regex.Matcher m = pattern.matcher(str); m.find(); ) {
         count++;
      }

      return count;
   }

   private static int count(String str, String part) {
      return str.split(Pattern.quote(part), -1).length - 1;
   }

   // ---- harness (the same as UniformSQLQualifiedQuotedColumnTest, #77558) ----

   // the sql regenerated after the metadata step with the columns of every table, or after
   // the parse if there are no columns
   private static String aggregate(String key, String query, String... columns) throws Exception {
      return regenerate(fixed(key, query, columns));
   }

   private static UniformSQL fixed(String key, String query, String... columns) throws Exception {
      JDBCDataSource ds = source(key);
      UniformSQL sql = parse(query, ds);

      if(columns.length > 0 && ds != null) {
         JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, ds);
      }

      return sql;
   }

   // the table metadata is cached by data source name, also in the sree home of earlier test
   // runs, use a new name each time
   private static JDBCDataSource source(String key) {
      JDBCDataSource ds = helpers().get(key);

      if(ds == null) {
         return null;
      }

      ds = (JDBCDataSource) ds.clone();
      ds.setName(ds.getName() + "Aggregate" + RUN + "_" + (++sources));
      return ds;
   }

   private static int sources;
   private static final String RUN = Long.toString(System.nanoTime(), 36);

   private static Map<String, JDBCDataSource> helpers() {
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("default", null);
      helpers.put("h2", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", false));
      helpers.put("h2-ansi", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", true));
      helpers.put("oracle", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x", "oracle",
                                       false));
      helpers.put("postgresql", dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql",
                                           false));
      helpers.put("snowflake", dataSource("net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake://x",
                                          "snowflake", true));
      helpers.put("exasol", dataSource("com.exasol.jdbc.EXADriver", "jdbc:exa:x", "exasol", false));
      return helpers;
   }

   // the metadata of every table, a column has a twin that differs only in case
   private static final String[] TWIN_SECOND = { "MixedCase", "MIXEDCASE", "id" };
   private static final String[] TWIN_FIRST = { "MIXEDCASE", "MixedCase", "id" };

   // the column metadata comes from the repository. SQLTypes.getQualifiedName logs an error
   // that it can't get the root meta-data from XRepository.getRepository(), then goes on
   private static XRepository repository(String[] columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
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

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenAnswer(
            inv -> String.valueOf((Object) inv.getArgument(0)).contains("oracle") ? "oracle" : "H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   private static JDBCDataSource dataSource(String driver, String url, String product, boolean ansiJoin) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77578" + product + ansiJoin);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(ansiJoin);
      return ds;
   }

   private static UniformSQL reload(UniformSQL sql) throws Exception {
      return load(toXML(sql), sql.getDataSource());
   }

   private static UniformSQL load(String xml, JDBCDataSource ds) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      if(ds != null) {
         loaded.setDataSource(ds);
      }

      return loaded;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(ds != null) {
         sql.setDataSource(ds);
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }
}
