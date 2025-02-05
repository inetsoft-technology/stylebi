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
package inetsoft.uql.rest.datasource.gsearchconsole;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;

@View(vertical = true, value = {
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         style = ButtonStyle.GOOGLE_AUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = GoogleSearchConsoleDataSource.SERVICE_NAME))),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class GoogleSearchConsoleDataSource
   extends OAuthEndpointJsonDataSource<GoogleSearchConsoleDataSource>
{
   static final String TYPE = "Rest.GoogleSearchConsole";
   static final String SERVICE_NAME = "google-search";

   public GoogleSearchConsoleDataSource() {
      super(TYPE, GoogleSearchConsoleDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.AUTH_TOKENS;
   }

   @Override
   protected boolean supportCredentialId() {
      return false;
   }

   @Override
   public String getURL() {
      return "https://www.googleapis.com/webmasters";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens();
      return new HttpParameter[]{
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
   public String getServiceName() {
      return SERVICE_NAME;
   }

   @Override
   public double getRequestsPerSecond() {
      return 10D;
   }

   @Override
   public void setRequestsPerSecond(double requestsPerSecond) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v3/sites";
   }
}
