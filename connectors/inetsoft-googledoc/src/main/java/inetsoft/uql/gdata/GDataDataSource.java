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
package inetsoft.uql.gdata;

import inetsoft.uql.ListedDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(
   vertical = true,
   value = {
      @View1(type = ViewType.LABEL, text = "authorization.note", align = ViewAlign.FILL,
         wrap = true, colspan = 2),
      @View1(type = ViewType.BUTTON, text = "Sign In with Google", button = @Button(
         type = ButtonType.OAUTH,
         style = ButtonStyle.GOOGLE_AUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = "google-sheets-picker")
      )),
      @View1(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
         wrap = true, colspan = 2, visibleMethod ="displayAPIKeyTip"),
      @View1(value = "accessToken"),
      @View1(value = "refreshToken"),
      @View1(value = "tokenExpiration"),
      @View1("connectTimeout"),
      @View1("readTimeout")
   })
public class GDataDataSource extends TabularDataSource<GDataDataSource> implements ListedDataSource
{
   static final String TYPE = "GoogleDocs";

   public GDataDataSource() {
      super(TYPE, GDataDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.AUTH_TOKENS;
   }

   @Override
   protected boolean supportCredentialId() {
      return false;
   }

   @Property(label = "Access Token", password = true)
   @PropertyEditor(enabled = false, dependsOn = "useCredentialId")
   public String getAccessToken() {
      return ((AuthTokensCredential) getCredential()).getAccessToken();
   }

   public void setAccessToken(String accessToken) {
      ((AuthTokensCredential) getCredential()).setAccessToken(accessToken);
   }

   @Property(label = "Refresh Token", password = true)
   @PropertyEditor(enabled = false, dependsOn = "useCredentialId")
   public String getRefreshToken() {
      return ((AuthTokensCredential) getCredential()).getRefreshToken();
   }

   public void setRefreshToken(String refreshToken) {
      ((AuthTokensCredential) getCredential()).setRefreshToken(refreshToken);
   }

   @Property(label = "Token Expiration", password = true)
   @PropertyEditor(enabled = false, dependsOn = "useCredentialId")
   public long getTokenExpiration() {
      return tokenExpiration;
   }

   public void setTokenExpiration(long tokenExpiration) {
      this.tokenExpiration = tokenExpiration;
   }

   /**
    * Gets the amount of time to wait for a connection before failing.
    *
    * @return the connect timeout.
    */
   @Property(label = "Connect Timeout")
   public int getConnectTimeout() {
      return connectTimeout;
   }

   /**
    * Sets the amount of time to wait for a connection before failing.
    *
    * @param connectTimeout the connect timeout.
    */
   public void setConnectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
   }

   /**
    * Gets the amount of time to wait for a response before failing.
    *
    * @return the read timeout.
    */
   @Property(label = "Read Timeout")
   public int getReadTimeout() {
      return readTimeout;
   }

   /**
    * Sets the amount of time to wait for a response before failing.
    *
    * @param readTimeout the read timeout.
    */
   public void setReadTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
   }

   public void updateTokens(Tokens tokens) {
      AuthTokensCredential credential = (AuthTokensCredential) getCredential();
      credential.setAccessToken(tokens.accessToken());
      credential.setRefreshToken(tokens.refreshToken());
      this.tokenExpiration = tokens.expiration();
   }

   @Override
   public boolean isTypeConversionSupported() {
      return true;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.format("<tokenExpiration>%d</tokenExpiration>%n", tokenExpiration);
      writer.format("<connectTimeout>%d</connectTimeout>%n", connectTimeout);
      writer.format("<readTimeout>%d</readTimeout>%n", readTimeout);
   }

   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      String value;

      if((value = Tool.getChildValueByTagName(tag, "tokenExpiration")) != null) {
         try {
            tokenExpiration = Long.parseLong(value);
         }
         catch(NumberFormatException e) {
            LOG.warn("Invalid token expiration: {}", value, e);
         }
      }

      if((value = Tool.getChildValueByTagName(tag, "connectTimeout")) != null) {
         connectTimeout = Integer.parseInt(value);
      }

      if((value = Tool.getChildValueByTagName(tag, "readTimeout")) != null) {
         readTimeout = Integer.parseInt(value);
      }
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      try {
         GDataDataSource ds = (GDataDataSource) obj;

         return tokenExpiration == ds.tokenExpiration &&
            connectTimeout == ds.connectTimeout &&
            readTimeout == ds.readTimeout;
      }
      catch(Exception ex) {
         return false;
      }
   }

   private int connectTimeout = 0;
   private int readTimeout = 0;
   private long tokenExpiration;

   private static final Logger LOG = LoggerFactory.getLogger(GDataDataSource.class);
}
