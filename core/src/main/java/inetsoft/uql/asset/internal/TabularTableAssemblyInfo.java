/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.internal;

import inetsoft.uql.*;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * SQLBoundTableAssemblyInfo stores sql bound table assembly information.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class TabularTableAssemblyInfo extends BoundTableAssemblyInfo {
   /**
    * Constructor.
    */
   public TabularTableAssemblyInfo() {
      super();
   }

   /**
    * Constructor.
    */
   public TabularTableAssemblyInfo(TabularQuery query) {
      super();
      this.query = query;
   }

   /**
    * Set SQL Query
    * @param query the specified SQL Query
    */
   public void setQuery(TabularQuery query) {
      this.query = query;
   }

   /**
    * Get the SQL Query
    */
   public TabularQuery getQuery() {
      return this.query;
   }

   @Override
   public boolean isSQLMergeable() {
      return false;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(query != null) {
         writer.format("<query type=\"%s\" class=\"%s\">%n",
                       query.getType(), query.getClass().getName());
         query.writeXML(writer);
         writer.println("</query>");

         XDataSource dataSource = query.getDataSource();

         if(dataSource != null) {
            writer.println("<datasource name=\"" + Tool.escape(dataSource.getFullName()) + "\"/>");
         }
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "query");

      if(node != null) {
         String cls = Tool.getAttribute(node, "class");
         String type = Tool.getAttribute(node, "type");

         if(type != null && Objects.equals(cls, "inetsoft.uql.rest.RestQuery")) {
            cls = Config.getQueryClass(type);
         }

         node = Tool.getFirstChildNode(node);

         if(cls == null) {
            cls = Tool.getAttribute(node, "class");
         }

         try {
            query = (TabularQuery) Config.getClass(type, cls).getConstructor().newInstance();
            query.parseXML(node);
         }
         catch(ClassNotFoundException ex) {
            LOG.error("Data source plugin missing: " + cls);
            return;
         }
      }

      node = Tool.getChildNodeByTagName(elem, "datasource");

      if(node != null) {
         String name = Tool.getAttribute(node, "name");
         XRepository repository = XFactory.getRepository();
         XDataSource dataSource = repository.getDataSource(name);
         query.setDataSource(dataSource);
      }

      if(query == null && getSourceInfo() != null && getSourceInfo().getSource() != null) {
         query = TabularUtil.createQuery(getSourceInfo().getSource());
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) super.clone();

         if(query != null) {
            info.query = (TabularQuery) query.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private TabularQuery query;

   private static final Logger LOG =
      LoggerFactory.getLogger(TabularTableAssemblyInfo.class);
}
