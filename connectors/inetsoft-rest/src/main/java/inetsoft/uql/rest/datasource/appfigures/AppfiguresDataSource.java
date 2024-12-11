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
package inetsoft.uql.rest.datasource.appfigures;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "clientKey", visibleMethod = "useCredential"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class AppfiguresDataSource extends EndpointJsonDataSource<AppfiguresDataSource> {
   static final String TYPE = "Rest.Appfigures";

   public AppfiguresDataSource() {
      super(TYPE, AppfiguresDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD_CLIENT_KEY;
   }

   @Property(label = "Client Key", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getClientKey() {
      return ((ClientKeyPasswordCredential) getCredential()).getClientKey();
   }

   public void setClientKey(String clientKey) {
      ((ClientKeyPasswordCredential) getCredential()).setClientKey(clientKey);
   }

   @Override
   public String getURL() {
      return "https://api.appfigures.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("X-Client-Key")
            .value(getClientKey())
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v2/";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof AppfiguresDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getClientKey());
   }
}
