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
package inetsoft.uql.rest.datasource.chargify;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("subdomain"),
   @View1("apiKey"),
   @View1("URL")
})
public class ChargifyDataSource extends EndpointJsonDataSource<ChargifyDataSource> {
   static String TYPE = "Rest.Chargify";

   public ChargifyDataSource() {
      super(TYPE, ChargifyDataSource.class);
      setAuthType(AuthType.BASIC);

      HttpParameter contentType = new HttpParameter();
      contentType.setName("Content-Type");
      contentType.setValue("application/json");
      contentType.setType(HttpParameter.ParameterType.HEADER);
      setQueryHttpParameters(new HttpParameter[]{ contentType });
   }

   @Property(label = "Subdomain", required = true)
   public String getSubdomain() {
      return subdomain;
   }

   public void setSubdomain(String subdomain) {
      this.subdomain = subdomain;
   }

   @Property(label = "API Key", required = true, password = true)
   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public String getUser() {
      return apiKey;
   }

   @Override
   public void setUser(String user) {
      // no-op
   }

   @Override
   public String getPassword() {
      return "X";
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = "subdomain")
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("https://");

      if(subdomain == null) {
         url.append("[subdomain]");
      }
      else {
         url.append(subdomain);
      }

      return url.append(".chargify.com").toString();
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(subdomain != null) {
         writer.format("<subdomain><![CDATA[%s]]></subdomain>%n", subdomain);
      }

      if(apiKey != null) {
         writer.format("<apiKey><![CDATA[%s]]></apiKey>%n", Tool.encryptPassword(apiKey));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      subdomain = Tool.getChildValueByTagName(root, "subdomain");
      apiKey = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiKey"));
   }

   @Override
   protected String getTestSuffix() {
      return "/subscriptions.json";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof ChargifyDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ChargifyDataSource that = (ChargifyDataSource) o;
      return Objects.equals(subdomain, that.subdomain) &&
         Objects.equals(apiKey, that.apiKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), subdomain, apiKey);
   }

   private String subdomain;
   private String apiKey;
}
