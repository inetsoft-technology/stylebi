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
package inetsoft.uql.asset.internal;

import inetsoft.uql.XFactory;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * SQLBoundTableAssemblyInfo stores sql bound table assembly information.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class SQLBoundTableAssemblyInfo extends BoundTableAssemblyInfo {
   /**
    * Constructor.
    */
   public SQLBoundTableAssemblyInfo() {
      super();
      this.query = new JDBCQuery();
      query.setUserQuery(true);
   }

   /**
    * Constructor.
    */
   public SQLBoundTableAssemblyInfo(JDBCQuery query) {
      super();
      this.query = query;
   }

   /**
    * Set SQL Query
    * @param query the specified SQL Query
    */
   public void setQuery(JDBCQuery query) {
      this.query = query;
   }

   /**
    * Get the SQL Query
    */
   public JDBCQuery getQuery() {
      return this.query;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(query != null) {
         writer.println("<query>");
         query.writeXML(writer);
         writer.println("</query>");

         JDBCDataSource dataSource = (JDBCDataSource) query.getDataSource();

         if(dataSource != null) {
            writer.println("<datasource name=\"" + dataSource.getFullName() + "\"/>");
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
         node = Tool.getFirstChildNode(node);
         query.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "datasource");

      if(node != null) {
         String name = Tool.getAttribute(node, "name");
         XRepository repository = XFactory.getRepository();
         JDBCDataSource dataSource = (JDBCDataSource) repository.getDataSource(name);
         query.setDataSource(dataSource);
         UniformSQL sql = (UniformSQL) query.getSQLDefinition();

         if(sql != null) {
            sql.setDataSource(dataSource);
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) super.clone();
         info.query = (JDBCQuery) query.clone();
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private JDBCQuery query;

   private static final Logger LOG =
      LoggerFactory.getLogger(SQLBoundTableAssemblyInfo.class);
}
