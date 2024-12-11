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
package inetsoft.uql.rest.datasource.twitter;

import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.credential.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
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
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "oauthToken", visibleMethod = "useCredential"),
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
   protected CredentialType getCredentialType() {
      return CredentialType.CONSUMER_SECRET;
   }

   @Override
   public void updateTokens(Tokens tokens) {
      super.updateTokens(tokens);
      final Map<String, Object> properties = tokens.properties();
      TwitterCredential credential = (TwitterCredential) getCredential();

      if(properties != null) {
         credential.setConsumerKey((String)properties.get("consumer_key"));
         final String consumerSecret = (String) properties.get("consumer_secret");
         credential.setConsumerSecret(decryptSecret(consumerSecret, getConsumerKey()));
         credential.setOauthToken((String) properties.get("oauth_token"));
         credential.setTokenSecret((String) properties.get("token_secret"));
      }
   }

   public boolean isVisible() {
      return false;
   }

   public void update(String suffix, Map<String, String> queryParameters) {
      // get suffix first so parameters are populated
      final String path = suffix.split("\\?")[0];
      final String url = TWITTER_API + path;
      authorizationString = new TwitterAuthorizationString(url, getConsumerKey(), getConsumerSecret())
         .setOauthToken(getOauthToken())
         .setOauthTokenSecret(getTokenSecret())
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
                                                           getConsumerKey(), getConsumerSecret())
         .setOauthToken(getOauthToken())
         .setOauthTokenSecret(getTokenSecret())
         .build();
      return testSuffix;
   }

   @Override
   public String getURL() {
      return TWITTER_API;
   }

   @Property(label = "Consumer Key", required = true, password = true)
   public String getConsumerKey() {
      return ((TwitterCredential) getCredential()).getConsumerKey();
   }

   public void setConsumerKey(String consumerKey) {
      ((TwitterCredential) getCredential()).setConsumerKey(consumerKey);
   }

   @Property(label = "Consumer Secret", required = true, password = true)
   public String getConsumerSecret() {
      return ((TwitterCredential) getCredential()).getConsumerSecret();
   }

   public void setConsumerSecret(String consumerSecret) {
      ((TwitterCredential) getCredential()).setConsumerSecret(consumerSecret);
   }

   @Property(label = "OAuth Token", required = true, password = true)
   public String getOauthToken() {
      return ((TwitterCredential) getCredential()).getOauthToken();
   }

   public void setOauthToken(String oauthToken) {
      ((TwitterCredential) getCredential()).setOauthToken(oauthToken);
   }

   @Property(label = "OAuth Token Secret", required = true, password = true)
   public String getTokenSecret() {
      return ((TwitterCredential) getCredential()).getTokenSecret();
   }

   public void setTokenSecret(String tokenSecret) {
      ((TwitterCredential) getCredential()).setTokenSecret(tokenSecret);
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

      return o instanceof TwitterDataSource;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getConsumerKey(), getConsumerSecret(),
                          getOauthToken(), getTokenSecret());
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
   private String authorizeUrl;
   private String authorizationString;
}
