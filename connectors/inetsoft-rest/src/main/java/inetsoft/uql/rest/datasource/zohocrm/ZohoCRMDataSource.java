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
import inetsoft.uql.XFactory;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
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
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@View(vertical = true, value = {
   @View1("clientId"),
   @View1("clientSecret"),
   @View1("accountDomain"),
   @View1("authorizationCode"),
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

   @Property(label = "Account Domain", required = true)
   @PropertyEditor(tagsMethod = "getAccountDomains")
   public String getAccountDomain() {
      return accountDomain;
   }

   public void setAccountDomain(String accountDomain) {
      this.accountDomain = accountDomain;
   }

   @Property(label = "Authorization Code", password = true)
   public String getAuthorizationCode() {
      return authorizationCode;
   }

   public void setAuthorizationCode(String authorizationCode) {
      this.authorizationCode = authorizationCode;
   }

   @Property(label = "Access Token", required = true, password = true)
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Property(label = "Refresh Token", required = true, password = true)
   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @Property(label = "Token Expiration", required = true)
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
            .value("Zoho-oauthtoken " + accessToken)
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

      if(clientId != null) {
         writer.format("<clientId><![CDATA[%s]]></clientId>%n", clientId);
      }

      if(clientSecret != null) {
         writer.format(
            "<clientSecret><![CDATA[%s]]></clientSecret>%n", Tool.encryptPassword(clientSecret));
      }

      if(accountDomain != null) {
         writer.format("<accountDomain><![CDATA[%s]]></accountDomain>%n", accountDomain);
      }

      if(accessToken != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(accessToken));
      }

      if(refreshToken != null) {
         writer.format(
            "<refreshToken><![CDATA[%s]]></refreshToken>%n", Tool.encryptPassword(refreshToken));
      }

      writer.format("<tokenExpiration><![CDATA[%d]]></tokenExpiration>%n", tokenExpiration);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      clientId = Tool.getChildValueByTagName(root, "clientId");
      clientSecret = Tool.decryptPassword(Tool.getChildValueByTagName(root, "clientSecret"));
      accountDomain = Tool.getChildValueByTagName(root, "accountDomain");
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
      if(clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty() ||
         accountDomain == null || accountDomain.isEmpty() ||
         authorizationCode == null || authorizationCode.isEmpty())
      {
         return;
      }

      updateTokens(
         new BasicNameValuePair("grant_type", "authorization_code"),
         new BasicNameValuePair("client_id", clientId),
         new BasicNameValuePair("client_secret", clientSecret),
         new BasicNameValuePair("code", authorizationCode));
   }

   @Override
   protected void refreshTokens() {
      if(refreshToken == null || refreshToken.isEmpty() || tokenExpiration == 0L ||
         Instant.ofEpochMilli(tokenExpiration).isAfter(Instant.now()))
      {
         return;
      }

      updateTokens(
         new BasicNameValuePair("grant_type", "refresh_token"),
         new BasicNameValuePair("client_id", clientId),
         new BasicNameValuePair("client_secret", clientSecret),
         new BasicNameValuePair("refresh_token", refreshToken));

      try {
         XFactory.getRepository().updateDataSource(this, getFullName());
      }
      catch(Exception e) {
         LOG.warn("Failed to save data source with updated access tokens", e);
      }
   }

   private void updateTokens(NameValuePair... parameters) {
      HttpPost request = new HttpPost(accountDomain + "/oauth/v2/token");
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
               accessToken = json.get("access_token").asText();
               setURL(json.get("api_domain").asText());

               if(json.has("refresh_token")) {
                  refreshToken = json.get("refresh_token").asText();
               }

               Duration duration = Duration.of(json.get("expires_in").asLong(), ChronoUnit.SECONDS);
               Instant instant = Instant.now().plus(duration);
               tokenExpiration = instant.toEpochMilli();
            }
            else {
               String error = json.get("error").asText();
               LOG.error("Failed to get access token: {}", error);
            }
         }
         else {
            LOG.error(
               "Failed to get access token [{}]: {}",
               status, response.getStatusLine().getReasonPhrase());
         }
      }
      catch(IOException e) {
         LOG.error("Failed to get access token", e);
      }
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
      return tokenExpiration == that.tokenExpiration &&
         Objects.equals(clientId, that.clientId) &&
         Objects.equals(clientSecret, that.clientSecret) &&
         Objects.equals(accountDomain, that.accountDomain) &&
         Objects.equals(accessToken, that.accessToken) &&
         Objects.equals(refreshToken, that.refreshToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), clientId, clientSecret, accountDomain, accessToken, refreshToken,
         tokenExpiration);
   }

   private String clientId;
   private String clientSecret;
   private String accountDomain;
   private String authorizationCode;
   private String accessToken;
   private String refreshToken;
   private long tokenExpiration;

   private static final Logger LOG = LoggerFactory.getLogger(ZohoCRMDataSource.class);
}
