/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.cassandra;

import inetsoft.uql.jdbc.util.VarSQL;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

@View(vertical=true, value={
      @View1(type=ViewType.LABEL, value="queryString", font="Default-BOLD-11"),
      @View1(type=ViewType.EDITOR, value="queryString"),
      @View1(type=ViewType.LABEL, text="The query is a CQL (Cassandra Query Language) " +
         "statement. CQL string literals are single-quoted and a quote inside a literal is " +
         "escaped by doubling it (''). A string $(name) variable inside a quoted literal " +
         "is escaped, an unquoted string $(name) becomes an escaped literal, and $(@name) " +
         "embeds the raw text.")
   })
public class CassandraQuery extends TabularQuery {
   public CassandraQuery() {
      super(CassandraDataSource.TYPE);
   }

   // CQL literals escape a quote only by doubling it (Bug #78594); a uuid or
   // timeuuid value in an unquoted placeholder is a bare token, so a uuid for
   // a text column must be written quoted, '$(v)' (Bug #79597)
   @Property(label="Enter Query", required=true, sql=true,
             literalEscapeStyle=VarSQL.LiteralEscapeStyle.SQL,
             rawUuidStrings=true)
   @PropertyEditor(rows=10, columns=65)
   public String getQueryString() {
      return qstr;
   }

   public void setQueryString(String qstr) {
      this.qstr = qstr;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(qstr != null) {
         writer.println("<queryStr><![CDATA[" + qstr + "]]></queryStr>");
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      Element node = Tool.getChildNodeByTagName(root, "queryStr");
      qstr = Tool.getValue(node);
   }

   private String qstr;
}
