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
package inetsoft.uql.rest.datasource.monday;

import inetsoft.uql.rest.datasource.graphql.AbstractGraphQLDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;
import org.apache.http.HttpHeaders;

import java.util.Objects;

@View(value= {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "apiKey", visibleMethod = "useCredential")
})
public class MondayDataSource extends AbstractGraphQLDataSource<MondayDataSource> {
   public MondayDataSource() {
      super(TYPE, MondayDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_KEY;
   }

   @Override
   public HttpParameter[] getRequestParameters() {
      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name(HttpHeaders.AUTHORIZATION)
            .value(getApiKey())
            .build()
      };
   }

   @Override
   public String getURL() {
      return "https://api.monday.com/v2";
   }

   @Property(label = "API Key", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiKey() {
      return ((ApiKeyCredential) getCredential()).getApiKey();
   }

   public void setApiKey(String apiKey) {
      ((ApiKeyCredential) getCredential()).setApiKey(apiKey);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getApiKey());
   }

   static String TYPE = "monday.com";
}