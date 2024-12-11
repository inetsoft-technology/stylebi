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
package com.inetsoft.connectors;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Data source for the R connector.
 */
@View(vertical = true, value = {
   @View1("url"),
   @View1("port"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class RDataSource extends TabularDataSource<RDataSource> {
   public static final String TYPE = "R";

   public RDataSource() {
      super(TYPE, RDataSource.class);
      this.port = 6311;
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD;
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
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getUser() {
      return ((PasswordCredential) getCredential()).getUser();
   }

   public void setUser(String user) {
      ((PasswordCredential) getCredential()).setUser(user);
   }

   @Property(label="Password", password=true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getPassword() {
      return ((PasswordCredential) getCredential()).getPassword();
   }

   public void setPassword(String password) {
      ((PasswordCredential) getCredential()).setPassword(password);
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
   }

   @Override
   public void parseAttributes(Element root) throws Exception {
      super.parseAttributes(root);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
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
            Objects.equals(getCredential(), ds.getCredential());
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String url;
   private int port;
}
