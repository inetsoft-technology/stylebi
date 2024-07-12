/*
 * inetsoft-r - StyleBI is a business intelligence web application.
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
package com.inetsoft.connectors;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Data source for the R connector.
 */
@View(vertical = true, value = {
   @View1("url"),
   @View1("port"),
   @View1("user"),
   @View1("password")
})
public class RDataSource extends TabularDataSource<RDataSource> {
   public static final String TYPE = "R";

   public RDataSource() {
      super(TYPE, RDataSource.class);
      this.port = 6311;
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   @Property(label="URL")
   public String getUrl() {
      return url;
   }

   public void setUrl(String url) {
      this.url = url;
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

   @Property(label="Port")
   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<port><![CDATA[" + port + "]]></port>");

      if(url != null) {
         writer.println("<url><![CDATA[" + url + "]]></url>");
      }

      if(user != null) {
         writer.println("<user><![CDATA[" + user + "]]></user>");
      }

      if(password != null) {
         writer.println("<password><![CDATA[" + Tool.encryptPassword(password) +
                           "]]></password>");
      }
   }

   @Override
   public void parseAttributes(Element root) throws Exception {
      super.parseAttributes(root);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
      user = Tool.getChildValueByTagName(root, "user");
      password = Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"));

      String portStr = Tool.getChildValueByTagName(root, "port");

      if(portStr != null) {
         port = Integer.parseInt(portStr);
      }
   }

   @Override
   public boolean equals(Object obj) {
      try {
         RDataSource ds = (RDataSource) obj;

         return Objects.equals(url, ds.url) &&
            port == ds.port &&
            Objects.equals(user, ds.user) &&
            Objects.equals(password, ds.password);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String url;
   private int port;
   private String user;
   private String password;
}
