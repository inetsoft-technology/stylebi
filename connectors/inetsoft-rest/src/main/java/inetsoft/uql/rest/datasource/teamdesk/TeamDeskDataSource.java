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
package inetsoft.uql.rest.datasource.teamdesk;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("domain"),
   @View1("applicationId"),
   @View1("authorizationToken")
})
public class TeamDeskDataSource extends EndpointJsonDataSource<TeamDeskDataSource> {
   static final String TYPE = "Rest.TeamDesk";

   public TeamDeskDataSource() {
      super(TYPE, TeamDeskDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "Domain", required = true)
   public String getDomain() {
      return domain;
   }

   public void setDomain(String domain) {
      this.domain = domain;
   }

   @Property(label = "Application ID", required = true)
   public String getApplicationId() {
      return applicationId;
   }

   public void setApplicationId(String applicationId) {
      this.applicationId = applicationId;
   }

   @Property(label = "Authorization Token", required = true, password = true)
   public String getAuthorizationToken() {
      return authorizationToken;
   }

   public void setAuthorizationToken(String authorizationToken) {
      this.authorizationToken = authorizationToken;
   }

   @Override
   public String getURL() {
      return "https://" + domain + "/secure/api/v2/" + applicationId;
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
            .value("Bearer " + authorizationToken)
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

      if(applicationId != null) {
         writer.format("<applicationId><![CDATA[%s]]></applicationId>%n", applicationId);
      }

      if(authorizationToken != null) {
         writer.format(
            "<authorizationToken><![CDATA[%s]]></authorizationToken>%n",
            Tool.encryptPassword(authorizationToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      domain = Tool.getChildValueByTagName(root, "domain");
      applicationId = Tool.getChildValueByTagName(root, "applicationId");
      authorizationToken =
         Tool.decryptPassword(Tool.getChildValueByTagName(root, "authorizationToken"));
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
      return Objects.equals(domain, that.domain) &&
         Objects.equals(applicationId, that.applicationId) &&
         Objects.equals(authorizationToken, that.authorizationToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), domain, applicationId, authorizationToken);
   }

   private String domain = "www.teamdesk.net";
   private String applicationId;
   private String authorizationToken;
}
