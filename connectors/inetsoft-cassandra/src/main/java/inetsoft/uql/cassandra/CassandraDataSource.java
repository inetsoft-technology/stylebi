/*
 * inetsoft-cassandra - StyleBI is a business intelligence web application.
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
package inetsoft.uql.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Objects;

@View(vertical=true, value={
      @View1("host"),
      @View1("port"),
      @View1("datacenter"),
      @View1("SSL"),
      @View1("keyspace"),
      @View1(type=ViewType.LABEL, text="authentication.required.text", col=1, paddingLeft=3),
      @View1("user"),
      @View1("password")
   })
public class CassandraDataSource extends TabularDataSource<CassandraDataSource> {
   public static final String TYPE = "Cassandra";

   public CassandraDataSource() {
      super(TYPE, CassandraDataSource.class);
   }

   @Property(label="Host", required = true)
   public String getHost() {
      return host;
   }

   public void setHost(String host) {
      this.host = host;
   }

   @Property(label="Port", required = true)
   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }

   @Property(label="Datacenter")
   public String getDatacenter() {
      if(datacenter == null || datacenter.isEmpty()) {
         return "datacenter1";
      }

      return datacenter;
   }

   public void setDatacenter(String datacenter) {
      this.datacenter = datacenter;
   }

   @Property(label="Use SSL")
   public boolean isSSL() {
      return ssl;
   }

   public void setSSL(boolean ssl) {
      this.ssl = ssl;
   }

   @Property(label="Keyspace")
   @PropertyEditor(tagsMethod="getKeyspaces", dependsOn={"host", "port", "user", "password"})
   public String getKeyspace() {
      return keyspace;
   }

   public void setKeyspace(String keyspace) {
      this.keyspace = keyspace;
   }

   @Property(label="User")
   public String getUser() {
      return user;
   }

   public void setUser(String user) {
      this.user = user;
   }

   @Property(label="Password", password=true)
   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Get the keyspace names in the cluster.
    */
   public String[] getKeyspaces() {
      if(host != null) {
         try(CqlSession session = CassandraRuntime.getCluster(this).build()) {
            Map<CqlIdentifier, KeyspaceMetadata> keyspaces = session.getMetadata().getKeyspaces();
            return keyspaces.values().stream()
               .map(KeyspaceMetadata::getName)
               .map(CqlIdentifier::asInternal)
               .toArray(String[]::new);
         }
         catch(Exception ex) {
            LOG.warn("Unable to connect to Cassandra: " + host + ":" + port, ex);
         }
      }

      return new String[0];
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" port=\"" + port + "\"");
      writer.print(" ssl=\"" + ssl + "\"");
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(host != null) {
         writer.println("<host><![CDATA[" + host + "]]></host>");
      }

      if(datacenter != null) {
         writer.println("<datacenter><![CDATA[" + datacenter + "]]></datacenter>");
      }

      if(user != null) {
         writer.println("<user><![CDATA[" + user + "]]></user>");
      }

      if(password != null) {
         writer.println("<password><![CDATA[" + Tool.encryptPassword(password) +
                        "]]></password>");
      }

      if(keyspace != null) {
         writer.println("<keyspace><![CDATA[" + keyspace + "]]></keyspace>");
      }
   }

   @Override
   public void parseAttributes(Element root) throws Exception {
      super.parseAttributes(root);
      String prop;

      if((prop = Tool.getAttribute(root, "port")) != null) {
         port = Integer.parseInt(prop);
      }

      ssl = "true".equals(Tool.getAttribute(root, "ssl"));
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      host = Tool.getChildValueByTagName(root, "host");
      datacenter = Tool.getChildValueByTagName(root, "datacenter");
      keyspace = Tool.getChildValueByTagName(root, "keyspace");
      user = Tool.getChildValueByTagName(root, "user");
      password = Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"));
   }

   @Override
   public boolean equals(Object obj) {
      try {
         CassandraDataSource ds = (CassandraDataSource) obj;

         return Objects.equals(host, ds.host) &&
            port == ds.port &&
            Objects.equals(keyspace, ds.keyspace) &&
            Objects.equals(user, ds.user) &&
            Objects.equals(password, ds.password) &&
            ssl == ds.ssl;
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String host;
   private int port = 9042;
   private String datacenter = "datacenter1";
   private String keyspace;
   private String user;
   private String password;
   private boolean ssl = false;

   private static final Logger LOG = LoggerFactory.getLogger(CassandraDataSource.class.getName());
}
