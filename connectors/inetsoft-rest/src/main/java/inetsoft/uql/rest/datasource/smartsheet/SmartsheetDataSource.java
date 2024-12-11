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
package inetsoft.uql.rest.datasource.smartsheet;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "accessToken", visibleMethod = "useCredential")
})
public class SmartsheetDataSource extends EndpointJsonDataSource<SmartsheetDataSource> {
   static final String TYPE = "Rest.Smartsheet";

   public SmartsheetDataSource() {
      super(TYPE, SmartsheetDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.ACCESS_TOKEN;
   }

   @Property(label = "Access Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getAccessToken() {
      return super.getAccessToken();
   }

   @Override
   public String getURL() {
      return "https://api.smartsheet.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter authParam = new HttpParameter();
      authParam.setType(HttpParameter.ParameterType.HEADER);
      authParam.setSecret(true);
      authParam.setName("Authorization");
      authParam.setValue("Bearer " + getAccessToken());

      return new HttpParameter[] { authParam };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/2.0/users/me";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof SmartsheetDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getAccessToken());
   }
}
