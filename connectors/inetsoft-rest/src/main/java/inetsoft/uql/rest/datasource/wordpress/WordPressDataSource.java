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
package inetsoft.uql.rest.datasource.wordpress;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;

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
   @View1(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
      wrap = true, colspan = 2),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class WordPressDataSource extends OAuthEndpointJsonDataSource<WordPressDataSource> {
   static final String TYPE = "Rest.WordPress";
   
   public WordPressDataSource() {
      super(TYPE, WordPressDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.CLIENT;
   }

   @Override
   public String getAuthorizationUri() {
      return "https://public-api.wordpress.com/oauth2/authorize";
   }

   @Override
   public void setAuthorizationUri(String authorizationUri) {
      // no-op
   }

   @Override
   public String getTokenUri() {
      return "https://public-api.wordpress.com/oauth2/token";
   }

   @Override
   public void setTokenUri(String tokenUri) {
      // no-op
   }

   @Override
   public String getScope() {
      return "global";
   }

   @Override
   public void setScope(String scope) {
      // no-op
   }

   @Override
   public String getOauthFlags() {
      return "credentialsInTokenRequestBody useAuthorizationCodeForRefresh";
   }

   @Override
   public void setOauthFlags(String oauthFlags) {
      // no-op
   }

   @Override
   public String getURL() {
      return "https://public-api.wordpress.com/rest";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens();
      return new HttpParameter[] {
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
   protected String getTestSuffix() {
      return "/v1.1/me";
   }
}
