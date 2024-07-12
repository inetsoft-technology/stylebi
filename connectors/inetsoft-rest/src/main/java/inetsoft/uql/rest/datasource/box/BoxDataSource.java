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
package inetsoft.uql.rest.datasource.box;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1("clientId"),
   @View1("clientSecret"),
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
               dependsOn = { "clientId", "clientSecret" },
               enabledMethod = "authorizeEnabled",
               oauth = @Button.OAuth
            )
         )
      }),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class BoxDataSource extends OAuthEndpointJsonDataSource<BoxDataSource> {
   static final String TYPE = "Rest.Box";

   public BoxDataSource() {
      super(TYPE, BoxDataSource.class);
      setAuthType(AuthType.NONE);
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
      return "https://account.box.com/api/oauth2/authorize";
   }

   @Override
   public void setAuthorizationUri(String authorizationUri) {
      // no-op
   }

   @Override
   public String getTokenUri() {
      return "https://api.box.com/oauth2/token";
   }

   @Override
   public void setTokenUri(String tokenUri) {
      // no-op
   }

   @Override
   public String getScope() {
      return "";
   }

   @Override
   public void setScope(String scope) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/2.0/users/me";
   }

   @Override
   public String getURL() {
      return "https://api.box.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public String getOauthFlags() {
      return "credentialsInTokenRequestBody";
   }
   @Override
   public void setOauthFlags(String oauthFlags) {
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
}