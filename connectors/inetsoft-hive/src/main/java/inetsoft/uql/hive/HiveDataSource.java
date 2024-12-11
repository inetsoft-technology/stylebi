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
package inetsoft.uql.hive;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
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
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class HiveDataSource extends TabularDataSource<HiveDataSource> {
   public static final String TYPE = "Hive";

   /**
    * Constructor
    */
   public HiveDataSource () {
      super(TYPE, HiveDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD;
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
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getUser() {
      return ((PasswordCredential) getCredential()).getUser();
   }

   /**
    * set user name
    *
    * @param user user name
    */
   public void setUser(String user) {
      ((PasswordCredential) getCredential()).setUser(user);
   }

   /**
    * get password
    *
    * @return password
    */
   @Property(label="Password", password=true, required=true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getPassword() {
      return ((PasswordCredential) getCredential()).getPassword();
   }

   /**
    * set password
    * @param password password
    */
   public void setPassword(String password) {
      ((PasswordCredential) getCredential()).setPassword(password);
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
   }

   @Override
   public boolean equals(Object obj) {
      try {
         HiveDataSource ds = (HiveDataSource) obj;

         return Objects.equals(host, ds.host) &&
            port == ds.port &&
            Objects.equals(hiveType, ds.hiveType) &&
            Objects.equals(dbName, ds.dbName) &&
            Objects.equals(getCredential(), ds.getCredential());
      }
      catch(Exception ex) {
         return false;
      }
   }

   @Override
   public Object clone() {
      HiveDataSource source = (HiveDataSource) super.clone();

      try {
         source.setCredential((Credential) getCredential().clone());
      }
      catch(CloneNotSupportedException ignore) {
      }

      return source;
   }

   private String host;
   private int port = 10000;
   private String hiveType;
   private String dbName = "default";
}
