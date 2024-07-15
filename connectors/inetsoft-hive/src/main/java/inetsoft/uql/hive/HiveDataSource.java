/*
 * inetsoft-hive - StyleBI is a business intelligence web application.
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
package inetsoft.uql.hive;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * DataSource that connects to a Apache Hive server
 */

@View(vertical=true, value={
   @View1("host"),
   @View1("port"),
   @View1("dbName"),
   @View1("hiveType"),
   @View1(type=ViewType.LABEL, text="authentication.required.text", col=1, paddingLeft=3),
   @View1("user"),
   @View1("password")
})
public class HiveDataSource extends TabularDataSource<HiveDataSource> {
   public static final String TYPE = "Hive";

   /**
    * Constructor
    */
   public HiveDataSource () {
      super(TYPE, HiveDataSource.class);
   }

   /**
    * get the JDBC Url host name
    *
    * @return the host name/ip address
    */
   @Property(label="Host", required=true)
   public String getHost() {
      return host;
   }

   /**
    * set the JDBC Url host name/ip address
    *
    * @param host the host name/ip address
    */
   public void setHost(String host) {
      this.host = host;
   }

   /**
    * get the JDBC Url port number
    *
    * @return port number
    */
   @Property(label="Port", required=true)
   public int getPort() {
      return port;
   }

   /**
    * set the server port number for the JDBC connection
    *
    * @param port the port number
    */
   public void setPort(int port) {
      this.port = port;
   }

   /**
    * get the Hive server types, as determinied by the getHiveServerTypes method
    *
    * @return Hive server type
    */
   @Property(label="HiveServer Type")
   @PropertyEditor(tagsMethod="getHiveServerTypes")
   public String getHiveType() {
      return hiveType;
   }

   /**
    * determine the available hive server types
    *
    * @return list of available types
    */
   public String[] getHiveServerTypes() {
      String[] hiveTypes = new String[2];
      hiveTypes[0] = "HiveServer1";
      hiveTypes[1] = "HiveServer2";

      return hiveTypes;
   }

   /**
    * set Hive server type
    * @param hType type of Hive server
    */
   public void setHiveType(String hType) {
      this.hiveType = hType;
   }

   /**
    * get user name
    *
    * @return user name
    */
   @Property(label="User", required=true)
   public String getUser() {
      return user;
   }

   /**
    * set user name
    *
    * @param user user name
    */
   public void setUser(String user) {
      this.user = user;
   }

   /**
    * get password
    *
    * @return password
    */
   @Property(label="Password", password=true, required=true)
   public String getPassword() {
      return password;
   }

   /**
    * set password
    * @param password password
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * get the database name
    *
    * @return database name
    */
   @Property(label="Database", required=true)
   public String getDbName() {
      return dbName;
   }

   /**
    * set database name
    *
    * @param name database name
    */
   public void setDbName(String name) {
      this.dbName = name;
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" port=\"" + port + "\"");
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(host != null) {
         writer.println("<host><![CDATA[" + host + "]]></host>");
      }

      if(dbName != null) {
         writer.println("<db><![CDATA[" + dbName + "]]></db>");
      }

      if(user != null) {
         writer.println("<user><![CDATA[" + user + "]]></user>");
      }

      if(password != null) {
         writer.println("<password><![CDATA[" + Tool.encryptPassword(password) +
                           "]]></password>");
      }

      if(hiveType != null) {
         writer.println("<hiveType><![CDATA[" + hiveType + "]]></hiveType>");
      }
   }

   @Override
   public void parseAttributes(Element root) throws Exception {
      super.parseAttributes(root);
      String prop;

      if((prop = Tool.getAttribute(root, "port")) != null) {
         port = Integer.parseInt(prop);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      host = Tool.getChildValueByTagName(root, "host");
      dbName = Tool.getChildValueByTagName(root, "db");
      hiveType = Tool.getChildValueByTagName(root, "hiveType");
      user = Tool.getChildValueByTagName(root, "user");
      password =
         Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"));
   }

   @Override
   public boolean equals(Object obj) {
      try {
         HiveDataSource ds = (HiveDataSource) obj;

         return Objects.equals(host, ds.host) &&
            port == ds.port &&
            Objects.equals(hiveType, ds.hiveType) &&
            Objects.equals(dbName, ds.dbName) &&
            Objects.equals(user, ds.user) &&
            Objects.equals(password, ds.password);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String host;
   private int port = 10000;
   private String hiveType;
   private String dbName = "default";
   private String user;
   private String password;
}
