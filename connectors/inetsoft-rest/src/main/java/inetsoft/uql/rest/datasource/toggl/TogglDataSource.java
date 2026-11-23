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
package inetsoft.uql.rest.datasource.toggl;

import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.RestResponseException;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.*;

import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "apiToken", visibleMethod = "useCredential")
})
public class TogglDataSource extends EndpointJsonDataSource<TogglDataSource> {
   static final String TYPE = "Rest.Toggl";
   static final String TOGGL_2_KEY_PREFIX = "toggl_sk_";
   static final String TOGGL_2_KEY_HINT =
      "Toggl 2.0 API keys (toggl_sk_...) are not accepted by the Toggl Track API; " +
      "use the API token from your Toggl Track profile settings.";
   
   public TogglDataSource() {
      super(TYPE, TogglDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_TOKEN;
   }

   @Property(label = "API Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiToken() {
      return ((ApiTokenCredential) getCredential()).getApiToken();
   }

   public void setApiToken(String apiToken) {
      ((ApiTokenCredential) getCredential()).setApiToken(apiToken);
   }

   @Override
   public String getURL() {
      return "https://api.track.toggl.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public String getUser() {
      return getApiToken();
   }

   @Override
   public void setUser(String user) {
      // no-op
   }

   @Override
   public String getPassword() {
      return "api_token";
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Accept")
            .value("application/json")
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/api/v9/me";
   }

   /**
    * For a Toggl 2.0 (Focus) API key, adds a hint to a 401/403 test failure. Such keys (prefix
    * {@code toggl_sk_}) are not accepted by the Toggl Track API. The response body is dropped
    * from the message (it contains words that make the Status column show the generic login
    * error instead). Nothing is rejected by prefix.
    */
   @Override
   protected Exception translateTestFailure(RestResponseException e) {
      HttpResponse response = e.getResponse();
      String token = getApiToken();

      if(response == null || token == null || !token.startsWith(TOGGL_2_KEY_PREFIX)) {
         return e;
      }

      int code = response.getResponseStatusCode();

      if(code != 401 && code != 403) {
         return e;
      }

      return new Exception("Test request returned " + code + ": " +
                              response.getResponseStatusText() + ". " + TOGGL_2_KEY_HINT, e);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof TogglDataSource)) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getApiToken());
   }
}
