/*
 * inetsoft-onedrive - StyleBI is a business intelligence web application.
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
package inetsoft.uql.onedrive;

import inetsoft.uql.XFactory;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@View(vertical = true, value = {
   @View1("clientId"),
   @View1("clientSecret"),
   @View1(type = ViewType.LABEL, text = "redirect.uri.description", colspan = 2),
   @View1(type = ViewType.PANEL,
      align = ViewAlign.RIGHT,
      elements = {
         @View2(
            type = ViewType.BUTTON,
            text = "Authorize",
            button = @Button(
               type = ButtonType.OAUTH,
               method = "updateTokens",
               dependsOn = { "clientId", "clientSecret" },
               enabledMethod = "authorizeEnabled",
               oauth = @Button.OAuth
            )
         )
      }),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class OneDriveDataSource extends TabularDataSource<OneDriveDataSource>  implements OAuthDataSource {
   public static final String TYPE = "OneDrive";

   public OneDriveDataSource() {
      super(TYPE, OneDriveDataSource.class);
   }

   @Property(label = "Client ID", required = true)
   public String getClientId() {
      return clientId;
   }

   @SuppressWarnings("unused")
   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Property(label = "Client Secret", password = true, required = true)
   public String getClientSecret() {
      return clientSecret;
   }

   @SuppressWarnings("unused")
   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   @Override
   public String getAuthorizationUri() {
      return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
   }

   @Override
   public void setAuthorizationUri(String authorizationUri) {
      // no-op
   }

   @Property(label = "Token URI")
   public String getTokenUri() {
      return "https://login.microsoftonline.com/common/oauth2/v2.0/token";
   }

   public void setTokenUri(String tokenUri) {
      // no-op
   }

   public String getScope() {
      return "offline_access%20Files.Read.All";
   }

   public void setScope(String scope) {
      // no-op
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Access Token", password = true)
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Refresh Token", password = true)
   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Token Expiration")
   public long getTokenExpiration() {
      return tokenExpiration;
   }

   public void setTokenExpiration(long tokenExpiration) {
      this.tokenExpiration = tokenExpiration;
   }

   public void updateTokens(Tokens tokens) {
      setAccessToken(tokens.accessToken());
      setRefreshToken(tokens.refreshToken());
      setTokenExpiration(tokens.expiration());
   }

   protected void refreshTokens() {
      if(!isTokenExpired()) {
         return;
      }

      try {
         String flags = getOauthFlags();
         Set<String> flagsSet = new HashSet<>();

         if(flags != null && !flags.isEmpty()) {
            flagsSet.addAll(Arrays.asList(getOauthFlags().split(" ")));
         }

         Tokens tokens = AuthorizationClient.refresh(
            getServiceName(), getRefreshToken(), getClientId(), getClientSecret(), getTokenUri(),
            flagsSet, false, null);
         updateTokens(tokens);
      }
      catch(Exception e) {
         LOG.error("Failed to refresh access token", e);
         return;
      }

      if(this.getFullName() != null) {
         try {
            XFactory.getRepository().updateDataSource(this, getFullName());
         }
         catch(Exception e) {
            LOG.warn("Failed to save data source after refreshing token", e);
         }
      }
   }

   protected boolean isTokenExpired() {
      return tokenExpiration == 0L || refreshToken == null || refreshToken.isEmpty() ||
         Instant.now().isAfter(Instant.ofEpochMilli(tokenExpiration));
   }

   public boolean authorizeEnabled() {
      return clientId != null && clientSecret != null;
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

      writer.format("<tokenExpiration>%d</tokenExpiration>%n", tokenExpiration);
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

      String expires = Tool.getChildValueByTagName(element, "tokenExpiration");

      if(expires != null) {
         try {
            tokenExpiration = Long.parseLong(expires);
         }
         catch(NumberFormatException e) {
            LOG.warn("Invalid token expiration: {}", expires, e);
         }
      }
   }

   @Override
   public boolean equals(Object obj) {
      try {
         OneDriveDataSource ds = (OneDriveDataSource) obj;

         return Objects.equals(username, ds.username) &&
            Objects.equals(password, ds.password) &&
            Objects.equals(clientId, ds.clientId) &&
            Objects.equals(tenantId, ds.tenantId) &&
            Objects.equals(clientSecret, ds.clientSecret) &&
            Objects.equals(accessToken, ds.accessToken) &&
            Objects.equals(refreshToken, ds.refreshToken) &&
            Objects.equals(tokenExpiration, ds.tokenExpiration);
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
   private long tokenExpiration;

   private static final Logger LOG =
      LoggerFactory.getLogger(OneDriveDataSource.class.getName());
}
