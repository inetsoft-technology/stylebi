/*
 * inetsoft-googledoc - StyleBI is a business intelligence web application.
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
package inetsoft.uql.gdata;

import inetsoft.uql.ListedDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.Tool;
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
         oauth = @Button.OAuth(serviceName = "google-sheets")
      )),
      @View1("accessToken"),
      @View1("refreshToken"),
      @View1("tokenExpiration"),
      @View1("connectTimeout"),
      @View1("readTimeout")
   })
public class GDataDataSource extends TabularDataSource<GDataDataSource> implements ListedDataSource
{
   static final String TYPE = "GoogleDocs";

   public GDataDataSource() {
      super(TYPE, GDataDataSource.class);
   }

   @Property(label = "Access Token", password = true)
   @PropertyEditor(enabled = false)
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Property(label = "Refresh Token", password = true)
   @PropertyEditor(enabled = false)
   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @Property(label = "Token Expiration", password = true)
   @PropertyEditor(enabled = false)
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
      this.accessToken = tokens.accessToken();
      this.refreshToken = tokens.refreshToken();
      this.tokenExpiration = tokens.expiration();
   }

   @Override
   public boolean isTypeConversionSupported() {
      return true;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(accessToken != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n",
            Tool.encryptPassword(accessToken));
      }

      if(refreshToken != null) {
         writer.format(
            "<refreshToken><![CDATA[%s]]></refreshToken>%n",
            Tool.encryptPassword(refreshToken));
      }

      writer.format("<tokenExpiration>%d</tokenExpiration>%n", tokenExpiration);
      writer.format("<connectTimeout>%d</connectTimeout>%n", connectTimeout);
      writer.format("<readTimeout>%d</readTimeout>%n", readTimeout);
   }

   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element element;

      if((element = Tool.getChildNodeByTagName(tag, "accessToken")) != null) {
         accessToken = Tool.decryptPassword(Tool.getValue(element));
      }

      if((element = Tool.getChildNodeByTagName(tag, "refreshToken")) != null) {
         refreshToken = Tool.decryptPassword(Tool.getValue(element));
      }

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
      try {
         GDataDataSource ds = (GDataDataSource) obj;

         return Objects.equals(accessToken, ds.accessToken) &&
            Objects.equals(refreshToken, ds.refreshToken) &&
            tokenExpiration == ds.tokenExpiration &&
            connectTimeout == ds.connectTimeout &&
            readTimeout == ds.readTimeout;
      }
      catch(Exception ex) {
         return false;
      }
   }

   private int connectTimeout = 0;
   private int readTimeout = 0;
   private String accessToken;
   private String refreshToken;
   private long tokenExpiration;

   private static final Logger LOG = LoggerFactory.getLogger(GDataDataSource.class);
}
