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
package inetsoft.uql.rest.datasource.zendesk;

import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialType;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("domain"),
   @View1("URL"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class ZendeskDataSource extends EndpointJsonDataSource<ZendeskDataSource> {
   static final String TYPE = "Rest.Zendesk";
   
   public ZendeskDataSource() {
      super(TYPE, ZendeskDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD;
   }

   @Property(label = "Domain", required = true)
   public String getDomain() {
      return domain;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = "domain")
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("https://");

      if(domain == null) {
         url.append("[Domain]");
      }
      else {
         url.append(domain);
      }

      return url.append(".zendesk.com").toString();
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Property(label = "User", required = true)
   @Override
   public String getUser() {
      String userEmail = super.getUser();

      if(userEmail != null && !userEmail.contains("/token")) {
         userEmail = userEmail + "/token";
      }

      return userEmail;
   }

   @Property(label = "Password", required = true, password = true)
   @Override
   public String getPassword() {
      return super.getPassword();
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
      return "/api/v2/users/me.json";
   }

   @Override
   protected void validateTestResponse(HttpResponse response) throws Exception {
      super.validateTestResponse(response);

      try(InputStream input = response.getResponseBodyAsStream()) {
         Boolean verified = (Boolean)
            new JsonTransformer().transform(input, "$.user.verified");

         if(verified == null) {
            throw new IllegalStateException("Invalid JSON response");
         }
         else if(!verified) {
            throw new IllegalStateException("Unverified user, check your domain and credentials.");
         }
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof ZendeskDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ZendeskDataSource that = (ZendeskDataSource) o;
      return Objects.equals(domain, that.domain);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), domain);
   }

   private String domain;
}
