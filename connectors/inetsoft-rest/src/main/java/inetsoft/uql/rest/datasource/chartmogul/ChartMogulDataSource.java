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
package inetsoft.uql.rest.datasource.chartmogul;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "accountToken", visibleMethod = "useCredential"),
   @View1(value = "secretKey", visibleMethod = "useCredential")
})
public class ChartMogulDataSource extends EndpointJsonDataSource<ChartMogulDataSource> {
   public static final String TYPE = "Rest.ChartMogul";

   public ChartMogulDataSource() {
      super(TYPE, ChartMogulDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.ACCOUNT_SECRET;
   }

   @Property(label = "Account Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getAccountToken() {
      return ((AccountSecretCrendential) getCredential()).getAccountToken();
   }

   public void setAccountToken(String accountToken) {
      ((AccountSecretCrendential) getCredential()).setAccountToken(accountToken);
   }

   @Property(label = "Secret Key", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getSecretKey() {
      return ((AccountSecretCrendential) getCredential()).getSecretKey();
   }

   public void setSecretKey(String secretKey) {
      ((AccountSecretCrendential) getCredential()).setSecretKey(secretKey);
   }

   @Override
   public String getURL() {
      return "https://api.chartmogul.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public String getUser() {
      return getAccountToken();
   }

   @Override
   public void setUser(String user) {
      // no-op
   }

   @Override
   public String getPassword() {
      return getSecretKey();
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v1/customers";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof ChartMogulDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getAccountToken(), getSecretKey());
   }
}
