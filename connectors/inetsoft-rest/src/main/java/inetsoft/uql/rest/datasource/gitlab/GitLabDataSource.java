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
package inetsoft.uql.rest.datasource.gitlab;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "token", visibleMethod = "useCredential")
})
public class GitLabDataSource extends EndpointJsonDataSource<GitLabDataSource> {
   public static final String TYPE = "Rest.GitLab";

   public GitLabDataSource() {
      super(TYPE, GitLabDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.TOKEN;
   }

   @Property(label = "Token", required = true, password = true)
   public String getToken() {
      if(getCredential() instanceof TokenCredential) {
         return ((TokenCredential) getCredential()).getToken();
      }

      return null;
   }

   public void setToken(String token) {
      if(getCredential() instanceof TokenCredential) {
         ((TokenCredential) getCredential()).setToken(token);
      }
   }

   @Override
   public String getURL() {
      return "https://gitlab.com/api";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Private-Token")
            .value(getToken())
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v4/user";
   }

   @Override
   public boolean equals(Object obj) {
      try {
         return obj instanceof GitLabDataSource && super.equals(obj);
      }
      catch(Exception ex) {
         return false;
      }
   }
}
