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
package inetsoft.uql.rest.datasource.xero;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.credential.CredentialType;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

@View(vertical = true, value = {
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = XeroDataSource.SERVICE_NAME))),
   @View1(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
      wrap = true, colspan = 2),
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
   protected CredentialType getCredentialType() {
      return CredentialType.AUTH_TOKENS;
   }

   @Override
   protected boolean supportCredentialId() {
      return false;
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

   /**
    * The xero-tenant-id header of the queries must be the tenant id of an organisation, so the
    * tenant is chosen from the connections by its type instead of by the token pattern.
    */
   @Override
   public Function<String, String> createTwoStepTokenExtractor() {
      return XeroDataSource::getOrganisationTenantId;
   }

   /**
    * Gets the tenant id of the first organisation in the response of the connections endpoint.
    * If no connection has a tenant type, the tenant id of the first connection is used.
    *
    * @param connections the response body of the connections endpoint.
    *
    * @return the tenant id.
    */
   static String getOrganisationTenantId(String connections) {
      final JsonNode root;

      try {
         root = MAPPER.readTree(connections);
      }
      catch(JsonProcessingException e) {
         throw new IllegalArgumentException("Failed to parse the Xero connections response", e);
      }

      if(root == null || !root.isArray()) {
         throw new IllegalArgumentException("The Xero connections response is not a list");
      }

      final Set<String> tenantTypes = new LinkedHashSet<>();
      String firstTenantId = null;

      for(JsonNode connection : root) {
         final String tenantId = getText(connection, "tenantId");
         final String tenantType = getText(connection, "tenantType");

         if(tenantType != null) {
            tenantTypes.add(tenantType);

            if(tenantId != null && ORGANISATION.equalsIgnoreCase(tenantType)) {
               return tenantId;
            }
         }

         if(firstTenantId == null) {
            firstTenantId = tenantId;
         }
      }

      if(tenantTypes.isEmpty() && firstTenantId != null) {
         return firstTenantId;
      }

      throw new IllegalArgumentException(
         "No Xero organisation is connected. Authorize access to a Xero organisation." +
         (tenantTypes.isEmpty() ? "" : " Connected tenant types: " + tenantTypes));
   }

   private static String getText(JsonNode node, String field) {
      final JsonNode value = node.get(field);
      return value != null && value.isTextual() && !value.asText().isEmpty() ?
         value.asText() : null;
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
   private static final String ORGANISATION = "ORGANISATION";
   private static final ObjectMapper MAPPER = new ObjectMapper();
}
