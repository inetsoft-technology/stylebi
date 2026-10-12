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
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77484: renaming a tabular data source must rename the data source qualifier in a
 * Spark SQL query's stored text without regenerating the query from a parse.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssetTabularDependencyTransformerSqlRenameTest {
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', quoteCharacter = '~', textBlock = """
      # parse fails on LIMIT
      select id from rest1.users where id > 5 limit 10 | select id from rest2.users where id > 5 limit 10
      # parse fails on QUALIFY
      select id from rest1.users where id > 5 qualify row_number() over (order by id) = 1 | select id from rest2.users where id > 5 qualify row_number() over (order by id) = 1
      # lossy parse drops TOP
      select top 5 id from rest1.users where id > 5 | select top 5 id from rest2.users where id > 5
      # table alias kept
      select u.id from rest1.users u where u.id > 5 | select u.id from rest2.users u where u.id > 5
      # full-name column qualifiers renamed
      select rest1.a.id from rest1.a, rest1.b where rest1.a.id = rest1.b.id | select rest2.a.id from rest2.a, rest2.b where rest2.a.id = rest2.b.id
      # subquery table renamed
      select id from rest1.users where id in (select uid from rest1.orders) | select id from rest2.users where id in (select uid from rest2.orders)
      # backtick table name kept
      select id from rest1.`my users` where id > 5 | select id from rest2.`my users` where id > 5
      # backtick data source name renamed
      select id from `rest1`.users | select id from `rest2`.users
      # string literal, prefix collision, dotted path and variable unchanged
      select id from rest1.users where name = 'rest1.users' and k in (select k from rest10.t) and x = xrest1.y and z = a.rest1.b and v = $(rest1.v) | select id from rest2.users where name = 'rest1.users' and k in (select k from rest10.t) and x = xrest1.y and z = a.rest1.b and v = $(rest1.v)
      # escaped quotes in a literal
      select id from rest1.users where s = 'it''s rest1.x' and t = 'a\\' rest1.y' | select id from rest2.users where s = 'it''s rest1.x' and t = 'a\\' rest1.y'
      # comments unchanged
      select id /* rest1.c */ from rest1.users -- rest1.x comment | select id /* rest1.c */ from rest2.users -- rest1.x comment
      # refused join before the matching table
      select x.id from other.x left join other.y on x.id > y.id, rest1.b where x.k = 1 | select x.id from other.x left join other.y on x.id > y.id, rest2.b where x.k = 1
      # no match is byte-identical
      ~select  ID\tfrom other.users  where n = 'rest1.users' -- rest1.~ | ~select  ID\tfrom other.users  where n = 'rest1.users' -- rest1.~
      """)
   void renamesDataSourceQualifierInSqlText(String sql, String expected) throws Exception {
      Document doc = createWorksheet(sql);

      new AssetTabularDependencyTransformer(null).renameWSSource(
         doc.getDocumentElement(),
         new RenameInfo("rest1", "rest2", RenameInfo.TABULAR_SOURCE | RenameInfo.DATA_SOURCE, true));

      assertEquals(expected, doc.getElementsByTagName("sql").item(0).getTextContent());
   }

   @ParameterizedTest(name = "{0} -> {1}")
   @CsvSource(delimiter = '|', textBlock = """
      rest1    | my rest  | select id from `my rest`.users
      f1/rest1 | f2/rest1 | select id from `f2/rest1`.users
      """)
   void quotesNewNameThatIsNotAPlainIdentifier(String oname, String nname, String expected)
      throws Exception
   {
      String sql = "rest1".equals(oname) ?
         "select id from rest1.users" : "select id from `" + oname + "`.users";
      Document doc = createWorksheet(sql);

      new AssetTabularDependencyTransformer(null).renameWSSource(
         doc.getDocumentElement(),
         new RenameInfo(oname, nname, RenameInfo.TABULAR_SOURCE | RenameInfo.DATA_SOURCE, true));

      assertEquals(expected, doc.getElementsByTagName("sql").item(0).getTextContent());
   }

   @ParameterizedTest(name = "{0} -> {1}: {2}")
   @CsvSource(delimiter = '|', quoteCharacter = '~', textBlock = """
      # Spark's unquoted identifier is ASCII only, so a non-ASCII new name is quoted
      rest1 | 销售  | select id from rest1.users              | select id from `销售`.users
      rest1 | café  | select id from rest1.users              | select id from `café`.users
      # a non-ASCII old name is still matched, unquoted or quoted
      销售  | rest2 | select id from 销售.users, `销售`.orders | select id from rest2.users, `rest2`.orders
      # a backtick in a name is doubled in the quoted form
      rest1 | a`b   | select id from rest1.users              | select id from `a``b`.users
      a`b   | c     | select id from `a``b`.t                 | select id from `c`.t
      # an unquoted all-digit name is a number, but a name that only starts with a digit is not
      1     | 2     | select 1.5 from `1`.t                   | select 1.5 from `2`.t
      2024sales | sales2024 | select id from 2024sales.orders | select id from sales2024.orders
      # known limitation: a table named like the data source can't be told apart from it
      rest1 | rest2 | select rest1.id from rest1.rest1        | select rest2.id from rest2.rest1
      """)
   void renamesSpecialNames(String oname, String nname, String sql, String expected)
      throws Exception
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
