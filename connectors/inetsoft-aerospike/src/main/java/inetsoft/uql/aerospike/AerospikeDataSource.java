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
package inetsoft.uql.aerospike;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * DataSource that connects to a Aerospike server
 */

@View(vertical=true, value={
   @View1("host"),
   @View1("port"),
   @View1("namespace"),
   @View1(type=ViewType.LABEL, text="authentication.required.text", col=1, paddingLeft=3),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class AerospikeDataSource extends TabularDataSource<AerospikeDataSource> {
   public static final String TYPE = "Aerospike";

   /**
    * Constructor
    */
   public AerospikeDataSource() {
      super(TYPE, AerospikeDataSource.class);
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
    * get user name
    *
    * @return user name
    */
   @Property(label="User")
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
   @Property(label="Password", password=true)
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
   @Property(label="Namespace", required=true)
   public String getNamespace() {
      return namespace;
   }

   /**
    * set database name
    *
    * @param name database name
    */
   public void setNamespace(String name) {
      this.namespace = name;
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

      if(namespace != null) {
         writer.println("<namespace><![CDATA[" + namespace + "]]></namespace>");
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
      namespace = Tool.getChildValueByTagName(root, "namespace");
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      try {
         AerospikeDataSource ds = (AerospikeDataSource) obj;

         return Objects.equals(host, ds.host) &&
            port == ds.port &&
            Objects.equals(namespace, ds.namespace);
      }
      catch(Exception ex) {
         return false;
      }
   }

   @Override
   public Object clone() {
      AerospikeDataSource source = (AerospikeDataSource) super.clone();

      try {
         source.setCredential((Credential) getCredential().clone());
      }
      catch(CloneNotSupportedException ignore) {
      }

      return source;
   }

   private String host;
   private int port = 10000;
   private String namespace = "default";
}
