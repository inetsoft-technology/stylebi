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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Bug #77569. The queries whose saved xml must be the same as before the fix, apart from the
 * new quotedSegments attribute of a table name and quotedSql attribute of a derived table, so
 * every reader of the saved text, and an older build, sees the same text. The expected lines
 * were recorded with these methods on main before the fix
 * (UniformSQLQuotedTableNameTest-stored-texts.txt). This class only uses methods that exist on
 * main, so it can record them again there.
 */
final class QuotedTableNameStoredTexts {
   private QuotedTableNameStoredTexts() {
   }

   /**
    * The queries, each written for the helper named before the |.
    */
   static final String[] QUERIES = {
      "derby|select \"a\".V from \"a\"",
      "derby|select x.V from \"a\" x",
      "derby|select \"a\".V, \"a\".ID from \"a\"",
      "derby|select \"a\".V from \"a\" where \"a\".ID = 1",
      "derby|select x.ID, y.V from \"A\" x left join \"a\" y on x.ID = y.ID",
      "derby|select \"b\".W, \"a\".V from \"b\" left join \"a\" on \"b\".ID = \"a\".ID",
      "derby|select \"b\".ID, \"a\".V from \"b\", \"a\" where \"b\".ID = \"a\".ID",
      "derby|select \"a\".V, \"A\".V from \"a\", \"A\" where \"a\".ID = \"A\".ID",
      "derby|select \"A\".V from \"A\" where \"A\".ID in (select \"a\".ID from \"a\")",
      "derby|select \"A\".V from \"A\" where exists (select 1 from \"a\" where \"a\".ID = \"A\".ID)",
      "derby|select \"a\".ID, count(*) from \"a\" group by \"a\".ID having count(*) > 0 " +
         "order by \"a\".ID",
      "derby|select sum(\"a\".\"MixedCase\") from \"a\"",
      "derby|select max(\"a\".V), \"a\".ID from \"a\" group by \"a\".ID",
      "derby|select y.V from \"S\".\"a\" y",
      "derby|select \"S\".\"a\".V from \"S\".\"a\"",
      "derby|select x.V from S.\"a\" x",
      "derby|select \"c\".ID from \"c\" where exists (select 1 from C where C.ID = \"c\".ID)",
      "derby|select \"c\".ID from \"c\" where not exists (select 1 from C where C.ID = \"c\".ID)",
      "derby|select \"c\".ID from \"c\" where \"c\".ID in (select C.ID from C where C.ID = \"c\".ID)",
      "derby|select \"c2\".ID from \"APP\".\"c2\" where exists " +
         "(select 1 from C2 where C2.ID = \"c2\".ID)",
      "derby|select \"c\".ID, (select max(C.V) from C where C.ID = \"c\".ID) from \"c\"",
      "derby|select \"c\".ID, coalesce((select max(C.V) from C where C.ID = \"c\".ID), 0) " +
         "from \"c\"",
      "derby|select \"c\".ID, (select count(*) from C where C.ID = \"c\".ID) + 1 from \"c\"",
      "derby|select \"c\".ID, case when (select count(*) from C where C.ID = \"c\".ID) > 0 " +
         "then 1 else 0 end from \"c\"",
      "derby|select \"c\".ID from \"c\" group by \"c\".ID " +
         "having (select count(*) from C where C.ID = \"c\".ID) > 0",
      "derby|select \"c\".ID from \"c\" where \"c\".ID = " +
         "(select max(C.ID) from C where C.ID = \"c\".ID)",
      "derby|select \"a\".ID, max(\"a\".V) from \"a\" group by \"a\".ID having max(\"a\".V) > 0",
      "derby|select case when \"a\".V > 0 then \"a\".ID else 0 end from \"a\"",
      "derby|select \"a\".ID + 1 from \"a\"",
      "derby|select coalesce(\"a\".V, 0) from \"a\"",
      "derby|select \"a\".ID from \"a\" where \"a\".V + 1 > 1",
      "derby|select (\"a\".V + 1) * 2 from \"a\"",
      "derby|select \"S\".\"a\".V * 2 from \"S\".\"a\"",
      // case conditions and subqueries generated while parsing
      "derby|select \"a\".ID from \"a\" where case when \"a\".V > 0 then 1 else 0 end = 1",
      "derby|select \"c\".ID from \"c\" where exists " +
         "(select 1 from C where case when C.ID = \"c\".ID then 1 else 0 end = 1)",
      "derby|select \"c\".ID from \"c\" where not exists " +
         "(select 1 from C where case when C.ID = \"c\".ID then 1 else 0 end = 1)",
      "derby|select \"c\".ID from \"c\" where 1 = " +
         "(select case when C.ID = \"c\".ID then 1 else 0 end from C where C.ID = 1)",
      "derby|select \"c\".ID from \"c\" where 1 = " +
         "(select max(case when C.ID = \"c\".ID then 1 else 0 end) from C)",
      "derby|select \"c\".ID, (select count(*) from \"a\" where \"a\".ID = \"c\".ID) from \"c\"",
      "derby|select \"c\".ID, (select count(*) from C where exists " +
         "(select 1 from \"a\" where \"a\".ID = C.ID and C.ID = \"c\".ID)) from \"c\"",
      "derby|select \"c\".ID, (select max(\"a\".V) from \"a\") from \"c\"",
      // a derived table, saved as the text of its sql
      "derby|select d.V from (select \"a\".V from \"a\") d",
      "derby|select d.V from (select \"a\".V from \"a\" where \"a\".ID = 1) d where d.V > 0",
      "mysql|select d.w from (select `b`.w from `b`) d",
      // other quotes
      "mysql|select c.id, (select max(`b`.w) from `b`) from c",
      "mysql|select coalesce((select max(`b`.w) from `b`), 0) from c",
      "mysql|select `c`.id, (select max(b.w) from `b` b where b.id = `c`.id) from `c`",
      "mysql|select case when `c`.id > 0 then `c`.id else 0 end from `c`",
      "mysql|select `c`.id from `c` where case when `c`.id > 0 then 1 else 0 end = 1",
      "mysql|select `orders`.amount * 2 from `orders`",
      "sqlserver|select [c].id, (select max([b].w) from [b] where [b].id = [c].id) from [c]",
      "sqlserver|select coalesce((select max([b].w) from [b]), 0) from c",
      "sqlserver|select case when [c].id > 0 then [c].id else 0 end from [c]",
      "sqlserver|select [Orders].Amount * 2 from [Orders]",
      // unquoted controls
      "derby|select a.V from a",
      "derby|select C.ID from C where exists (select 1 from a where a.ID = C.ID)",
   };

