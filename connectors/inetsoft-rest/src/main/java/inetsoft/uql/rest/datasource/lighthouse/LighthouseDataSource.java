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
package inetsoft.uql.rest.datasource.lighthouse;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "domain", visibleMethod = "useCredential"),
   @View1(value = "apiToken", visibleMethod = "useCredential"),
   @View1("URL")
})
public class LighthouseDataSource extends EndpointJsonDataSource<LighthouseDataSource> {
   static final String TYPE = "Rest.Lighthouse";
   
   public LighthouseDataSource() {
      super(TYPE, LighthouseDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_TOKEN;
   }

   @Property(label = "Domain", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getDomain() {
      return domain;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   @Property(label = "API Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiToken() {
      return ((ApiTokenCredential) getCredential()).getApiToken();
   }

   public void setApiToken(String apiToken) {
      ((ApiTokenCredential) getCredential()).setApiToken(apiToken);
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
            .value(getApiToken())
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
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      domain = Tool.getChildValueByTagName(root, "domain");
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
      return Objects.equals(domain, that.domain);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), domain, getApiToken());
   }

   private String domain;
}
