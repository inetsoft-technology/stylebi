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
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("URL"),
   @View1("authScheme"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredentialForBasicAuth"),
   @View1(value = "password", visibleMethod = "useCredentialForBasicAuth"),
   @View1(value = "apiToken", visibleMethod = "useCredentialForAuth")
})
public class InfluxDBDataSource extends EndpointJsonDataSource<InfluxDBDataSource> {
   static final String TYPE = "Rest.InfluxDB";

   public InfluxDBDataSource() {
      super(TYPE, InfluxDBDataSource.class);
      AuthType authType = authScheme.equals("BASIC") ? AuthType.BASIC : AuthType.NONE;
      setAuthType(authType);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD_APITOKEN;
   }

   @Property(label = "Authentication Scheme")
   @PropertyEditor(tags = { "BASIC", "TOKEN"},
      labels = { "Username and Password Scheme", "Token Scheme"})
   public String getAuthScheme() {
      return authScheme;
   }

   public void setAuthScheme(String authScheme) {
      this.authScheme = authScheme;
      AuthType authType = authScheme.equals("BASIC") ? AuthType.BASIC : AuthType.NONE;
      setAuthType(authType);
   }

   @Property(label = "API Token", required = true, password = true)
   @PropertyEditor(dependsOn="authScheme")
   public String getApiToken() {
      return ((ApiTokenCredential) getCredential()).getApiToken();
   }

   public void setApiToken(String apiToken) {
      ((ApiTokenCredential) getCredential()).setApiToken(apiToken);
   }

   public boolean isTokenAuth() {
      return getAuthType() == AuthType.NONE;
   }

   public boolean useCredentialForAuth() {
      return super.useCredential() && isTokenAuth();
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      if(isTokenAuth()) {
         return new HttpParameter[] {
            HttpParameter.builder()
               .type(HttpParameter.ParameterType.HEADER)
               .name("Authorization")
               .value("Token " + getApiToken())
               .build()
         };
      }
      else {
         return super.getQueryHttpParameters();
      }
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(authScheme != null) {
         writer.format("<authScheme><![CDATA[%s]]></authScheme>%n", authScheme);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      authScheme = Tool.getChildValueByTagName(root, "authScheme");
   }

   @Override
   protected String getTestSuffix() {
      return "/ping";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof InfluxDBDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      InfluxDBDataSource that = (InfluxDBDataSource) o;
      return Objects.equals(authScheme, that.authScheme);
   }

   private String authScheme = "BASIC";
}
