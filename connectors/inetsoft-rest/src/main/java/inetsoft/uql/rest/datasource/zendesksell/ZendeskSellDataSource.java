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
package inetsoft.uql.rest.datasource.zendesksell;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("clientId"),
   @View1("clientSecret"),
   @View1("accessToken"),
})
public class ZendeskSellDataSource extends EndpointJsonDataSource<ZendeskSellDataSource> {
   static final String TYPE = "Rest.ZendeskSell";

   public ZendeskSellDataSource() {
      super(TYPE, ZendeskSellDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "App Client ID")
   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Property(label = "App Client Secret", password = true)
   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   @Property(label = "Access Token", required = true, password = true)
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public String getURL() {
      return "https://api.getbase.com";
   }

   @Override
   public void setURL(String url) {
      // no-op;
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
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Bearer " + accessToken)
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(clientId != null) {
         writer.format("<clientId><![CDATA[%s]]></clientId>%n", clientId);
      }

      if(clientSecret != null) {
         writer.format(
            "<clientSecret><![CDATA[%s]]></clientSecret>%n", Tool.encryptPassword(clientSecret));
      }

      if(accessToken != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(accessToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      clientId = Tool.getChildValueByTagName(root, "clientId");
      clientSecret = Tool.decryptPassword(Tool.getChildValueByTagName(root, "clientSecret"));
      accessToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "accessToken"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v2/users/self";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof ZendeskSellDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ZendeskSellDataSource that = (ZendeskSellDataSource) o;
      return Objects.equals(clientId, that.clientId) &&
         Objects.equals(clientSecret, that.clientSecret) &&
         Objects.equals(accessToken, that.accessToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), clientId, clientSecret, accessToken);
   }

   private String clientId;
   private String clientSecret;
   private String accessToken;

   private static final Logger LOG = LoggerFactory.getLogger(ZendeskSellDataSource.class);
}
