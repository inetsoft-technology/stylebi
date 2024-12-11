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
package inetsoft.uql.rest.datasource.teamdesk;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("domain"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "applicationId", visibleMethod = "useCredential"),
   @View1(value = "authorizationToken", visibleMethod = "useCredential")
})
public class TeamDeskDataSource extends EndpointJsonDataSource<TeamDeskDataSource> {
   static final String TYPE = "Rest.TeamDesk";

   public TeamDeskDataSource() {
      super(TYPE, TeamDeskDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.AUTHORIZATION_TOKEN;
   }

   @Property(label = "Domain", required = true)
   public String getDomain() {
      return domain;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   @Property(label = "Application ID", required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApplicationId() {
      return ((AuthorizationTokenCredential) getCredential()).getApplicationId();
   }

   public void setApplicationId(String applicationId) {
      ((AuthorizationTokenCredential) getCredential()).setApplicationId(applicationId);
   }

   @Property(label = "Authorization Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getAuthorizationToken() {
      return ((AuthorizationTokenCredential) getCredential()).getAuthorizationToken();
   }

   public void setAuthorizationToken(String authorizationToken) {
      ((AuthorizationTokenCredential) getCredential()).setAuthorizationToken(authorizationToken);
   }

   @Override
   public String getURL() {
      return "https://" + domain + "/secure/api/v2/" + getApplicationId();
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
            .value("Bearer " + getAuthorizationToken())
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/user.json";
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
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof TeamDeskDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      TeamDeskDataSource that = (TeamDeskDataSource) o;
      return Objects.equals(domain, that.domain);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), domain, getApplicationId(), getAuthorizationToken());
   }

   private String domain = "www.teamdesk.net";
}
