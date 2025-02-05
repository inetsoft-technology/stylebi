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
package inetsoft.uql.odata;

import inetsoft.uql.XFactory;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@View(vertical=true, value={
      @View1("URL"),
      @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
      @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
      @View1(type=ViewType.LABEL, text="authentication.required.text", colspan = 2),
      @View1(value = "user", visibleMethod = "useCredential"),
      @View1(value = "password", visibleMethod = "useCredential"),
      @View1(type = ViewType.LABEL, text = "oauth.required.text", colspan = 2),
      @View1(value = "odataClientId", visibleMethod = "useCredential"),
      @View1(value = "odataClientSecret", visibleMethod = "useCredential"),
      @View1(value = "odataScope", visibleMethod = "useCredential"),
      @View1(value = "odataAuthorizationUri", visibleMethod = "useCredential"),
      @View1(value = "odataTokenUri", visibleMethod = "useCredential"),
      @View1(type = ViewType.LABEL, text = "redirect.uri.description", colspan = 2, visibleMethod = "useCredential"),
      @View1(type = ViewType.PANEL,
         align = ViewAlign.RIGHT,
         elements = {
            @View2(
               type = ViewType.BUTTON,
               text = "Authorize",
               button = @Button(
                  type = ButtonType.OAUTH,
                  method = "updateTokens",
                  dependsOn = { "odataClientId", "odataClientSecret", "user", "password", "odataTokenUri", "credentialId"},
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

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD_OAUTH2;
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

   @PropertyEditor(dependsOn = "useCredentialId")
   public String getUser() {
      if(getCredential() instanceof PasswordCredential) {
         return ((PasswordCredential) getCredential()).getUser();
      }

      return null;
   }

   public void setUser(String user) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setUser(user);
      }
   }

   @PropertyEditor(dependsOn = "useCredentialId")
   public String getPassword() {
      if(getCredential() instanceof PasswordCredential) {
         return ((PasswordCredential) getCredential()).getPassword();
      }

      return null;
   }

   public void setPassword(String password) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setPassword(password);
      }
   }

   public String getVersion() {
      return this.odataVersion;
   }

   public void setODataVersion(String version) {
      this.odataVersion = version;
   }

   @PropertyEditor(dependsOn = "useCredentialId")
   @Property(label = "Client ID")
   public String getOdataClientId() {
      return getClientId();
   }

   public void setOdataClientId(String clientId) {
      setClientId(clientId);
   }

   public String getClientId() {
      if(getCredential() instanceof ClientCredentials) {
         return ((ClientCredentials) getCredential()).getClientId();
      }

      return null;
   }

   public void setClientId(String clientId) {
      if(getCredential() instanceof ClientCredentials) {
         ((ClientCredentials) getCredential()).setClientId(clientId);
      }
   }

   @PropertyEditor(dependsOn = "useCredentialId")
   @Property(label = "Client Secret", password = true)
   public String getOdataClientSecret() {
      return getClientSecret();
   }

   public void setOdataClientSecret(String clientSecret) {
      setClientSecret(clientSecret);
   }

   public String getClientSecret() {
      if(getCredential() instanceof ClientCredentials) {
         return ((ClientCredentials) getCredential()).getClientSecret();
      }

      return null;
   }

   public void setClientSecret(String clientSecret) {
      if(getCredential() instanceof ClientCredentials) {
         ((ClientCredentials) getCredential()).setClientSecret(clientSecret);
      }
   }

   @PropertyEditor(dependsOn = "useCredentialId")
   @Property(label = "Authorization URI")
   public String getOdataAuthorizationUri() {
      return getAuthorizationUri();
   }

   public void setOdataAuthorizationUri(String authorizationUri) {
      setAuthorizationUri(authorizationUri);
   }

   public String getAuthorizationUri() {
      return ((OAuth2CredentialsGrant) getCredential()).getAuthorizationUri();
   }

   public void setAuthorizationUri(String authorizationUri) {
      ((OAuth2CredentialsGrant) getCredential()).setAuthorizationUri(authorizationUri);
      this.authorizationUri = authorizationUri;
   }

   @PropertyEditor(dependsOn = "useCredentialId")
   @Property(label = "Token URI")
   public String getOdataTokenUri() {
      return getTokenUri();
   }

   public void setOdataTokenUri(String tokenUri) {
      setTokenUri(tokenUri);
   }

   public String getTokenUri() {
      return ((OAuth2CredentialsGrant) getCredential()).getTokenUri();
   }

   public void setTokenUri(String tokenUri) {
      ((OAuth2CredentialsGrant) getCredential()).setTokenUri(tokenUri);
   }

   @PropertyEditor(dependsOn = "useCredentialId")
   @Property(label = "Scope")
   public String getOdataScope() {
      return getScope();
   }

   public void setOdataScope(String scope) {
      setScope(scope);
   }

   public String getScope() {
      return ((OAuth2CredentialsGrant) getCredential()).getScope();
   }

   public void setScope(String scope) {
      ((OAuth2CredentialsGrant) getCredential()).setScope(scope);
   }

   public String getAccessToken() {
      if(getCredential() instanceof AccessTokenCredential) {
         return ((AccessTokenCredential) getCredential()).getAccessToken();
      }

      return null;
   }

   public void setAccessToken(String accessToken) {
      if(getCredential() instanceof AccessTokenCredential) {
         ((AccessTokenCredential) getCredential()).setAccessToken(accessToken);
      }
   }

   public String getRefreshToken() {
      if(getCredential() instanceof RefreshTokenCredential) {
         return ((RefreshTokenCredential) getCredential()).getRefreshToken();
      }

      return null;
   }

   public void setRefreshToken(String refreshToken) {
      if(getCredential() instanceof RefreshTokenCredential) {
         ((RefreshTokenCredential) getCredential()).setRefreshToken(refreshToken);
      }
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
      return getClientId() != null && getClientSecret() != null;
   }

   private boolean hasPopulatedPasswordGrantFields() {
      return getUser() != null && getPassword() != null && tokenUri != null && authorizationUri == null;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(url != null) {
         writer.println("<url><![CDATA[" + url + "]]></url>");
      }

      if(odataVersion != null) {
         writer.println("<odataVersion><![CDATA[" + odataVersion + "]]></odataVersion>");
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

      writer.format("<tokenExpiration>%d</tokenExpiration>%n", tokenExpiration);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
      odataVersion = Tool.getChildValueByTagName(root, "odataVersion");
      authorizationUri = Tool.getChildValueByTagName(root, "authorizationUri");
      tokenUri = Tool.getChildValueByTagName(root, "tokenUri");
      scope = Tool.getChildValueByTagName(root, "scope");
      String value = Tool.getChildValueByTagName(root, "tokenExpiration");

      if(value != null) {
         try {
            tokenExpiration = Long.parseLong(value);
         }
         catch(NumberFormatException e) {
            LOG.warn("Invalid token expiration: {}", value, e);
         }
      }

      Element credentialNode = Tool.getChildNodeByTagName(root, "PasswordCredential");

      if(credentialNode != null) {
         getCredential().parseXML(credentialNode);
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
      return tokenExpiration == 0L || getRefreshToken() == null || getRefreshToken().isEmpty() ||
         Instant.now().isBefore(Instant.ofEpochMilli(tokenExpiration));
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!super.equals(o)  || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ODataDataSource that = (ODataDataSource) o;

      return tokenExpiration == that.tokenExpiration &&
         Objects.equals(url, that.url) &&
         Objects.equals(odataVersion, that.odataVersion) &&
         Objects.equals(authorizationUri, that.authorizationUri) &&
         Objects.equals(tokenUri, that.tokenUri) &&
         Objects.equals(scope, that.scope);
   }

   private String url;
   private String odataVersion;
   private String authorizationUri;
   private String tokenUri;
   private String scope;
   private long tokenExpiration;

   private static final Logger LOG =
      LoggerFactory.getLogger(ODataDataSource.class.getName());
}
