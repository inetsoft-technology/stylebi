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
package inetsoft.uql.rest.datasource.pipelinecrm;

import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "apiKey", visibleMethod = "useCredential")
})
public class PipelineCRMDataSource extends EndpointJsonDataSource<PipelineCRMDataSource> {
   static final String TYPE = "Rest.PipelineDeals";

   public PipelineCRMDataSource() {
      super(TYPE, PipelineCRMDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_KEY;
   }

   @Property(label = "API Key", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiKey() {
      return ((ApiKeyCredential) getCredential()).getApiKey();
   }

   public void setApiKey(String apiKey) {
      ((ApiKeyCredential) getCredential()).setApiKey(apiKey);
   }

   @Override
   public String getURL() {
      return "https://api.pipelinedeals.com/api";
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
            .name("Accept")
            .value("application/json")
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.QUERY)
            .name("api_key")
            .value(getApiKey())
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v3/profile.json";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof PipelineCRMDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getApiKey());
   }
}
