/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.xero;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = XeroDataSource.SERVICE_NAME))),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class XeroDataSource extends OAuthEndpointJsonDataSource<XeroDataSource> {
   static final String TYPE = "Rest.Xero";
   
   public XeroDataSource() {
      super(TYPE, XeroDataSource.class);
   }

   @Override
   protected String getTestSuffix() {
      return "/connections";
   }

   @Override
   public AuthType getAuthType() {
      return AuthType.TWO_STEP;
   }

   @Override
   public String getAuthURL() {
      return "https://api.xero.com/connections";
   }

   @Override
   public String getTokenPattern() {
      return "\"tenantId\":\"(.*?)\"";
   }

   @Override
   public String getURL() {
      return "https://api.xero.com";
   }

   @Override
   public HttpParameter[] getAuthenticationHttpParameters() {
      return new HttpParameter[]{
         getAuthorizationHeader()
      };
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens();
      return new HttpParameter[]{
         getAuthorizationHeader(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("xero-tenant-id")
            .value("{{token}}")
            .build()
      };
   }

   @Override
   public String getServiceName() {
      return SERVICE_NAME;
   }

   private HttpParameter getAuthorizationHeader() {
      return HttpParameter.builder()
         .type(HttpParameter.ParameterType.HEADER)
         .name("Authorization")
         .value("Bearer " + getAccessToken())
         .build();
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public double getRequestsPerSecond() {
      return 1;
   }

   public static final String SERVICE_NAME = "xero";
}
