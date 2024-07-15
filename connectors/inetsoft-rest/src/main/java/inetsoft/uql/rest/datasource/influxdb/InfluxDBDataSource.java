/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("URL"),
   @View1("authScheme"),
   @View1(value = "user", visibleMethod = "isBasicAuth"),
   @View1(value = "password", visibleMethod = "isBasicAuth"),
   @View1(value = "apiToken", visibleMethod = "isTokenAuth")
})
public class InfluxDBDataSource extends EndpointJsonDataSource<InfluxDBDataSource> {
   static final String TYPE = "Rest.InfluxDB";

   public InfluxDBDataSource() {
      super(TYPE, InfluxDBDataSource.class);
      AuthType authType = authScheme.equals("BASIC") ? AuthType.BASIC : AuthType.NONE;
      setAuthType(authType);
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
      return apiToken;
   }

   public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
   }

   public boolean isTokenAuth() {
      return getAuthType() == AuthType.NONE;
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      if(isTokenAuth()) {
         return new HttpParameter[] {
            HttpParameter.builder()
               .type(HttpParameter.ParameterType.HEADER)
               .name("Authorization")
               .value("Token " + apiToken)
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

      if(apiToken != null) {
         writer.format("<apiToken><![CDATA[%s]]></apiToken>%n", Tool.encryptPassword(apiToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      authScheme = Tool.getChildValueByTagName(root, "authScheme");
      apiToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiToken"));
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
      return Objects.equals(authScheme, that.authScheme) && Objects.equals(apiToken, that.apiToken);
   }

   private String authScheme = "BASIC";
   private String apiToken;
}
