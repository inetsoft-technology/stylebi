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
package inetsoft.uql.datagov;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Query used to connect to the data.gov web services.
 */
@SuppressWarnings("unused")
@View(vertical = true, value = { @View1("suffix") })
public class DatagovQuery extends TabularQuery {
   /**
    * Creates a new instance of <tt>DatagovQuery</tt>.
    */
   public DatagovQuery() {
      super(DatagovDataSource.TYPE);
   }

   /**
    * Gets the path, relative to the data source URL of the source of the
    * query data.
    *
    * @return the URL suffix.
    */
   @Property(label = "URL Suffix")
   public String getSuffix() {
      return suffix;
   }

   /**
    * Sets the path, relative to the data source URL of the source of the
    * query data.
    *
    * @param suffix the URL suffix.
    */
   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(suffix != null) {
         writer.println("<suffix><![CDATA[" + suffix + "]]></suffix>");
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      Element node = Tool.getChildNodeByTagName(root, "suffix");
      suffix = Tool.getValue(node);
   }

   private String suffix;
}
