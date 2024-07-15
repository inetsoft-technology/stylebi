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
package inetsoft.uql.util;

import com.google.gson.*;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/**
 * Base class for data source implementations that use a Google web API.
 */
@SuppressWarnings("unused")
public abstract class GoogleDataSource<SELF extends GoogleDataSource<SELF>>
   extends TabularDataSource<SELF>
{
   /**
    * Creates a new instance of <tt>GoogleDataSource</tt>.
    *
    * @param type the data source type.
    */
   protected GoogleDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
   }

   /**
    * Gets the authorization code.
    *
    * @return the authorization code.
    */
   public String getCode() {
      return code;
   }

   /**
    * Sets the authorization code.
    *
    * @param code the authorization code.
    */
   public void setCode(String code) {
      this.code = code;
   }

   /**
    * Gets the unique identifier for these credentials.
    *
    * @return the unique identifier.
    */
   public String getUserId() {
      return userId;
   }

   /**
    * Sets the unique identifier for these credentials.
    *
    * @param userId the unique identifier.
    */
   public void setUserId(String userId) {
      this.userId = userId;
   }

   /**
    * Gets the OAuth refresh token for the access token.
    *
    * @return the refresh token.
    */
   public String getRefreshToken() {
      return refreshToken;
   }

   /**
    * Sets the OAuth refresh token for the access token.
    *
    * @param refreshToken the refresh token.
    */
   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   /**
    * Gets the expiration timestamp for the access token.
    *
    * @return the expiration timestamp.
    */
   public long getExpiration() {
      return expiration;
   }

   /**
    * Sets the expiration timestamp for the access token.
    *
    * @param expiration the expiration timestamp.
    */
   public void setExpiration(long expiration) {
      this.expiration = expiration;
   }

   /**
    * Gets the OAuth2 access token.
    *
    * @return the access token.
    */
   public String getAccessToken() {
      return token;
   }

   /**
    * Sets the OAuth2 access token.
    *
    * @param token the access token.
    */
   public void setAccessToken(String token) {
      this.token = token;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      isAuthenticationCodeValid();

      if(code != null) {
         writer.println("<code><![CDATA[" + code + "]]></code>");
      }

      if(token != null) {
         writer.println("<token><![CDATA[" + token + "]]></token>");
      }

      if(refreshToken != null) {
         writer.println("<refreshToken><![CDATA[" + refreshToken + "]]></refreshToken>");
      }

      if(userId != null) {
         writer.println("<userId><![CDATA[" + userId + "]]></userId>");
      }

      writer.println("<expiration><![CDATA[" + expiration + "]]></expiration>");
   }

   /**
    * Checks that the authentication code is valid. If the authentication code
    * has not been changed, this method will return <tt>true</tt>.
    *
    * @return <tt>true</tt> if valid; <tt>false</tt> otherwise.
    */
   public boolean isAuthenticationCodeValid() {
      boolean result = true;

      if(originalCode == null || !originalCode.equals(code)) {
         result = false;

         if(code != null) {
            try {
               GoogleAuthentication auth =
                  new GoogleAuthentication(this, getClientId(), getClientSecret());
               auth.authorize(code, getClientId(), getClientSecret(), getScopes());
               originalCode = code;
               result = true;
            }
            catch(Exception e) {
               LOG.warn("Google authentication failed", e);
            }
         }
      }

      return result;
   }

   /**
    * Refreshes the OAuth2 access token.
    *
    * @return the refreshed token.
    */
   public String refreshToken() {
      String result = null;
      String data = "refresh_token=" + getRefreshToken() +
         "&client_id=" + getClientId() +
         "&client_secret=" + getClientSecret() +
         "&grant_type=refresh_token";
      byte[] body = data.getBytes();

      try {
         URL url = new URL("https://accounts.google.com/o/oauth2/token");
         HttpURLConnection conn = (HttpURLConnection) url.openConnection();
         conn.setDoOutput(true);
         conn.setFixedLengthStreamingMode(body.length);
         conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
         conn.getOutputStream().write(body);

         BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()));
         JsonParser parser = new JsonParser();
         JsonObject json = (JsonObject) parser.parse(reader);
         JsonElement token = json.get("access_token");

         result = (token == null) ? null : token.getAsString();
      }
      catch(Exception ex) {
         LOG.error("Failed to refresh token: " + getName(), ex);
      }

      token = result;
      return result;
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      code = Tool.getChildValueByTagName(root, "code");
      token = Tool.getChildValueByTagName(root, "token");
      refreshToken = Tool.getChildValueByTagName(root, "refreshToken");
      userId = Tool.getChildValueByTagName(root, "userId");
      expiration = Long.parseLong(Tool.getChildValueByTagName(root, "expiration"));
      originalCode = code;
   }

   /**
    * Gets the URL used to authorize this data source with the Google web
    * services.
    *
    * @return the authorization URL.
    */
   public String getAuthorizationUrl() {
      return GoogleAuthentication.getAuthorizationUrl(
         getClientId(), getClientSecret(), getScopes());
   }

   /**
    * Gets the Google client ID for this data source.
    *
    * @return the client identifier.
    */
   protected abstract String getClientId();

   /**
    * Gets the Google client secret for this data source.
    *
    * @return the client secret.
    */
   protected abstract String getClientSecret();

   /**
    * Gets the Google authorization scopes required by this data source.
    *
    * @return the scopes.
    */
   protected abstract String[] getScopes();

   @Override
   public boolean equals(Object obj) {
      try {
         GoogleDataSource ds = (GoogleDataSource) obj;

         return Objects.equals(code, ds.code) &&
            Objects.equals(originalCode, ds.originalCode) &&
            Objects.equals(token, ds.token) &&
            Objects.equals(userId, ds.userId) &&
            Objects.equals(refreshToken, ds.refreshToken) &&
            expiration == ds.expiration;
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String code;
   private String originalCode;
   private String token;
   private String userId = null;
   private String refreshToken = null;
   private long expiration = 0L;

   private static final Logger LOG = LoggerFactory.getLogger(GoogleDataSource.class.getName());
}
