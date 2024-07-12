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
package inetsoft.uql.rest.datasource.twitter;

import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.w3c.dom.Element;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.*;

@View(vertical = true, value = {
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = "twitter"))),
   @View1("oauthToken"),
   @View1(type = ViewType.PANEL, visibleMethod = "isVisible", elements = {
      @View2("consumerKey"),
      @View2("consumerSecret"),
      @View2("tokenSecret")
   })
})
public class TwitterDataSource extends OAuthEndpointJsonDataSource<TwitterDataSource> {
   static final String TYPE = "Rest.Twitter";
   public static final String TWITTER_API = "https://api.twitter.com";

   public TwitterDataSource() {
      super(TYPE, TwitterDataSource.class);
   }

   @Override
   public void updateTokens(Tokens tokens) {
      super.updateTokens(tokens);
      final Map<String, Object> properties = tokens.properties();

      if(properties != null) {
         this.consumerKey = (String) properties.get("consumer_key");
         final String consumerSecret = (String) properties.get("consumer_secret");
         this.consumerSecret = decryptSecret(consumerSecret, this.consumerKey);
         this.oauthToken = (String) properties.get("oauth_token");
         this.tokenSecret = (String) properties.get("token_secret");
      }
   }

   public boolean isVisible() {
      return false;
   }

   public void update(String suffix, Map<String, String> queryParameters) {
      // get suffix first so parameters are populated
      final String path = suffix.split("\\?")[0];
      final String url = TWITTER_API + path;
      authorizationString = new TwitterAuthorizationString(url, consumerKey, consumerSecret)
         .setOauthToken(oauthToken)
         .setOauthTokenSecret(tokenSecret)
         .setParameters(queryParameters)
         .build();
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name(HttpHeaders.AUTHORIZATION)
            .value(authorizationString)
            .build()
      };
   }

   @Override
   public double getRequestsPerSecond() {
      return 1;
   }

   @Override
   protected String getTestSuffix() {
      final String testSuffix = "/1.1/account/settings.json";
      authorizationString = new TwitterAuthorizationString(TWITTER_API + testSuffix,
                                                           consumerKey, consumerSecret)
         .setOauthToken(oauthToken)
         .setOauthTokenSecret(tokenSecret)
         .build();
      return testSuffix;
   }

   @Override
   public String getURL() {
      return TWITTER_API;
   }

   @Property(label = "Consumer Key", required = true, password = true)
   public String getConsumerKey() {
      return consumerKey;
   }

   public void setConsumerKey(String consumerKey) {
      this.consumerKey = consumerKey;
   }

   @Property(label = "Consumer Secret", required = true, password = true)
   public String getConsumerSecret() {
      return consumerSecret;
   }

   public void setConsumerSecret(String consumerSecret) {
      this.consumerSecret = consumerSecret;
   }

   @Property(label = "OAuth Token", required = true, password = true)
   public String getOauthToken() {
      return oauthToken;
   }

   public void setOauthToken(String oauthToken) {
      this.oauthToken = oauthToken;
   }

   @Property(label = "OAuth Token Secret", required = true, password = true)
   public String getTokenSecret() {
      return tokenSecret;
   }

   public void setTokenSecret(String tokenSecret) {
      this.tokenSecret = tokenSecret;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(oauthToken != null && !oauthToken.isEmpty()) {
         writer.format("<oauthToken>%s</oauthToken>%n", oauthToken);
      }

      if(tokenSecret != null && !tokenSecret.isEmpty()) {
         writer.format("<tokenSecret>%s</tokenSecret>%n", Tool.encryptPassword(tokenSecret));
      }

      if(consumerKey != null && !consumerKey.isEmpty()) {
         writer.format("<consumerKey>%s</consumerKey>%n", Tool.encryptPassword(consumerKey));
      }

      if(consumerSecret != null && !consumerSecret.isEmpty()) {
         writer.format("<consumerSecret>%s</consumerSecret>%n", Tool.encryptPassword(consumerSecret));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      oauthToken = CoreTool.getChildValueByTagName(root, "oauthToken");
      tokenSecret = Tool.decryptPassword(CoreTool.getChildValueByTagName(root, "tokenSecret"));
      consumerKey = Tool.decryptPassword(CoreTool.getChildValueByTagName(root, "consumerKey"));
      consumerSecret = Tool.decryptPassword(CoreTool.getChildValueByTagName(root, "consumerSecret"));
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

      TwitterDataSource that = (TwitterDataSource) o;
      return Objects.equals(consumerKey, that.consumerKey) &&
         Objects.equals(consumerSecret, that.consumerSecret) &&
         Objects.equals(oauthToken, that.oauthToken) &&
         Objects.equals(tokenSecret, that.tokenSecret);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), consumerKey, consumerSecret, oauthToken, tokenSecret);
   }

   private String decryptSecret(String encoded, String password) {
      try {
         final byte[] encrypted = Base64.getDecoder().decode(encoded);
         final byte[] key = password.getBytes(StandardCharsets.UTF_8);
         final SecretKeySpec spec = new SecretKeySpec(key, "Blowfish");
         final Cipher cipher = Cipher.getInstance("Blowfish");
         cipher.init(Cipher.DECRYPT_MODE, spec);
         return new String(cipher.doFinal(encrypted));
      }
      catch(Exception e) {
         LOG.warn("Failed to decrypt secret", e);
      }

      // just return and use as normal
      return encoded;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private String consumerKey;
   private String consumerSecret;
   private String oauthToken;
   // twitter allows token secret to be empty string when signing requests
   private String tokenSecret = "";
   private String authorizeUrl;
   private String authorizationString;
}
