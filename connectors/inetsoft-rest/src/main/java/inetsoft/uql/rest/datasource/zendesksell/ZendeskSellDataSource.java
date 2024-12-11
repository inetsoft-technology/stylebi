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
package inetsoft.uql.rest.datasource.zendesksell;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "clientId", visibleMethod = "useCredential"),
   @View1(value = "clientSecret", visibleMethod = "useCredential"),
   @View1(value = "accessToken", visibleMethod = "useCredential"),
})
public class ZendeskSellDataSource extends EndpointJsonDataSource<ZendeskSellDataSource> {
   static final String TYPE = "Rest.ZendeskSell";

   public ZendeskSellDataSource() {
      super(TYPE, ZendeskSellDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.CLIENT_GRANT;
   }

   @Property(label = "App Client ID")
   public String getClientId() {
      return super.getClientId();
   }

   @Property(label = "App Client Secret", password = true)
   public String getClientSecret() {
      return super.getClientSecret();
   }

   @Property(label = "Access Token", required = true, password = true)
   public String getAccessToken() {
      return super.getAccessToken();
   }

   @Override
   public String getURL() {
      return "https://api.getbase.com";
   }

   @Override
   public void setURL(String url) {
      // no-op;
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Accept")
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
   protected String getTestSuffix() {
      return "/v2/users/self";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof ZendeskSellDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), getClientId(), getClientSecret(), getAccessToken());
   }

   private static final Logger LOG = LoggerFactory.getLogger(ZendeskSellDataSource.class);
}
