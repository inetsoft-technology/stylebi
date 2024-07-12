/*
 * inetsoft-mongodb - StyleBI is a business intelligence web application.
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
package inetsoft.uql.mongodb;

import com.mongodb.*;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@View(vertical=true, value={
      @View1(type=ViewType.LABEL, text="mongodb.host.description", col=1, paddingLeft=3),
      @View1("host"),
      @View1("port"),
      @View1("SSL"),
      @View1("replicaSet"),
      @View1("DB"),
      @View1(type=ViewType.LABEL, text="mongodb.credentials.instructions", col=1, paddingLeft=3),
      @View1("user"),
      @View1("password"),
      @View1("authDB")
   })
public class MongoDataSource extends TabularDataSource<MongoDataSource> {
   public static final String TYPE = "Mongo";

   public MongoDataSource() {
      super(TYPE, MongoDataSource.class);
   }

   @Property(label="Host")
   public String getHost() {
      return host;
   }

   public void setHost(String host) {
      this.host = host;
   }

   @Property(label="Port")
   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }

   @Property(label="Database")
   @PropertyEditor(tagsMethod="getDatabaseNames", dependsOn={"host", "port", "user", "password"})
   public String getDB() {
      return db;
   }

   public void setDB(String db) {
      this.db = db;
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

   @Property(label="Authentication Database")
   public String getAuthDB() {
      return authDB;
   }

   public void setAuthDB(String db) {
      this.authDB = db;
   }

   @Property(label="SSL")
   public boolean isSSL() {
      return ssl;
   }

   public void setSSL(boolean ssl) {
      this.ssl = ssl;
   }

   @Property(label="ReplicaSet")
   public String getReplicaSet() {
      return replicaSet;
   }

   public void setReplicaSet(String replicaSet) {
      this.replicaSet = replicaSet;
   }

   @Override
   public boolean isTypeConversionSupported() {
      return true;
   }

   /**
    * Get a list all database names.
    */
   public String[] getDatabaseNames() {
      if(host != null) {
         try {
            MongoClient mongoClient = getMongoClient();
            List<String> names = new ArrayList<>();

            for(String name : mongoClient.listDatabaseNames()) {
               names.add(name);
            }

            return names.toArray(new String[0]);
         }
         catch(Exception ex) {
            LOG.warn("Failed to connect to MongoDB: " + host + ":" + port, ex);
            Tool.addUserMessage("Failed to connect to MongoDB: " + ex);
         }
      }

      return new String[0];
   }

   /**
    * Create a mongo client.
    */
   public MongoClient getMongoClient() throws Exception {
      MongoClient client;
      MongoCredential credential = null;

      if(user != null && user.length() > 0 && password != null) {
         String authDB = this.authDB;

         if(authDB == null) {
            authDB = (this.db == null) ? "admin" : this.db;
         }

         credential = MongoCredential.createCredential(user, authDB, password.toCharArray());
      }

      MongoClientOptions.Builder options = new MongoClientOptions.Builder();

      if(ssl) {
         options = options.sslEnabled(ssl);
      }

      if(replicaSet != null) {
         options = options.requiredReplicaSetName(replicaSet);
      }

      List<ServerAddress> hosts = getHosts();

      if(credential != null) {
         if(hosts.size() > 1) {
            client = new MongoClient(hosts, credential, options.build());
         }
         else {
            client = new MongoClient(hosts.get(0), credential, options.build());
         }
      }
      else {
         if(hosts.size() > 1) {
            client = new MongoClient(hosts, options.build());
         }
         else {
            client = new MongoClient(hosts.get(0), options.build());
         }
      }

      return client;
   }

   private List<ServerAddress> getHosts() {
      return Arrays.stream(host.split(","))
         .map(n -> n.split(":"))
         .map(p -> p.length > 1 ? new ServerAddress(p[0], Integer.parseInt(p[1]))
              : new ServerAddress(p[0], port))
         .collect(Collectors.toList());
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

      if(user != null) {
         writer.println("<user><![CDATA[" + user + "]]></user>");
      }

      if(password != null) {
         writer.println("<password><![CDATA[" + Tool.encryptPassword(password) +
                        "]]></password>");
      }

      if(authDB != null) {
         writer.println("<authDB><![CDATA[" + authDB + "]]></authDB>");
      }

      if(db != null) {
         writer.println("<db><![CDATA[" + db + "]]></db>");
      }

      if(replicaSet != null) {
         writer.println("<replicaSet><![CDATA[" + replicaSet + "]]></replicaSet>");
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
      db = Tool.getChildValueByTagName(root, "db");
      authDB = Tool.getChildValueByTagName(root, "authDB");
      user = Tool.getChildValueByTagName(root, "user");
      replicaSet = Tool.getChildValueByTagName(root, "replicaSet");
      password = Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"));
   }

   @Override
   public boolean equals(Object obj) {
      try {
         MongoDataSource dx = (MongoDataSource) obj;

         return Objects.equals(host, dx.host) &&
            Objects.equals(db, dx.db) &&
            Objects.equals(user, dx.user) &&
            Objects.equals(password, dx.password) &&
            Objects.equals(authDB, dx.authDB) &&
            Objects.equals(replicaSet, dx.replicaSet) &&
            port == dx.port && ssl == dx.ssl;
      }
      catch(ClassCastException ex) {
         return false;
      }
   }

   private String host;
   private int port = 27017;
   private String db;
   private String user;
   private String password;
   private String authDB;
   private boolean ssl;
   private String replicaSet;

   private static final Logger LOG = LoggerFactory.getLogger(MongoDataSource.class.getName());
}
