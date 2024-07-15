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
package inetsoft.uql.xmla;

import inetsoft.uql.XDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * XMLA data source records URL and catalog information.
 *
 * @version 10.1, 2/2/2009
 * @author InetSoft Technology Corp
 */
public class XMLADataSource extends XDataSource {
   /**
    * Create an XMLA data source.
    */
   public XMLADataSource() {
      super(XMLA);
   }

   /**
    * Get the data source connection parameters.
    */
   @Override
   public UserVariable[] getParameters() {
      if(!isRequireLogin() ||
         getUser() != null && getUser().length() > 0 && getPassword() != null)
      {
         return null;
      }

      UserVariable[] params = new UserVariable[2];

      params[0] = new UserVariable(XUtil.DB_USER_PREFIX + getName());
      params[0].setAlias(Catalog.getCatalog().getString("User"));
      params[1] = new UserVariable(XUtil.DB_PASSWORD_PREFIX + getName());
      params[1].setAlias(Catalog.getCatalog().getString("Password"));
      params[0].setSource(getName());
      params[1].setSource(getName());
      params[1].setHidden(true);

      return params;
   }

   /**
    * Get the name of the data source to which this domain is associated.
    *
    * @return the name of the associated data source.
    */
   public String getDataSource() {
      return datasource;
   }

   /**
    * Set the name of the data source to which this domain is associated.
    *
    * @param datasource the name of the associated data source.
    */
   public void setDataSource(String datasource) {
      this.datasource = datasource;
   }

   /**
    * Get the data source server side name.
    *
    * @return the associated data source server side name.
    */
   public String getDatasourceName() {
      return datasourceName;
   }

   /**
    * Set the data source server side name.
    */
   public void setDatasourceName(String datasourceName) {
      this.datasourceName = datasourceName;
   }

   /**
    * Get the data source info.
    *
    * @return the associated data source info.
    */
   public String getDatasourceInfo() {
      return datasourceInfo;
   }

   /**
    * Set the data source info.
    */
   public void setDatasourceInfo(String datasourceInfo) {
      this.datasourceInfo = datasourceInfo;
   }

   /**
    * Get the data source catalog name.
    *
    * @return the associated data source catalog name.
    */
   public String getCatalogName() {
      return catalogName;
   }

   /**
    * Set the data source catalog name.
    */
   public void setCatalogName(String catalogName) {
      this.catalogName = catalogName;
   }

   /**
    * Get the data source url.
    *
    * @return the associated data source url.
    */
   public String getURL() {
      return url;
   }

   /**
    * Set the data source url.
    */
   public void setURL(String url) {
      this.url = url;
   }

   /**
    * Set whether this data source requires user login during connection.
    */
   public void setRequireLogin(boolean login) {
      this.login = login;
   }

   /**
    * Check if this data soure requires user login.
    */
   public boolean isRequireLogin() {
      return this.login;
   }

   /**
    * Set the user id.
    */
   public void setUser(String user) {
      this.user = user;
   }

   /**
    * Get the used user id.
    */
   public String getUser() {
      return this.user;
   }

   /**
    * Set the user password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Get the used user password.
    */
   public String getPassword() {
      return this.password;
   }

   @Override
   public boolean equals(Object obj) {
      if(obj instanceof XMLADataSource) {
         XMLADataSource jdbcObj = (XMLADataSource) obj;

         String obj_url = jdbcObj.getURL();

         if(obj_url == null) {
            obj_url = "";
         }

         String url = this.getURL();

         if(url == null) {
            url = "";
         }

         if(!url.equals(obj_url) || !Tool.equals(datasource, jdbcObj.datasource) ||
            !Tool.equals(datasourceInfo, jdbcObj.datasourceInfo) ||
            !Tool.equals(catalogName, jdbcObj.catalogName) ||
            !Tool.equals(getDescription(), jdbcObj.getDescription()) ||
            isRequireLogin() != jdbcObj.isRequireLogin())
         {
            return false;
         }


         if(isRequireLogin() && (!Tool.equals(user, jdbcObj.user) ||
            !Tool.equals(password, jdbcObj.password)))
         {
            return false;
         }

         return true;
      }

      return super.equals(obj);
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<ds_xmla");

      if(datasource != null) {
         writer.print(" datasource=\"" + Tool.byteEncode(datasource) + "\"");
      }

      if(datasourceName != null) {
         writer.print(" datasourceName=\"" +
            Tool.byteEncode(datasourceName) + "\"");
      }

      if(datasourceInfo != null) {
         writer.print(" datasourceInfo=\"" +
            Tool.byteEncode(datasourceInfo) + "\"");
      }

      if(catalogName != null) {
         writer.print(" catalogName=\"" + Tool.byteEncode(catalogName) + "\"");
      }

      if(url != null) {
         writer.print(" url=\"" + Tool.byteEncode(url) + "\"");
      }

      if(user != null) {
         writer.print(" user=\"" + Tool.byteEncode(user) + "\"");
      }

      if(password != null) {
         writer.print(" password=\"" + Tool.escape(Tool.encryptPassword(password)) + "\""); // NOSONAR
      }

      writer.print(" login=\"" + login + "\"");
      writer.print(">");
      super.writeXML(writer);
      writer.print("</ds_xmla>");
   }

   /**
    * Parse the XML element that contains information on this
    * data source.
    */
   @Override
   public void parseXML(Element root) throws Exception {
      super.parseXML(root);

      String val = Tool.getAttribute(root, "datasource");

      if(val != null) {
         datasource = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(root, "datasourceName");

      if(val != null) {
         datasourceName = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(root, "datasourceInfo");

      if(val != null) {
         datasourceInfo = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(root, "catalogName");

      if(val != null) {
         catalogName = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(root, "url");

      if(val != null) {
         url = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(root, "user");

      if(val != null) {
         user = Tool.byteDecode(val);
      }

      password = Tool.decryptPassword(Tool.getAttribute(root, "password"));
      val = Tool.getAttribute(root, "login");

      if(val != null) {
         login = "true".equals(val);
      }
   }

   private String datasource = null; // dx name
   private String datasourceName = null; // dx server side name
   private String datasourceInfo = null; // dx info
   private String catalogName = null; // dx catalog name
   private String url = null; // dx url
   private String user = null; // dx user
   private String password = null; // dx password
   private boolean login = false; // if requires login
}