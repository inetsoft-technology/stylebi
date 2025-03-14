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
package inetsoft.uql.rest.datasource.keap;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.credential.CredentialType;

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
   @View1(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
      wrap = true, colspan = 2, visibleMethod ="displayAPIKeyTip"),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class KeapDataSource extends OAuthEndpointJsonDataSource<KeapDataSource> {
   static final String TYPE = "Rest.InfusionSoft";

   public KeapDataSource() {
      super(TYPE, KeapDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.CLIENT;
   }

   @Property(label = "API Key", required = true)
   @Override
   public String getClientId() {
      return super.getClientId();
   }

   @Property(label = "App Secret", required = true, password = true)
   @Override
   public String getClientSecret() {
      return super.getClientSecret();
   }

   @Override
   public String getAuthorizationUri() {
      return "https://signin.infusionsoft.com/app/oauth/authorize";
   }

   @Override
   public void setAuthorizationUri(String authorizationUri) {
      // no-op
   }

   @Override
   public String getTokenUri() {
      return "https://api.infusionsoft.com/token";
   }

   @Override
   public void setTokenUri(String tokenUri) {
      // no-op
   }

   @Override
   public String getScope() {
      return "full";
   }

   @Override
   public void setScope(String scope) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v1/users";
   }

   @Override
   public String getURL() {
      return "https://api.infusionsoft.com/crm/rest";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens();
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Content-Type")
            .value("application/json")
            .build(),
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
}