   /**
    * Get the data source of a helper, built with a real driver and url.
    */
   static JDBCDataSource dataSource(String helper) {
      String[] info = switch(helper) {
         case "derby" -> new String[] { "org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:x", "derby" };
         case "mysql" -> new String[] { "com.mysql.jdbc.Driver", "jdbc:mysql://localhost/db", "mysql" };
         default -> new String[] { "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                                   "jdbc:sqlserver://localhost", "sql server" };
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77569" + info[2]);
      ds.setDriver(info[0]);
      ds.setURL(info[1]);
      ds.setRuntimeProductName(info[2]);
      // otherwise the mysql helper asks the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   /**
    * Get the saved xml of each query, one line each, without the quotedSegments and quotedSql
    * attributes.
    */
   static List<String> lines() throws Exception {
      List<String> lines = new ArrayList<>();

      for(String query : QUERIES) {
         int bar = query.indexOf('|');
         String text = query.substring(bar + 1);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(dataSource(query.substring(0, bar)));
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         StringWriter buffer = new StringWriter();

         try(PrintWriter writer = new PrintWriter(buffer)) {
            sql.writeXML(writer);
         }

         String xml = buffer.toString().replaceAll(" quoted(Segments|Sql)=\"[^\"]*\"", "")
            .replace("\r", "").replace("\n", "\\n");
         lines.add(query + " => " + xml);
      }

      return lines;
   }
}
