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
package inetsoft.uql.asset.sync;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Bug #77484: edge cases of the textual data source rename in a Spark SQL query's stored
 * text (special characters in the names, unterminated text, line endings, empty text, and
 * inputs with no match).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetTabularDependencyTransformerSqlRenameEdgeTest {
   static Stream<Arguments> cases() {
      return Stream.of(
         // replacement text is never treated as a regex replacement
         arguments("dollar in new name", "rest1", "a$1",
                   "select id from rest1.users", "select id from `a$1`.users"),
         arguments("backslash in new name", "rest1", "a\\b",
                   "select id from rest1.users", "select id from `a\\b`.users"),
         // old name is matched literally, never as a regex
         arguments("regex metachar in old name", "rest(1)", "r2",
                   "select id from rest(1).users, rest.1.x", "select id from r2.users, rest.1.x"),
         arguments("dot in old name", "rest.1", "r2",
                   "select id from rest1.users, restX1.users", "select id from rest1.users, restX1.users"),
         arguments("folder old name", "f1/rest1", "f2/rest1",
                   "select id from `f1/rest1`.users", "select id from `f2/rest1`.users"),
         // unterminated text: rename up to it, never inside or after it
         arguments("unterminated quote after", "rest1", "rest2",
                   "select id from rest1.users where s = 'abc", "select id from rest2.users where s = 'abc"),
         arguments("unterminated quote before", "rest1", "rest2",
                   "select 'abc from rest1.users", "select 'abc from rest1.users"),
         arguments("unterminated comment", "rest1", "rest2",
                   "select id from rest1.a /* rest1.b", "select id from rest2.a /* rest1.b"),
         arguments("trailing backslash in literal", "rest1", "rest2",
                   "select id from rest1.a where s = 'x\\", "select id from rest2.a where s = 'x\\"),
         arguments("escaped backslash closes literal", "rest1", "rest2",
                   "select 'a\\\\', rest1.x from t", "select 'a\\\\', rest2.x from t"),
         // position, case and line endings
         arguments("name at start", "rest1", "rest2", "rest1.users", "rest2.users"),
         arguments("different case not renamed", "rest1", "rest2",
                   "select id from REST1.users", "select id from REST1.users"),
         arguments("windows line endings", "rest1", "rest2",
                   "select id\r\nfrom rest1.users -- rest1.c\r\nwhere rest1.users.id = 1\r\n",
                   "select id\r\nfrom rest2.users -- rest1.c\r\nwhere rest2.users.id = 1\r\n"),
         arguments("empty sql", "rest1", "rest2", "", ""),
         // a new name starting with a digit is not an unquoted identifier
         arguments("digit-start new name", "rest1", "1rest",
                   "select id from rest1.users", "select id from `1rest`.users"),
         // a non-ASCII old name does not match inside a longer identifier or dotted path
         arguments("non-ASCII old name prefix guard", "café", "rest2",
                   "select xcafé.x, cafés.y, a.café.z from café.t",
                   "select xcafé.x, cafés.y, a.café.z from rest2.t"),
         // a digit-start old name is matched unquoted, but not inside a longer identifier,
         // a longer number-like token or a dotted path
         arguments("digit-start old name prefix guard", "2024sales", "sales2024",
                   "select x2024sales.a, 12024sales.b, a.2024sales.c, 2024salesX.d from 2024sales.t",
                   "select x2024sales.a, 12024sales.b, a.2024sales.c, 2024salesX.d from sales2024.t"),
         // the old name only in literals, comments, quoted identifiers and longer
         // identifiers: the whole text is scanned and must come back byte-identical
         arguments("no match is byte-identical", "rest1", "rest2",
                   "  select\tID , 'rest1.x' as \"rest1.y\" /* rest1.z */\r\n from rest1x.u -- rest1.w\r\n" +
                      " where rest1 .q = `rest1.u` and rest1_b.v = 1\n\n",
                   "  select\tID , 'rest1.x' as \"rest1.y\" /* rest1.z */\r\n from rest1x.u -- rest1.w\r\n" +
                      " where rest1 .q = `rest1.u` and rest1_b.v = 1\n\n")
      );
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void renamesDataSourceQualifier(String label, String oname, String nname, String sql,
                                   String expected) throws Exception
   {
      Document doc = createWorksheet(sql);

      new AssetTabularDependencyTransformer(null).renameWSSource(
         doc.getDocumentElement(),
         new RenameInfo(oname, nname, RenameInfo.TABULAR_SOURCE | RenameInfo.DATA_SOURCE, true));

      assertEquals(expected, doc.getElementsByTagName("sql").item(0).getTextContent());
   }

   // no Spark SQL query class ships in either repo, so the stored XML is built by hand
   private static Document createWorksheet(String sql) throws Exception {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      Element worksheet = doc.createElement("worksheet");
      Element info = doc.createElement("assemblyInfo");
      info.setAttribute("class", "inetsoft.uql.asset.internal.TabularTableAssemblyInfo");
      Element query = doc.createElement("query");
      query.setAttribute("type", "spark");
      query.setAttribute("class", "inetsoft.uql.spark.sql.SparkSQLQuery");
      Element spark = doc.createElement("query_spark");
      Element sqlElem = doc.createElement("sql");
      sqlElem.setTextContent(sql);
      spark.appendChild(sqlElem);
      query.appendChild(spark);
      info.appendChild(query);
      worksheet.appendChild(info);
      doc.appendChild(worksheet);
      return doc;
   }
}
