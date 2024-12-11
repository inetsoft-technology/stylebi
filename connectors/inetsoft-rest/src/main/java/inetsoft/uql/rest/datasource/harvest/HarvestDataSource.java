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
package inetsoft.uql.rest.datasource.harvest;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "accountId", visibleMethod = "useCredential"),
   @View1(value = "accessToken", visibleMethod = "useCredential")
})
public class HarvestDataSource extends EndpointJsonDataSource<HarvestDataSource> {
   static final String TYPE = "Rest.Harvest";

   public HarvestDataSource() {
      super(TYPE, HarvestDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.CLIENT_TOKEN;
   }

   @Property(label = "Account ID", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getAccountId() {
      return super.getClientId();
   }

   public void setAccountId(String accountId) {
      super.setClientId(accountId);
   }

   @Property(label = "Personal Access Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getAccessToken() {
      return super.getAccessToken();
   }

   @Override
   public String getURL() {
      return "https://api.harvestapp.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter accountParam = new HttpParameter();
      accountParam.setName("Harvest-Account-ID");
      accountParam.setValue(getAccountId());
      accountParam.setType(HttpParameter.ParameterType.HEADER);

      HttpParameter authParam = new HttpParameter();
      authParam.setName("Authorization");
      authParam.setSecret(true);
      authParam.setValue("Bearer " + getAccessToken());
      authParam.setType(HttpParameter.ParameterType.HEADER);

      HttpParameter agentParam = new HttpParameter();
      agentParam.setName("User-Agent");
      agentParam.setValue("InetSoft");
      agentParam.setType(HttpParameter.ParameterType.HEADER);

      return new HttpParameter[] { accountParam, authParam, agentParam };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v2/users/me";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof HarvestDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getAccountId(), getAccessToken());
   }
}
