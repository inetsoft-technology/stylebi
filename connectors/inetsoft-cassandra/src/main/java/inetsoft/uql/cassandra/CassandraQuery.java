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

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

@View(vertical=true, value={
      @View1(type=ViewType.LABEL, value="queryString", font="Default-BOLD-11"),
      @View1(type=ViewType.EDITOR, value="queryString")
   })
public class CassandraQuery extends TabularQuery {
   public CassandraQuery() {
      super(CassandraDataSource.TYPE);
   }

   @Property(label="Enter Query", required=true)
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
