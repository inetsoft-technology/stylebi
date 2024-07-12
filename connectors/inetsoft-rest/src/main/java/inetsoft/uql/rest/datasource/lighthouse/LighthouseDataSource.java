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
package inetsoft.uql.rest.datasource.lighthouse;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("domain"),
   @View1("apiToken"),
   @View1("URL")
})
public class LighthouseDataSource extends EndpointJsonDataSource<LighthouseDataSource> {
   static final String TYPE = "Rest.Lighthouse";
   
   public LighthouseDataSource() {
      super(TYPE, LighthouseDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "Domain", required = true)
   public String getDomain() {
      return domain;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   @Property(label = "API Token", required = true, password = true)
   public String getApiToken() {
      return apiToken;
   }

   public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = "domain")
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("http://");

      if(domain == null) {
         url.append("[domain]");
      }
      else {
         url.append(domain);
      }

      return url.append(".lighthouseapp.com").toString();
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
            .name("X-LighthouseToken")
            .value(apiToken)
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Content-Type")
            .value("application/json")
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

      if(domain != null) {
         writer.format("<domain><![CDATA[%s]]></domain>%n", domain);
      }

      if(apiToken != null) {
         writer.format("<apiToken><![CDATA[%s]]></apiToken>%n", Tool.encryptPassword(apiToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      domain = Tool.getChildValueByTagName(root, "domain");
      apiToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiToken"));
   }

   @Override
   protected String getTestSuffix() {
      return "/profile.json";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof LighthouseDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      LighthouseDataSource that = (LighthouseDataSource) o;
      return Objects.equals(domain, that.domain) &&
         Objects.equals(apiToken, that.apiToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), domain, apiToken);
   }

   private String domain;
   private String apiToken;
}
