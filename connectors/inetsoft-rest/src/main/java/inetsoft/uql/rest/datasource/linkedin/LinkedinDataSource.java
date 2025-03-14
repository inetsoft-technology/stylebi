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
package inetsoft.uql.rest.datasource.linkedin;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;

@View(vertical = true, value = {
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = "linkedin"))),
   @View1(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
      wrap = true, colspan = 2, visibleMethod ="displayAPIKeyTip"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "accessToken", visibleMethod = "useCredential"),
   @View1("tokenExpiration")
})
public class LinkedinDataSource extends OAuthEndpointJsonDataSource<LinkedinDataSource> {
   static final String TYPE = "Rest.Linkedin";
   
   public LinkedinDataSource() {
      super(TYPE, LinkedinDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.ACCESS_TOKEN;
   }

   @Override
   public String getURL() {
      return "https://api.linkedin.com";
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
            .name("X-RestLi-Protocol-Version")
            .value("2.0.0")
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Accept")
            .value("application/json")
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Bearer " + getAccessToken())
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Linkedin-Version")
            .value("202411")
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v2/me";
   }
}
