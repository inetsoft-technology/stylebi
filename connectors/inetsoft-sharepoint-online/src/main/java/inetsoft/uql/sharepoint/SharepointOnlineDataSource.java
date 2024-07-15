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
package inetsoft.uql.sharepoint;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("username"),
   @View1("password"),
   @View1("clientId"),
   @View1("tenantId"),
   @View1("clientSecret")
})
public class SharepointOnlineDataSource extends TabularDataSource<SharepointOnlineDataSource> {
   public static final String TYPE = "SharepointOnline";

   public SharepointOnlineDataSource() {
      super(TYPE, SharepointOnlineDataSource.class);
   }

   @Property(label = "Username", required = true)
   public String getUsername() {
      return username;
   }

   @SuppressWarnings("unused")
   public void setUsername(String username) {
      this.username = username;
   }

   @Property(label = "Password", password = true, required = true)
   public String getPassword() {
      return password;
   }

   @SuppressWarnings("unused")
   public void setPassword(String password) {
      this.password = password;
   }

   @Property(label = "Client ID", required = true)
   public String getClientId() {
      return clientId;
   }

   @SuppressWarnings("unused")
   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Property(label = "Tenant ID", required = true)
   public String getTenantId() {
      return tenantId;
   }

   @SuppressWarnings("unused")
   public void setTenantId(String tenantId) {
      this.tenantId = tenantId;
   }

   @Property(label = "Client Secret", password = true, required = true)
   public String getClientSecret() {
      return clientSecret;
   }

   @SuppressWarnings("unused")
   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   String getAccessToken() {
      return accessToken;
   }

   void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   String getRefreshToken() {
      return refreshToken;
   }

   void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   Instant getTokenExpires() {
      return tokenExpires;
   }

   void setTokenExpires(Instant tokenExpires) {
      this.tokenExpires = tokenExpires;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(username != null) {
         writer.format("<username><![CDATA[%s]]></username>%n", username);
      }

      if(password != null) {
         writer.format(
            "<password><![CDATA[%s]]></password>%n", Tool.encryptPassword(password));
      }

      if(clientId != null) {
         writer.format("<client-id><![CDATA[%s]]></client-id>%n", clientId);
      }

      if(tenantId != null) {
         writer.format("<tenant-id><![CDATA[%s]]></tenant-id>%n", tenantId);
      }

      if(clientSecret != null) {
         writer.format(
            "<client-secret><![CDATA[%s]]></client-secret>%n", Tool.encryptPassword(clientSecret));
      }

      if(accessToken != null) {
         writer.format(
            "<access-token><![CDATA[%s]]></access-token>%n", Tool.encryptPassword(accessToken));
      }

      if(refreshToken != null) {
         writer.format(
            "<refresh-token><![CDATA[%s]]></refresh-token>%n", Tool.encryptPassword(refreshToken));
      }

      if(tokenExpires != null) {
         writer.format("<token-expires>%s</token-expires>%n", tokenExpires);
      }
   }

   @Override
   protected void parseContents(Element element) throws Exception {
      super.parseContents(element);
      username = Tool.getChildValueByTagName(element, "username");
      password = Tool.decryptPassword(Tool.getChildValueByTagName(element, "password"));
      clientId = Tool.getChildValueByTagName(element, "client-id");
      tenantId = Tool.getChildValueByTagName(element, "tenant-id");
      clientSecret = Tool.decryptPassword(Tool.getChildValueByTagName(element, "client-secret"));
      accessToken = Tool.decryptPassword(Tool.getChildValueByTagName(element, "access-token"));
      refreshToken = Tool.decryptPassword(Tool.getChildValueByTagName(element, "refresh-token"));

      String expires = Tool.getChildValueByTagName(element, "token-expires");

      if(expires != null) {
         tokenExpires = Instant.parse(expires);
      }
   }

   @Override
   public boolean equals(Object obj) {
      try {
         SharepointOnlineDataSource ds = (SharepointOnlineDataSource) obj;

         return Objects.equals(username, ds.username) &&
            Objects.equals(password, ds.password) &&
            Objects.equals(clientId, ds.clientId) &&
            Objects.equals(tenantId, ds.tenantId) &&
            Objects.equals(clientSecret, ds.clientSecret) &&
            Objects.equals(accessToken, ds.accessToken) &&
            Objects.equals(refreshToken, ds.refreshToken) &&
            Objects.equals(tokenExpires, ds.tokenExpires);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String username;
   private String password;
   private String clientId;
   private String tenantId;
   private String clientSecret;
   private String accessToken;
   private String refreshToken;
   private Instant tokenExpires;
}
