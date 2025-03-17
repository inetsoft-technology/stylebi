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
package inetsoft.uql.rest.datasource.freshsales;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "domain"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "domainSuffix", visibleMethod = "useCredential"),
   @View1(value = "apiKey", visibleMethod = "useCredential"),
   @View1("URL")
})
public class FreshsalesDataSource extends EndpointJsonDataSource<FreshsalesDataSource> {
   public static final String TYPE = "Rest.Freshsales";

   public FreshsalesDataSource() {
      super(TYPE, FreshsalesDataSource.class);
      setAuthType(AuthType.NONE);
      setHttpParameter("Content-Type", "application/json", HttpParameter.ParameterType.HEADER);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_KEY;
   }

   @Property(label = "API Key", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiKey() {
      return ((ApiKeyCredential) getCredential()).getApiKey();
   }

   public void setApiKey(String apiKey) {
      ((ApiKeyCredential) getCredential()).setApiKey(apiKey);
   }

   @Property(label = "Domain", required = true)
   public String getDomain() {
      return domain;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   @Property(label = "Domain Suffix", required = true)
   @PropertyEditor(tags={".freshsales.io", ".freshworks.com/crm/sales",
                         ".myfreshworks.com/crm/sales"}, dependsOn = "useCredentialId")
   public String getDomainSuffix() {
      return domainSuffix;
   }

   public void setDomainSuffix(String domainSuffix) {
      this.domainSuffix = domainSuffix;
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = {"domain", "domainSuffix"})
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("https://");

      if(domain == null) {
         url.append("[domain]");
      }
      else {
         url.append(domain);
      }

      url.append(domainSuffix);
      url.append("/api");

      return url.toString();
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
            .name("Authorization")
            .value(AUTH_VALUE_PREFIX + getApiKey())
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

      if(domainSuffix != null) {
         writer.format("<domainSuffix><![CDATA[%s]]></domainSuffix>%n", domainSuffix);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      domain = Tool.getChildValueByTagName(root, "domain");
      domainSuffix = Tool.getChildValueByTagName(root, "domainSuffix");
      domainSuffix = domainSuffix == null ? ".freshsales.io" : domainSuffix;
   }

   @Override
   protected String getTestSuffix() {
      return "/leads/filters";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof FreshsalesDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      FreshsalesDataSource that = (FreshsalesDataSource) o;
      return Objects.equals(domain, that.domain) &&
         Objects.equals(domainSuffix, that.domainSuffix);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), domain, getApiKey());
   }

   public static final String AUTH_VALUE_PREFIX = "Token token=";
   private String domain;
   private String domainSuffix;
}