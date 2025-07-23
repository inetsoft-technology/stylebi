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
package inetsoft.uql.rest.datasource.mixpanel;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "apiSecret", visibleMethod = "useCredential")
})
public class MixpanelDataSource extends EndpointJsonDataSource<MixpanelDataSource> {
   static final String TYPE = "Rest.Mixpanel";
   
   public MixpanelDataSource() {
      super(TYPE, MixpanelDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_SECRET;
   }

   @Property(label = "API Secret", required = true, password = true)
   public String getApiSecret() {
      return ((ApiSecretCredential) getCredential()).getApiSecret();
   }

   public void setApiSecret(String apiSecret) {
      ((ApiSecretCredential) getCredential()).setApiSecret(apiSecret);
   }

   @Override
   public String getURL() {
      return dataPipelineApi ? "https://data.mixpanel.com/api" : "https://mixpanel.com/api";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   public boolean isDataPipelineApi() {
      return dataPipelineApi;
   }

   public void setDataPipelineApi(boolean dataPipelineApi) {
      this.dataPipelineApi = dataPipelineApi;
   }

   @Override
   public String getUser() {
      return getApiSecret();
   }

   @Override
   public void setUser(String user) {
      // no-op
   }

   @Override
   public String getPassword() {
      return "";
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      // %5B%22%24any%22%5D is ["$pageview"] url encoded
      return "https://data.mixpanel.com/api/2.0/export/?from_date=2024-01-01&to_date=2024-01-01&event=%5B%22%24pageview%22%5D";
   }

   @Override
   protected boolean isIgnoreBaseUrlForTest() {
      return true;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof MixpanelDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getApiSecret());
   }

   private boolean dataPipelineApi;
}
