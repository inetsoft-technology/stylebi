/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest;

import inetsoft.uql.ListedDataSource;
import inetsoft.uql.XFactory;
import inetsoft.uql.rest.auth.*;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
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
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements = {
         @View2(value = "clientId", visibleMethod = "isOauth"),
         @View2(value = "clientSecret", visibleMethod = "isOauth"),
         @View2(value = "authorizationUri", visibleMethod = "isOauth"),
         @View2(value = "tokenUri", visibleMethod = "isOauth"),
         @View2(value = "scope", visibleMethod = "isOauth"),
         @View2(value = "oauthFlags", visibleMethod = "isOauth"),
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
         @View2(value = "accessToken", visibleMethod = "isOauth"),
         @View2(value = "refreshToken", visibleMethod = "isOauth")
      }
   ),
   @View1(value="user", visibleMethod="isBasicAuth"),
   @View1(value="password", visibleMethod="isBasicAuth"),
   @View1(value="authURL", visibleMethod="isTwoStepAuth"),
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
//      return true;
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
      return user;
   }

   public void setUser(String user) {
      this.user = user;
   }

   @Property(label="Password", password=true)
   @PropertyEditor(dependsOn = "authType")
   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   @Property(label="Authentication URI")
   @PropertyEditor(dependsOn="authType")
   public String getAuthURL() {
      return authURL;
   }

   public void setAuthURL(String authURL) {
      this.authURL = authURL;
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
   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Property(label = "Client Secret", required = true, password = true)
   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Authorization URI", required = true)
   public String getAuthorizationUri() {
      return authorizationUri;
   }

   public void setAuthorizationUri(String authorizationUri) {
      this.authorizationUri = authorizationUri;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Token URI", required = true)
   public String getTokenUri() {
      return tokenUri;
   }

   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Scope", required = true)
   public String getScope() {
      return scope;
   }

   public void setScope(String scope) {
      this.scope = scope;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "OAuth Flags")
   public String getOauthFlags() {
      return oauthFlags;
   }

   public void setOauthFlags(String oauthFlags) {
      this.oauthFlags = oauthFlags;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Access Token", required = true, password = true)
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Refresh Token", required = true, password = true)
   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
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

   public boolean authorizeEnabled() {
      return clientId != null && clientSecret != null;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(url != null) {
         writer.format("<url><![CDATA[%s]]></url>%n", url);
      }

      if(user != null) {
         writer.format("<user><![CDATA[%s]]></user>%n", user);
      }

      if(password != null) {
         writer.format("<password><![CDATA[%s]]></password>%n", Tool.encryptPassword(password));
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

      if(oauthFlags != null) {
         writer.format("<oauthFlags><![CDATA[%s]]></oauthFlags>%n", oauthFlags);
      }

      writer.format("<tokenExpiration>%d</tokenExpiration>%n", tokenExpiration);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
      user = Optional.ofNullable(Tool.getChildValueByTagName(root, "user")).orElse("");
      password = Optional.ofNullable(
         Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"))).orElse("");

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

      clientId = Tool.getChildValueByTagName(root, "clientId");
      clientSecret = Tool.decryptPassword(Tool.getChildValueByTagName(root, "clientSecret"));
      authorizationUri = Tool.getChildValueByTagName(root, "authorizationUri");
      tokenUri = Tool.getChildValueByTagName(root, "tokenUri");
      scope = Tool.getChildValueByTagName(root, "scope");
      oauthFlags = Tool.getChildValueByTagName(root, "oauthFlags");
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

      return Objects.equals(url, that.url) &&
         Objects.equals(user, that.user) &&
         Objects.equals(password, that.password) &&
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
         Objects.equals(clientId, that.clientId) &&
         Objects.equals(clientSecret, that.clientSecret) &&
         Objects.equals(authorizationUri, that.authorizationUri) &&
         Objects.equals(tokenUri, that.tokenUri) &&
         Objects.equals(scope, that.scope) &&
         Objects.equals(oauthFlags, that.oauthFlags) &&
         Objects.equals(accessToken, that.accessToken) &&
         Objects.equals(refreshToken, that.refreshToken);
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

      return ds;
   }

   /**
    * @return true if the refresh token is valid and the token expiration is passed
    */
   protected boolean isTokenValid() {
      return tokenExpiration == 0L || refreshToken == null || refreshToken.isEmpty() ||
         Instant.now().isBefore(Instant.ofEpochMilli(tokenExpiration));
   }

   private String url = "";
   private String user = "";
   private String password;
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
   private String clientId;
   private String clientSecret;
   private String authorizationUri;
   private String tokenUri;
   private String scope;
   private String oauthFlags;
   private String accessToken;
   private String refreshToken;
   private long tokenExpiration;
   private static final Logger LOG = LoggerFactory.getLogger(AbstractRestDataSource.class);
}
