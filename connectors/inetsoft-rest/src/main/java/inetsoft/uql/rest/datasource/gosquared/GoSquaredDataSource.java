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
package inetsoft.uql.rest.datasource.gosquared;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "apiKey", visibleMethod = "useCredential"),
   @View1(value = "siteToken", visibleMethod = "useCredential")
})
public class GoSquaredDataSource extends EndpointJsonDataSource<GoSquaredDataSource> {
   static final String TYPE = "GoSquared";

   public GoSquaredDataSource() {
      super(TYPE, GoSquaredDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.SITE_TOKEN;
   }

   @Property(label = "API Key", required = true, password = true)
   public String getApiKey() {
      return ((SiteTokenCredential) getCredential()).getApiKey();
   }

   public void setApiKey(String apiKey) {
      ((SiteTokenCredential) getCredential()).setApiKey(apiKey);
   }

   @Property(label = "Site Token", required = true, password = true)
   public String getSiteToken() {
      return ((SiteTokenCredential) getCredential()).getSiteToken();
   }

   public void setSiteToken(String siteToken) {
      ((SiteTokenCredential) getCredential()).setSiteToken(siteToken);
   }

   @Override
   public String getURL() {
      return "https://api.gosquared.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter keyParam = new HttpParameter();
      keyParam.setName("api_key");
      keyParam.setSecret(true);
      keyParam.setValue(getApiKey());
      keyParam.setType(HttpParameter.ParameterType.QUERY);

      HttpParameter tokenParam = new HttpParameter();
      tokenParam.setName("site_token");
      tokenParam.setSecret(true);
      tokenParam.setValue(getSiteToken());
      tokenParam.setType(HttpParameter.ParameterType.QUERY);

      HttpParameter acceptToken = new HttpParameter();
      acceptToken.setName("Accept");
      acceptToken.setValue("application/json");
      acceptToken.setType(HttpParameter.ParameterType.HEADER);

      return new HttpParameter[] { keyParam, tokenParam, acceptToken };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/auth/v1/tokeninfo";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof GoSquaredDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getApiKey(), getSiteToken());
   }
}
