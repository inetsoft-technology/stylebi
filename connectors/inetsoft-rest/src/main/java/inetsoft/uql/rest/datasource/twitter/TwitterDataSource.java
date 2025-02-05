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

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.credential.CredentialType;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "clientId", visibleMethod = "useCredential"),
   @View1(value = "clientSecret", visibleMethod = "useCredential"),
   @View1(type = ViewType.LABEL, text = "redirect.uri.description", colspan = 2),
   @View1(type = ViewType.PANEL,
      align = ViewAlign.RIGHT,
      elements = {
         @View2(
            type = ViewType.BUTTON,
            text = "Authorize",
            button = @Button(
               type = ButtonType.OAUTH,
               method = "updateTokens",
               dependsOn = { "clientId", "clientSecret", "credentialId" },
               enabledMethod = "authorizeEnabled",
               oauth = @Button.OAuth
            )
         )
      }),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class TwitterDataSource extends OAuthEndpointJsonDataSource<TwitterDataSource> {
   static final String TYPE = "Rest.Twitter";
   public static final String TWITTER_API = "https://api.twitter.com";

   public TwitterDataSource() {
      super(TYPE, TwitterDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.CLIENT;
   }

   @Property(label = "Client ID", required = true)
   @Override
   public String getClientId() {
      return super.getClientId();
   }

   @Property(label = "Client Secret", required = true, password = true)
   @Override
   public String getClientSecret() {
      return super.getClientSecret();
   }

   @Override
   public String getAuthorizationUri() {
      return "https://x.com/i/oauth2/authorize";
   }

   @Override
   public void setAuthorizationUri(String authorizationUri) {
      // no-op
   }

   @Override
   public String getTokenUri() {
      return "https://api.x.com/2/oauth2/token";
   }

   @Override
   public void setTokenUri(String tokenUri) {
      // no-op
   }

   @Override
   public String getScope() {
      return "offline.access tweet.read users.read follows.read space.read mute.read like.read " +
         "list.read block.read bookmark.read";
   }

   @Override
   public void setScope(String scope) {
      // no-op
   }

   @Override
   public String getURL() {
      return TWITTER_API;
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public double getRequestsPerSecond() {
      return 1;
   }

   @Override
   public void setRequestsPerSecond(double requestsPerSecond) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens(true);
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Bearer " + getAccessToken())
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void updateTokens(Tokens tokens) {
      setAccessToken(tokens.accessToken());
      setRefreshToken(tokens.refreshToken());
      // expiresIn not returned by oauth server, all tokens expire in 24 hours
      setTokenExpiration(tokens.issued() + TimeUnit.MILLISECONDS.convert(24L, TimeUnit.HOURS));
   }

   @Override
   protected String getTestSuffix() {
      return "/2/users/me";
   }

   @Override
   public String getOauthFlags() {
      String flags = super.getOauthFlags();

      if(StringUtils.isBlank(flags)) {
         return "pkce";
      }

      return flags + " pkce";
   }

   public boolean isVisible() {
      return false;
   }
}
