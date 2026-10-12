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
package inetsoft.uql.rest.datasource.zohocrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.XRepository;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.CoreTool;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.credential.AuthorizationCodeGrant;
import inetsoft.util.credential.CredentialType;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static inetsoft.util.AbstractPasswordEncryption.MASTER_PREFIX;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "clientId", visibleMethod = "useCredential"),
   @View1(value = "clientSecret", visibleMethod = "useCredential"),
   @View1(value = "accountDomain", visibleMethod = "useCredential"),
   @View1(value = "authorizationCode", visibleMethod = "useCredential"),
   @View1(type = ViewType.LABEL, text = "authorization.instructions", col = 1, wrap = true),
   @View1(type = ViewType.BUTTON, text = "Authorize", col = 1, button = @Button(type = ButtonType.METHOD, method = "authorize")),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration"),
   @View1("URL")
})
public class ZohoCRMDataSource extends EndpointJsonDataSource<ZohoCRMDataSource> {
   static final String TYPE = "Rest.ZohoCRM";

   public ZohoCRMDataSource() {
      super(TYPE, ZohoCRMDataSource.class);
      setAuthType(AuthType.NONE);
      setURL("https://www.zohoapis.com");
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.AUTHORIZATION_CODE;
   }

   @Property(label = "Account Domain", required = true)
   @PropertyEditor(tagsMethod = "getAccountDomains", dependsOn = "useCredentialId")
   public String getAccountDomain() {
      return ((AuthorizationCodeGrant) getCredential()).getAccountDomain();
   }

   public void setAccountDomain(String accountDomain) {
      ((AuthorizationCodeGrant) getCredential()).setAccountDomain(accountDomain);
   }

