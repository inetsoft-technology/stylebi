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
package inetsoft.uql.rest.datasource.github;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import org.apache.http.HttpHeaders;

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
})
public class GitHubDataSource extends OAuthEndpointJsonDataSource<GitHubDataSource> {
   static final String TYPE = "Rest.GitHub";

   public GitHubDataSource() {
      super(TYPE, GitHubDataSource.class);
   }

   @Override
   public AuthType getAuthType() {
      return AuthType.NONE;
   }

   @Override
   public String getAuthorizationUri() {
      return "https://github.com/login/oauth/authorize";
   }

   @Override
   public String getTokenUri() {
      return "https://github.com/login/oauth/access_token";
   }

   @Override
   public String getScope() {
      return "repo security_events read:repo_hook read:org read:public_key notifications read:user user:email read:discussion read:packages read:gpg_key";
   }

   @Override
   public String getURL() {
      return "https://api.github.com";
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter parameter = new HttpParameter();
      parameter.setType(HttpParameter.ParameterType.HEADER);
      parameter.setName("Accept");
      parameter.setValue("application/vnd.github.v3+json");

      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name(HttpHeaders.AUTHORIZATION)
            .value("token " + getAccessToken())
            .build(),
         parameter
      };
   }

   @Override
   protected String getTestSuffix() {
      return "/user";
   }
}
