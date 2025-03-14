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
package inetsoft.uql.rest;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ListedDataSource;
import inetsoft.uql.XFactory;
import inetsoft.uql.rest.auth.*;
import inetsoft.uql.rest.datasource.zohocrm.ZohoCRMDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@View(vertical=true, value={
   @View1("URL"),
   @View1("authType"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements = {
         @View2(value = "clientId", visibleMethod = "useCredentialForOauth"),
         @View2(value = "clientSecret", visibleMethod = "useCredentialForOauth"),
         @View2(value = "authorizationUri", visibleMethod = "useCredentialForOauth"),
         @View2(value = "tokenUri", visibleMethod = "useCredentialForOauth"),
         @View2(value = "scope", visibleMethod = "useCredentialForOauth"),
         @View2(value = "oauthFlags", visibleMethod = "useCredentialForOauth"),
         @View2(
            type = ViewType.BUTTON,
            text = "Authorize",
            visibleMethod = "isOauth",
            col = 1,
            button = @Button(
               type = ButtonType.OAUTH,
               method = "updateTokens",
               oauth = @Button.OAuth)
         ),
         @View2(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
            wrap = true, colspan = 2, visibleMethod ="displayAPIKeyTip"),
         @View2(value = "accessToken", visibleMethod = "isOauth"),
         @View2(value = "refreshToken", visibleMethod = "isOauth")
      }
   ),
   @View1(value="user", visibleMethod="useCredentialForBasicAuth"),
   @View1(value="password", visibleMethod="useCredentialForBasicAuth"),
   @View1(value="authURL", visibleMethod="useCredentialForTwoStepAuth"),
   @View1(value="authenticationHttpParameters", visibleMethod="isTwoStepAuth",
          verticalAlign= ViewAlign.TOP),
   @View1(value="authMethod", visibleMethod="isTwoStepAuth"),
   //Kerberos properties
   @View1(value =  "servicePrincipalName", visibleMethod = "isImpersonateValue"),
   @View1(value =  "constrainedDelegation", visibleMethod = "isImpersonateValue"),
   @View1(value = "configurationServiceName", visibleMethod = "isConstrainedDelegation"),
   @View1(value = "impersonationType", visibleMethod = "isConstrainedDelegation"),
   @View1(value = "impersonatePrincipal", visibleMethod = "isImpersonateValue"),
   @View1(value="contentType", visibleMethod="isBodyVisible"),
   @View1(value="body", visibleMethod="isBodyVisible"),
   @View1(type = ViewType.LABEL, text = "auth.token.example", col = 1, visibleMethod="isTwoStepAuth"),
   @View1(value="tokenPattern", visibleMethod="isTwoStepAuth"),
   @View1(type = ViewType.LABEL, text = "auth.token.help", col = 1, visibleMethod="isTwoStepAuth"),
   @View1(value="queryHttpParameters"),
})
public abstract class AbstractRestDataSource<SELF extends AbstractRestDataSource<SELF>>
   extends TabularDataSource<SELF> implements ListedDataSource, OAuthDataSource
{
   public AbstractRestDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD_OAUTH2_WITH_FLAGS;
   }

   @Property(label="Credential", required=true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getCredentialId() {
      return Objects.requireNonNullElse(getCredential().getId(), "");
   }

   @Property(label="Use Secret ID")
   @PropertyEditor(dependsOn = "authType")
   public boolean isUseCredentialId() {
      return super.isUseCredentialId();
   }

   @Override
   protected boolean supportCredentialId() {
      if(getCredentialType() != CredentialType.PASSWORD_OAUTH2_WITH_FLAGS) {
         return true;
      }

      AuthType type = getAuthType();
      return type == AuthType.BASIC || type == AuthType.OAUTH;
   }

   @Override
   public String getBaseType() {
      return "Rest";
   }

   @Property(label="URL", required = true)
   public String getURL() {
      return url;
   }

   public void setURL(String url) {
      this.url = url;
   }

   @Property(label="Authentication")
   @PropertyEditor(tags={"NONE", "BASIC", "OAUTH", "TWO_STEP", "KERBEROS"},
                   labels={"None", "Basic", "OAuth2/OpenID Connect", "Two-Step Token", "Kerberos"})
   public AuthType getAuthType() {
      return authType;
   }

   public void setAuthType(AuthType authType) {
      this.authType = authType;

      if(!supportToggleCredential() && getCredential() instanceof CloudCredential) {
         setCredential(createCredential(true));
      }
   }

   @Property(label = "Service Principal Name")
   public String getServicePrincipalName() {
      return servicePrincipalName;
   }

   public void setServicePrincipalName(String servicePrincipalName) {
      this.servicePrincipalName = servicePrincipalName;
   }

   @Property(label = "Use Constrained Delegation", required = true)
   public boolean isConstrainedDelegation() {
      return constrainedDelegation && isKerberos();
   }

   public void setConstrainedDelegation(boolean constrainedDelegation) {
      this.constrainedDelegation = constrainedDelegation;
   }

   /**
    * Name of the configuration from the JAAS login configuration file
    */
   @Property(label = "Configuration Service Name")
   @PropertyEditor(dependsOn = "constrainedDelegation")
   public String getConfigurationServiceName() {
      return configurationServiceName;
   }

   public void setConfigurationServiceName(String configurationServiceName) {
      this.configurationServiceName = configurationServiceName;
   }

   @Property(label = "Impersonation Type", required = true)
   @PropertyEditor(
      tags = {"STATIC", "PRINCIPAL", "PROPERTY"},
      labels = {"Static Value", "Principal Name", "Principal Property"}
   )
   public KerberosImpersonationType getImpersonationType() {
      return impersonationType;
   }

   public void setImpersonationType(KerberosImpersonationType impersonationType) {
      this.impersonationType = impersonationType;
   }

   @Property(label = "Principal to Impersonate", required = true)
   @PropertyEditor(dependsOn = "impersonationType")
   public String getImpersonatePrincipal() {
      return impersonatePrincipal;
   }

   public void setImpersonatePrincipal(String impersonatePrincipal) {
      this.impersonatePrincipal = impersonatePrincipal;
   }

   public boolean isKerberos() {
      return authType == AuthType.KERBEROS;
   }

   /**
    * @return true if we need to set a value for the impersonation
    */
   public boolean isImpersonateValue() {
      return impersonationType != KerberosImpersonationType.PRINCIPAL  && isKerberos();
   }

   @Property(label="User", required = true)
   @PropertyEditor(dependsOn="authType")
   public String getUser() {
      if(getCredential() instanceof PasswordCredential) {
         return ((PasswordCredential) getCredential()).getUser();
      }
      else if(getCredential() instanceof ApiKeyCredential) {
         return ((ApiKeyCredential) getCredential()).getApiKey();
      }

      return null;
   }

   public void setUser(String user) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setUser(user);
      }
      else if(getCredential() instanceof ApiKeyCredential) {
         ((ApiKeyCredential) getCredential()).setApiKey(user);
      }
   }

   @Property(label="Password", password=true)
   @PropertyEditor(dependsOn = "authType")
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

   @Property(label="Authentication URI")
   @PropertyEditor(dependsOn="authType")
   public String getAuthURL() {
      if(getCredential() instanceof OAuth2CredentialsGrant) {
         return ((OAuth2CredentialsGrant) getCredential()).getAuthorizationUri();
      }

      return null;
   }

   public void setAuthURL(String authURL) {
      if(getCredential() instanceof OAuth2CredentialsGrant) {
         ((OAuth2CredentialsGrant) getCredential()).setAuthorizationUri(authURL);
      }
   }

   @Property(label="Authentication HTTP Parameters")
   @PropertyEditor(dependsOn="authType")
   public HttpParameter[] getAuthenticationHttpParameters() {
      return authenticationHttpParameters;
   }

   public void setAuthenticationHttpParameters(HttpParameter[] parameters) {
      this.authenticationHttpParameters = parameters;
   }

   @Property(label="Auth HTTP Method")
   @PropertyEditor(tags={"GET", "POST"},
                   labels={"GET", "POST"},
                   dependsOn="authType")
   public AuthMethod getAuthMethod() {
      return authMethod;
   }

   public void setAuthMethod(AuthMethod authMethod) {
      this.authMethod = authMethod;
   }

   @Property(label="Content Type")
   @PropertyEditor(tags={"application/json", "application/xml", "application/x-www-form-urlencoded", "text/plain", "text/xml"},
                   labels={"application/json", "application/xml", "application/x-www-form-urlencoded", "text/plain", "text/xml"},
                   dependsOn={"authType, authMethod"})
   public String getContentType() {
      return contentType;
   }

   public void setContentType(String contentType) {
      this.contentType = contentType;
   }

   @Property(label="Request Body")
   @PropertyEditor(columns = 40,
                   rows = 16,
                   dependsOn={"authType", "authMethod"})
   public String getBody() {
      return body;
   }

   public void setBody(String body) {
      this.body = body;
   }

   @Property(label="Token Regex Pattern")
   @PropertyEditor(dependsOn="authType")
   public String getTokenPattern() {
      return tokenPattern;
   }

   public void setTokenPattern(String tokenPattern) {
      this.tokenPattern = tokenPattern;
   }

   @Property(label="Query HTTP Parameters")
   @PropertyEditor(dependsOn="authType")
   public HttpParameter[] getQueryHttpParameters() {
      return queryHttpParameters;
   }

   public void setQueryHttpParameters(HttpParameter[] parameters) {
      this.queryHttpParameters = parameters;
   }

   @SuppressWarnings("unused")
   public boolean isAuthEnabled() {
      return authType != AuthType.NONE;
   }

   @SuppressWarnings("unused")
   public boolean isBasicAuth() {
      return authType == AuthType.BASIC;
   }

   @SuppressWarnings("unused")
   public boolean isTwoStepAuth() {
      return authType == AuthType.TWO_STEP;
   }

   public boolean isOauth() {
      return getAuthType() == AuthType.OAUTH;
   }

   public boolean useCredentialForOauth() {
      return super.useCredential() && isOauth();
   }

   public boolean useCredentialForBasicAuth() {
      return super.useCredential() && isBasicAuth();
   }

   public boolean useCredentialForTwoStepAuth() {
      return super.useCredential() && isTwoStepAuth();
   }

   @SuppressWarnings("unused")
   public boolean isBodyVisible() {
      return isTwoStepAuth() && authMethod == AuthMethod.POST;
   }

   @Override
   public boolean isTypeConversionSupported() {
      return true;
   }

   protected HttpParameter getHttpParameter(String name, HttpParameter.ParameterType type) {
      return Arrays.stream(queryHttpParameters)
         .filter((param) -> param.getName().equals(name) && param.getType() == type)
         .findFirst()
         .orElse(null);
   }

   protected void setHttpParameter(String name, String value, HttpParameter.ParameterType type) {
      HttpParameter httpParameter = new HttpParameter();
      httpParameter.setName(name);
      httpParameter.setValue(value);
      httpParameter.setType(type);

      for(int i = 0; i < queryHttpParameters.length; i++) {
         if(queryHttpParameters[i].getName().equals(name)) {
            queryHttpParameters[i] = httpParameter;
            return;
         }
      }

      HttpParameter[] httpParameters = Arrays.copyOf(
         queryHttpParameters, queryHttpParameters.length + 1);
      httpParameters[httpParameters.length - 1] = httpParameter;
      queryHttpParameters = httpParameters;
   }

   @Property(label = "Client ID", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getClientId() {
      if(getCredential() instanceof ClientCredentials) {
         return ((ClientCredentials) getCredential()).getClientId();
      }
      else if(getCredential() instanceof ClientTokenCredential) {
         return ((ClientTokenCredential) getCredential()).getClientId();
      }

      return null;
   }

   public void setClientId(String clientId) {
      if(getCredential() instanceof ClientCredentials) {
         ((ClientCredentials) getCredential()).setClientId(clientId);
      }
      else if(getCredential() instanceof ClientTokenCredential) {
         ((ClientTokenCredential) getCredential()).setClientId(clientId);
      }
   }

   @Property(label = "Client Secret", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
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

   @PropertyEditor(enabled = false, dependsOn = "useCredentialId")
   @Property(label = "Authorization URI", required = true)
   public String getAuthorizationUri() {
      return authorizationUri;
   }

   public void setAuthorizationUri(String authorizationUri) {
      this.authorizationUri = authorizationUri;
   }

   @PropertyEditor(enabled = false, dependsOn = "useCredentialId")
   @Property(label = "Token URI", required = true)
   public String getTokenUri() {
      if(getCredential() instanceof OAuth2CredentialsGrant) {
         return ((OAuth2CredentialsGrant) getCredential()).getTokenUri();
      }

      return null;
   }

   public void setTokenUri(String tokenUri) {
      if(getCredential() instanceof OAuth2CredentialsGrant) {
         ((OAuth2CredentialsGrant) getCredential()).setTokenUri(tokenUri);
      }
   }

   @PropertyEditor(enabled = false, dependsOn = "useCredentialId")
   @Property(label = "Scope", required = true)
   public String getScope() {
      if(getCredential() instanceof OAuth2CredentialsGrant) {
         return ((OAuth2CredentialsGrant) getCredential()).getScope();
      }

      return null;
   }

   public void setScope(String scope) {
      if(getCredential() instanceof OAuth2CredentialsGrant) {
         ((OAuth2CredentialsGrant) getCredential()).setScope(scope);
      }
   }

   @PropertyEditor(enabled = false, dependsOn = "useCredentialId")
   @Property(label = "OAuth Flags")
   public String getOauthFlags() {
      if(getCredential() instanceof PasswordAndOAuth2WithFlagCredentialsGrant) {
         return ((PasswordAndOAuth2WithFlagCredentialsGrant) getCredential()).getOauthFlags();
      }

      return null;
   }

   public void setOauthFlags(String oauthFlags) {
      if(getCredential() instanceof PasswordAndOAuth2WithFlagCredentialsGrant) {
         ((PasswordAndOAuth2WithFlagCredentialsGrant) getCredential()).setOauthFlags(oauthFlags);
      }
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Access Token", required = true, password = true)
   public String getAccessToken() {
      if(getCredential() instanceof AccessTokenCredential) {
         return ((AccessTokenCredential) getCredential()).getAccessToken();
      }
      else if(getCredential() instanceof ClientTokenCredential) {
         return ((ClientTokenCredential) getCredential()).getAccessToken();
      }

      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      if(getCredential() instanceof AccessTokenCredential) {
         ((AccessTokenCredential) getCredential()).setAccessToken(accessToken);
      }
      else if(getCredential() instanceof ClientTokenCredential) {
         ((ClientTokenCredential) getCredential()).setAccessToken(accessToken);
      }

      this.accessToken = accessToken;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Refresh Token", required = true, password = true)
   public String getRefreshToken() {
      if(getCredential() instanceof RefreshTokenCredential) {
         return ((RefreshTokenCredential) getCredential()).getRefreshToken();
      }

      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      if(getCredential() instanceof RefreshTokenCredential) {
         ((RefreshTokenCredential) getCredential()).setRefreshToken(refreshToken);
      }
      else {
         this.refreshToken = refreshToken;
      }
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Token Expiration", required = true)
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

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(url != null) {
         writer.format("<url><![CDATA[%s]]></url>%n", url);
      }

      writer.format("<authType><![CDATA[%s]]></authType>%n", authType);
      writer.format("<authMethod><![CDATA[%s]]></authMethod>%n", authMethod);

      if(authURL != null) {
         writer.format("<authURL><![CDATA[%s]]></authURL>%n", authURL);
      }

      if(authenticationHttpParameters != null) {
         writer.println("<authenticationHttpParameters>");

         for(final HttpParameter parameter : authenticationHttpParameters) {
            if(parameter != null) {
               parameter.writeXML(writer);
            }
         }

         writer.println("</authenticationHttpParameters>");
      }

      if(contentType != null && authMethod != AuthMethod.GET) {
         writer.format("<contentType><![CDATA[%s]]></contentType>%n", contentType);
      }

      if(body != null) {
         writer.format("<body><![CDATA[%s]]></body>%n", body);
      }

      if(tokenPattern != null) {
         writer.format("<tokenPattern><![CDATA[%s]]></tokenPattern>%n", tokenPattern);
      }

      if(queryHttpParameters != null) {
         writer.println("<queryHttpParameters>");

         for(final HttpParameter parameter : queryHttpParameters) {
            if(parameter != null) {
               parameter.writeXML(writer);
            }
         }

         writer.println("</queryHttpParameters>");
      }

      if(servicePrincipalName != null) {
         writer.format("<servicePrincipalName>%s</servicePrincipalName>%n", servicePrincipalName);
      }

      writer.format("<constrainedDelegation>%s</constrainedDelegation>%n", constrainedDelegation);

      if(impersonatePrincipal != null) {
         writer.format("<impersonatePrincipal>%s</impersonatePrincipal>%n", impersonatePrincipal);
      }

      if(impersonationType != null) {
         writer.format("<impersonationType>%s</impersonationType>%n", impersonationType);
      }

      if(configurationServiceName != null) {
         writer.format("<configurationServiceName>%s</configurationServiceName>%n", configurationServiceName);
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

      if(oauthFlags != null) {
         writer.format("<oauthFlags><![CDATA[%s]]></oauthFlags>%n", oauthFlags);
      }

      //zoho datasource writes tokenExpiration directly
      if(!(this instanceof ZohoCRMDataSource)) {
         writer.format("<tokenExpiration>%d</tokenExpiration>%n", tokenExpiration);
      }

   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
      Element credentialNode = Tool.getChildNodeByTagName(root, "PasswordCredential");

      if(credentialNode != null) {
         getCredential().parseXML(credentialNode);
      }

      final String authTypeString = Tool.getChildValueByTagName(root, "authType");

      if(authTypeString != null && !"null".equals(authTypeString)) {
         this.authType = AuthType.valueOf(authTypeString);
      }

      final String authMethodString = Tool.getChildValueByTagName(root, "authMethod");

      if(authMethodString != null && !"null".equals(authMethodString)) {
         this.authMethod = AuthMethod.valueOf(authMethodString);
      }

      authURL = Tool.getChildValueByTagName(root, "authURL");

      final Element authParameters =
         Tool.getChildNodeByTagName(root, "authenticationHttpParameters");

      if(authParameters != null) {
         final NodeList parameters = Tool.getChildNodesByTagName(authParameters, "httpParameter");
         authenticationHttpParameters = new HttpParameter[parameters.getLength()];

         for(int i = 0; i < parameters.getLength(); i++) {
            final HttpParameter parameter = new HttpParameter();
            parameter.parseXML((Element) parameters.item(i));
            authenticationHttpParameters[i] = parameter;
         }
      }

      contentType = Tool.getChildValueByTagName(root, "contentType");
      body = Tool.getChildValueByTagName(root, "body");
      tokenPattern = Tool.getChildValueByTagName(root, "tokenPattern");

      final Element queryParameters = Tool.getChildNodeByTagName(root, "queryHttpParameters");

      if(queryParameters != null) {
         final NodeList parameters = Tool.getChildNodesByTagName(queryParameters, "httpParameter");
         queryHttpParameters = new HttpParameter[parameters.getLength()];

         for(int i = 0; i < parameters.getLength(); i++) {
            final HttpParameter parameter = new HttpParameter();
            parameter.parseXML((Element) parameters.item(i));
            queryHttpParameters[i] = parameter;
         }
      }

      servicePrincipalName = CoreTool.getChildValueByTagName(root, "servicePrincipalName");
      constrainedDelegation = "true".equals(CoreTool.getChildValueByTagName(root, "constrainedDelegation"));
      impersonatePrincipal = CoreTool.getChildValueByTagName(root, "impersonatePrincipal");
      final String impersonationType = CoreTool.getChildValueByTagName(root, "impersonationType");

      if(impersonationType != null) {
         this.impersonationType = KerberosImpersonationType.valueOf(impersonationType);
      }

      configurationServiceName = CoreTool.getChildValueByTagName(root, "configurationServiceName");
      authorizationUri = Tool.getChildValueByTagName(root, "authorizationUri");
      tokenUri = Tool.getChildValueByTagName(root, "tokenUri");
      scope = Tool.getChildValueByTagName(root, "scope");
      oauthFlags = Tool.getChildValueByTagName(root, "oauthFlags");
      String val = Tool.getChildValueByTagName(root, "accessToken");

      if(val != null) {
         accessToken = Tool.decryptPassword(val);
      }

      val = Tool.getChildValueByTagName(root, "refreshToken");

      if(val != null) {
         refreshToken = Tool.decryptPassword(val);
      }

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
      refreshTokens(false);
   }

   protected void refreshTokens(boolean useBasicAuth) {
      if(isTokenValid()) {
         return;
      }

      try {
         String flags = getOauthFlags();
         Set<String> flagsSet = new HashSet<>();

         if(flags != null && !flags.isEmpty()) {
            flagsSet.addAll(Arrays.asList(getOauthFlags().split(" ")));
         }

         Tokens tokens = AuthorizationClient.refresh(getServiceName(),
                                                     getRefreshToken(), getClientId(), getClientSecret(), getTokenUri(), flagsSet,
                                                     useBasicAuth, null);
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

      final AbstractRestDataSource that = (AbstractRestDataSource) o;

      if(!(getCredential() instanceof AccessTokenCredential) &&
         (!Objects.equals(getAccessToken(), that.getAccessToken()) ||
         !Objects.equals(getRefreshToken(), that.getRefreshToken())))
      {
         return false;
      }

      return Objects.equals(url, that.url) &&
         authType == that.authType &&
         authMethod == that.authMethod &&
         Objects.equals(authURL, that.authURL) &&
         Arrays.equals(authenticationHttpParameters, that.authenticationHttpParameters) &&
         Objects.equals(contentType, that.contentType) &&
         Objects.equals(body, that.body) &&
         Objects.equals(tokenPattern, that.tokenPattern) &&
         Arrays.equals(queryHttpParameters, that.queryHttpParameters) &&
         Objects.equals(servicePrincipalName, that.servicePrincipalName) &&
         Objects.equals(constrainedDelegation, that.constrainedDelegation) &&
         Objects.equals(impersonatePrincipal, that.impersonatePrincipal) &&
         Objects.equals(impersonationType, that.impersonationType) &&
         Objects.equals(configurationServiceName, that.configurationServiceName) &&
         tokenExpiration == that.tokenExpiration &&
         Objects.equals(authorizationUri, that.authorizationUri) &&
         Objects.equals(tokenUri, that.tokenUri) &&
         Objects.equals(scope, that.scope) &&
         Objects.equals(oauthFlags, that.oauthFlags);
   }

   @Override
   public AbstractRestDataSource clone() {
      final AbstractRestDataSource ds = (AbstractRestDataSource) super.clone();

      if(authenticationHttpParameters != null) {
         ds.authenticationHttpParameters = Arrays.stream(authenticationHttpParameters)
            .map(HttpParameter::clone)
            .toArray(HttpParameter[]::new);
      }

      if(queryHttpParameters != null) {
         ds.queryHttpParameters = Arrays.stream(queryHttpParameters)
            .map(HttpParameter::clone)
            .toArray(HttpParameter[]::new);
      }

      if(getCredential() != null) {
         ds.setCredential((Credential) Tool.clone(getCredential()));
      }

      return ds;
   }

   /**
    * @return true if the refresh token is valid and the token expiration is passed
    */
   protected boolean isTokenValid() {
      return tokenExpiration == 0L || getRefreshToken() == null || getRefreshToken().isEmpty() ||
         Instant.now().isBefore(Instant.ofEpochMilli(tokenExpiration));
   }

   private String url = "";
   private AuthType authType = AuthType.NONE;
   private AuthMethod authMethod = AuthMethod.GET;
   private String authURL;
   private HttpParameter[] authenticationHttpParameters = new HttpParameter[0];
   private String contentType = "application/json";
   private String body;
   private String tokenPattern;
   private HttpParameter[] queryHttpParameters = new HttpParameter[0];
   private String servicePrincipalName;
   private boolean constrainedDelegation;
   private String impersonatePrincipal;
   private String configurationServiceName;
   private KerberosImpersonationType impersonationType;
   private String authorizationUri;
   private String tokenUri;
   private String scope;
   private String oauthFlags;
   private String accessToken;
   private String refreshToken;
   private long tokenExpiration;
   private static final Logger LOG = LoggerFactory.getLogger(AbstractRestDataSource.class);
}