   @Property(label = "Authorization Code", password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getAuthorizationCode() {
      return ((AuthorizationCodeGrant) getCredential()).getAuthorizationCode();
   }

   public void setAuthorizationCode(String authorizationCode) {
      ((AuthorizationCodeGrant) getCredential()).setAuthorizationCode(authorizationCode);
   }

   @Property(label = "Access Token", required = true, password = true)
   public String getAccessToken() {
      return super.getAccessToken();
   }

   @Property(label = "Refresh Token", required = true, password = true)
   public String getRefreshToken() {
      return super.getRefreshToken();
   }

   @Property(label = "Token Expiration", required = true)
   @PropertyEditor(enabled = false)
   public long getTokenExpiration() {
      return tokenExpiration;
   }

   public void setTokenExpiration(long tokenExpiration) {
      this.tokenExpiration = tokenExpiration;
   }

   @Property(label = "API Domain")
   @PropertyEditor(enabled = false)
   @Override
   public String getURL() {
      return super.getURL();
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens();
      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Zoho-oauthtoken " + getAccessToken())
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.format("<tokenExpiration><![CDATA[%d]]></tokenExpiration>%n", tokenExpiration);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
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

   @Override
   protected String getTestSuffix() {
      return "/crm/v2/settings/modules";
   }

   public String[][] getAccountDomains() {
      return new String[][] {
         { "United States", "https://accounts.zoho.com" },
         { "Australia", "https://accounts.zoho.com.au" },
         { "Europe", "https://accounts.zoho.com.eu" },
         { "India", "https://accounts.zoho.com.in" },
         { "China", "https://accounts.zoho.com.cn" }
      };
   }

   public void authorize(String sessionId) {
      if(isUndecryptable(getClientSecret())) {
         addUserMessage("zohocrm.undecryptable");
         return;
      }

      String missing = Tool.isEmptyString(getClientId()) ? "Client ID" :
         Tool.isEmptyString(getClientSecret()) ? "Client Secret" :
         Tool.isEmptyString(getAccountDomain()) ? "Account Domain" :
         Tool.isEmptyString(getAuthorizationCode()) ? "Authorization Code" : null;

      if(missing != null) {
         if("Authorization Code".equals(missing)) {
            addUserMessage("zohocrm.authorize.missingCode");
         }
         else {
            addUserMessage("zohocrm.authorize.missingField", getBundle().getString(missing));
         }

         return;
      }

      boolean success = updateTokens(
         new BasicNameValuePair("grant_type", "authorization_code"),
         new BasicNameValuePair("client_id", getClientId()),
         new BasicNameValuePair("client_secret", getClientSecret()),
         new BasicNameValuePair("code", getAuthorizationCode()));

      if(success) {
         // the code can only be used once, clear it so that it is not replayed or exported
         if(!isUseCredentialId()) {
            setAuthorizationCode(null);
         }

         addUserMessage("zohocrm.authorize.success");
      }
   }

   @Override
   protected void refreshTokens() {
      if(getRefreshToken() == null || getRefreshToken().isEmpty() || tokenExpiration == 0L ||
         Instant.ofEpochMilli(tokenExpiration).isAfter(Instant.now()))
      {
         return;
      }

      if(isUndecryptable(getClientSecret()) || isUndecryptable(getRefreshToken())) {
         LOG.warn(
            "The client secret or refresh token of data source {} could not be decrypted, " +
            "the master password has likely been changed", getFullName());
         addUserMessage("zohocrm.undecryptable");
         return;
      }

      boolean success = updateTokens(
         new BasicNameValuePair("grant_type", "refresh_token"),
         new BasicNameValuePair("client_id", getClientId()),
         new BasicNameValuePair("client_secret", getClientSecret()),
         new BasicNameValuePair("refresh_token", getRefreshToken()));

      if(!success || getFullName() == null) {
         return;
      }

      // Bug #77692, save the refreshed tokens onto the stored definition, not this runtime
      // instance whose variables may have been replaced with the values of the query
      final String accessToken = getAccessToken();
      final String refreshToken = getRefreshToken();
      final long expiration = getTokenExpiration();
      final String apiDomain = getURL();

      try {
         XRepository.getRepository().updateDataSourceTokens(this, stored -> {
            ZohoCRMDataSource zoho = (ZohoCRMDataSource) stored;
            zoho.setAccessToken(accessToken);
            zoho.setRefreshToken(refreshToken);
            zoho.setTokenExpiration(expiration);
            zoho.setURL(apiDomain);
         });
      }
      catch(Exception e) {
         LOG.warn("Failed to save data source with updated access tokens", e);
      }
   }

   /**
    * Updates the tokens from the Zoho token endpoint.
    *
    * @return {@code true} if the tokens were updated, {@code false} if the request failed, in
    *         which case a user message describing the failure has been added.
    */
   private boolean updateTokens(NameValuePair... parameters) {
      HttpPost request = new HttpPost(getAccountDomain() + "/oauth/v2/token");
      List<NameValuePair> form = Arrays.asList(parameters);
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);
      request.setEntity(entity);

      try(CloseableHttpClient client = HttpClients.createDefault();
          CloseableHttpResponse response = client.execute(request))
      {
         int status = response.getStatusLine().getStatusCode();

         if(status == 200) {
            String content = EntityUtils.toString(response.getEntity());
            JsonNode json = new ObjectMapper().readTree(content);

            if(json.has("access_token") && json.has("api_domain")) {
               setAccessToken(json.get("access_token").asText());
               setURL(json.get("api_domain").asText());

               if(json.has("refresh_token")) {
                  setRefreshToken(json.get("refresh_token").asText());
               }

               Duration duration = Duration.of(json.get("expires_in").asLong(), ChronoUnit.SECONDS);
               Instant instant = Instant.now().plus(duration);
               tokenExpiration = instant.toEpochMilli();
               return true;
            }
            else if(json.has("error")) {
               String error = json.get("error").asText();
               LOG.error("Failed to get access token: {}", error);

               if("invalid_code".equals(error)) {
                  addUserMessage("zohocrm.token.invalidCode");
               }
               else if("invalid_client".equals(error)) {
                  addUserMessage("zohocrm.token.invalidClient");
               }
               else {
                  addUserMessage("zohocrm.token.error", error);
               }
            }
            else {
               LOG.error("Failed to get access token, unexpected response");
               addUserMessage("zohocrm.token.unexpectedResponse");
            }
         }
         else {
            LOG.error(
               "Failed to get access token [{}]: {}",
               status, response.getStatusLine().getReasonPhrase());
            addUserMessage(
               "zohocrm.token.httpError", status, response.getStatusLine().getReasonPhrase());
         }
      }
      catch(IOException e) {
         LOG.error("Failed to get access token", e);
         addUserMessage("zohocrm.token.error", e.getMessage());
      }

      return false;
   }

   /**
    * Determines if a secret is still encrypted with the master password after it was loaded,
    * which means that it could not be decrypted on this server, for example because the data
    * source was imported from a server that uses a different master password.
    */
   private static boolean isUndecryptable(String secret) {
      return secret != null && secret.startsWith(MASTER_PREFIX);
   }

   private static ResourceBundle getBundle() {
      return ResourceBundle.getBundle(
         "inetsoft.uql.rest.datasource.zohocrm.Bundle", ThreadContext.getLocale());
   }

   private static void addUserMessage(String key, Object... args) {
      String message = getBundle().getString(key);
      CoreTool.addUserMessage(args.length == 0 ? message : MessageFormat.format(message, args));
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof ZohoCRMDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ZohoCRMDataSource that = (ZohoCRMDataSource) o;
      return tokenExpiration == that.tokenExpiration;
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), getClientId(), getClientSecret(), getAccountDomain(), getAccessToken(),
         getRefreshToken(), tokenExpiration);
   }

   private long tokenExpiration;

   private static final Logger LOG = LoggerFactory.getLogger(ZohoCRMDataSource.class);
}
