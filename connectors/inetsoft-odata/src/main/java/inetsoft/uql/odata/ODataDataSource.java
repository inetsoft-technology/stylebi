/*
 * inetsoft-odata - StyleBI is a business intelligence web application.
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
package inetsoft.uql.odata;

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

@View(vertical=true, value={
      @View1("URL"),
      @View1(type=ViewType.LABEL, text="authentication.required.text", colspan = 2),
      @View1("user"),
      @View1("password"),
      @View1(type = ViewType.LABEL, text = "oauth.required.text", colspan = 2),
      @View1("clientId"),
      @View1("clientSecret"),
      @View1("scope"),
      @View1("authorizationUri"),
      @View1("tokenUri"),
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
                  dependsOn = { "clientId", "clientSecret", "user", "password", "tokenUri" },
                  enabledMethod = "authorizeEnabled",
                  oauth = @Button.OAuth
               )
            )
         }),
      @View1("accessToken"),
      @View1("refreshToken"),
      @View1("tokenExpiration")
   })
public class ODataDataSource extends TabularDataSource<ODataDataSource> implements OAuthDataSource {
   public static final String TYPE = "OData";

   public ODataDataSource() {
      super(TYPE, ODataDataSource.class);
   }

   @Property(label="URL")
   public String getURL() {
      return url;
   }

   public void setURL(String url) {
      if(this.url != null && !this.url.equals(url)) {
         this.odataVersion = null;
      }

      this.url = url;
   }

   public String getUser() {
      return user;
   }

   public void setUser(String user) {
      this.user = user;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public String getVersion() {
      return this.odataVersion;
   }

   public void setODataVersion(String version) {
      this.odataVersion = version;
   }

   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   public String getAuthorizationUri() {
      return authorizationUri;
   }

   public void setAuthorizationUri(String authorizationUri) {
      this.authorizationUri = authorizationUri;
   }

   public String getTokenUri() {
      return tokenUri;
   }

   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   public String getScope() {
      return scope;
   }

   public void setScope(String scope) {
      this.scope = scope;
   }

   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }
   
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

   public boolean authorizeEnabled() {
      return hasPopulatedOIDCFields() || hasPopulatedPasswordGrantFields();
   }

   private boolean hasPopulatedOIDCFields() {
      return clientId != null && clientSecret != null;
   }

   private boolean hasPopulatedPasswordGrantFields() {
      return user != null && password != null && tokenUri != null && authorizationUri == null;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

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

      if(odataVersion != null) {
         writer.println("<odataVersion><![CDATA[" + odataVersion + "]]></odataVersion>");
      }

      if(clientId != null) {
         writer.format("<clientId><![CDATA[%s]]></clientId>%n", clientId);
      }

      if(clientSecret != null) {
         writer.format(
            "<clientSecret><![CDATA[%s]]></clientSecret>%n", Tool.encryptPassword(clientSecret));
      }

      if(authorizationUri != null) {
         writer.format(
            "<authorizationUri><![CDATA[%s]]></authorizationUri>%n", authorizationUri);
      }

      if(tokenUri != null) {
         writer.format("<tokenUri><![CDATA[%s]]></tokenUri>%n", tokenUri);
      }

      if(scope != null) {
         writer.format("<scope><![CDATA[%s]]></scope>%n", scope);
      }

      if(accessToken != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(accessToken));
      }

      if(refreshToken != null) {
         writer.format(
            "<refreshToken><![CDATA[%s]]></refreshToken>%n", Tool.encryptPassword(refreshToken));
      }

      writer.format("<tokenExpiration>%d</tokenExpiration>%n", tokenExpiration);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
      user = Tool.getChildValueByTagName(root, "user");
      password = Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"));
      odataVersion = Tool.getChildValueByTagName(root, "odataVersion");
      clientId = Tool.getChildValueByTagName(root, "clientId");
      clientSecret = Tool.decryptPassword(Tool.getChildValueByTagName(root, "clientSecret"));
      authorizationUri = Tool.getChildValueByTagName(root, "authorizationUri");
      tokenUri = Tool.getChildValueByTagName(root, "tokenUri");
      scope = Tool.getChildValueByTagName(root, "scope");
      accessToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "accessToken"));
      refreshToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "refreshToken"));
      String value = Tool.getChildValueByTagName(root, "tokenExpiration");

      if(value != null) {
         try {
            tokenExpiration = Long.parseLong(value);
         }
         catch(NumberFormatException e) {
            LOG.warn("Invalid token expiration: {}", value, e);
         }
      }
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

   /**
    * @return true if the refresh token is valid and the token expiration is passed
    */
   protected boolean isTokenExpired() {
      return tokenExpiration == 0L || refreshToken == null || refreshToken.isEmpty() ||
         Instant.now().isBefore(Instant.ofEpochMilli(tokenExpiration));
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ODataDataSource that = (ODataDataSource) o;

      return tokenExpiration == that.tokenExpiration &&
         Objects.equals(url, that.url) &&
         Objects.equals(user, that.user) &&
         Objects.equals(password, that.password) &&
         Objects.equals(odataVersion, that.odataVersion) &&
         Objects.equals(clientId, that.clientId) &&
         Objects.equals(clientSecret, that.clientSecret) &&
         Objects.equals(authorizationUri, that.authorizationUri) &&
         Objects.equals(tokenUri, that.tokenUri) &&
         Objects.equals(scope, that.scope) &&
         Objects.equals(accessToken, that.accessToken) &&
         Objects.equals(refreshToken, that.refreshToken);
   }

   private String url;
   private String user;
   private String password;
   private String odataVersion;
   private String clientId;
   private String clientSecret;
   private String authorizationUri;
   private String tokenUri;
   private String scope;
   private String accessToken;
   private String refreshToken;
   private long tokenExpiration;

   private static final Logger LOG =
      LoggerFactory.getLogger(ODataDataSource.class.getName());
}
