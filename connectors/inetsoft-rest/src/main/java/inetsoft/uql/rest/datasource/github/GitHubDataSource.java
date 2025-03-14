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
package inetsoft.uql.rest.datasource.github;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;
import org.apache.http.HttpHeaders;

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
})
public class GitHubDataSource extends OAuthEndpointJsonDataSource<GitHubDataSource> {
   static final String TYPE = "Rest.GitHub";

   public GitHubDataSource() {
      super(TYPE, GitHubDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.CLIENT;
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
            .secret(true)
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
