/*
 * inetsoft-datagov - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.datagov;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Data source used to connect to the data.gov web services.
 */
@SuppressWarnings("unused")
@View(vertical = true, value = {
   @View1("URL"),
   @View1(type = ViewType.LABEL,
          text = "authentication.required.text",
          col = 1,
          paddingLeft = 3),
   @View1("user"),
   @View1("password")
})
public class DatagovDataSource extends TabularDataSource<DatagovDataSource> {
   public static final String TYPE = "Datagov";

   /**
    * Creates a new instance of <tt>DatagovDataSource</tt>.
    */
   public DatagovDataSource() {
      super(TYPE, DatagovDataSource.class);
   }

   /**
    * Gets the base URL of the data.gov web services.
    *
    * @return the base URL.
    */
   @Property(label="URL")
   public String getURL() {
      return url;
   }

   /**
    * Sets the base URL of the data.gov web services.
    *
    * @param url the base URL.
    */
   public void setURL(String url) {
      this.url = url;
   }

   /**
    * Gets the name of the user used to authenticate with the web services.
    *
    * @return the user name or <tt>null</tt> if not required.
    */
   @Property(label="User")
   public String getUser() {
      return user;
   }

   /**
    * Sets the name of the user used to authenticate with the web services.
    *
    * @param user the user name or <tt>null</tt> if not required.
    */
   public void setUser(String user) {
      this.user = user;
   }

   /**
    * Gets the password used to authenticate with the web services.
    *
    * @return the password or <tt>null</tt> if not required.
    */
   @Property(label="Password", password=true)
   public String getPassword() {
      return password;
   }

   /**
    * Sets the password used to authenticate with the web services.
    *
    * @param password the password or <tt>null</tt> if not required.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   @Override
   public boolean isTypeConversionSupported() {
      return true;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(url != null) {
         writer.format("<url><![CDATA[%s]]></url>%n", url);
      }

      if(user != null) {
         writer.format("<user><![CDATA[%s]]></user>%n", user);
      }

      if(password != null) {
         writer.format(
            "<password><![CDATA[%s]]></password>%n",
            Tool.encryptPassword(password));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
      user = Tool.getChildValueByTagName(root, "user");
      password =
         Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"));
   }

   @Override
   public boolean equals(Object obj) {
      try {
         DatagovDataSource ds = (DatagovDataSource) obj;

         return Objects.equals(url, ds.url) &&
            Objects.equals(user, ds.user) &&
            Objects.equals(password, ds.password);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String url;
   private String user;
   private String password;

   private static final Logger LOG =
      LoggerFactory.getLogger(DatagovDataSource.class.getName());
}
