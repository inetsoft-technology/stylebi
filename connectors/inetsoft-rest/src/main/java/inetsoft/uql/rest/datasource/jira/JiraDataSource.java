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
package inetsoft.uql.rest.datasource.jira;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("serverType"),
   @View1(value = "cloudDomain", visibleMethod = "isCloudServer"),
   @View1(value = "URL", visibleMethod = "isCloudServer"),
   @View1(value = "localUrl", visibleMethod = "isLocalServer"),
   @View1("user"),
   @View1(value = "apiToken", visibleMethod = "isCloudServer"),
   @View1(value = "localPassword", visibleMethod = "isLocalServer")
})
public class JiraDataSource extends EndpointJsonDataSource<JiraDataSource> {
   static final String TYPE = "Rest.Jira";

   public JiraDataSource() {
      super(TYPE, JiraDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "Server Type", required = true)
   @PropertyEditor(tagsMethod = "getServerTypes")
   public String getServerType() {
      return serverType;
   }

   public void setServerType(String serverType) {
      this.serverType = serverType;
   }

   @Property(label = "Domain")
   @PropertyEditor(dependsOn = "serverType")
   public String getCloudDomain() {
      return cloudDomain;
   }

   public void setCloudDomain(String cloudDomain) {
      this.cloudDomain = cloudDomain;
   }

   @Property(label = "URL")
   @PropertyEditor(dependsOn = "serverType")
   public String getLocalUrl() {
      return localUrl;
   }

   public void setLocalUrl(String localUrl) {
      this.localUrl = localUrl;
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = "cloudDomain")
   @Override
   public String getURL() {
      if(isLocalServer()) {
         return localUrl;
      }
      else {
         StringBuilder url = new StringBuilder("https://");

         if(cloudDomain == null) {
            url.append("[domain]");
         }
         else {
            url.append(cloudDomain);
         }

         return url.append(".atlassian.net").toString();
      }
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Property(label = "User", required = true)
   @Override
   public String getUser() {
      return super.getUser();
   }

   @Property(label = "Password", required = true, password = true)
   @PropertyEditor(dependsOn = "serverType")
   public String getLocalPassword() {
      return localPassword;
   }

   public void setLocalPassword(String localPassword) {
      this.localPassword = localPassword;
   }

   @Override
   public String getPassword() {
      if(isCloudServer()) {
         return apiToken;
      }
      else {
         return localPassword;
      }
   }

   @Property(label = "Api Token", required = true, password = true)
   @PropertyEditor(dependsOn = "serverType")
   public String getApiToken() {
      return apiToken;
   }

   public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
   }

   public String[][] getServerTypes() {
      return new String[][] {
         { "Jira Cloud", "cloud" },
         { "Local Installation", "local" }
      };
   }

   public boolean isCloudServer() {
      return "cloud".equals(serverType);
   }

   public boolean isLocalServer() {
      return "local".equals(serverType);
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(serverType != null) {
         writer.format("<serverType><![CDATA[%s]]></serverType>%n", serverType);
      }

      if(cloudDomain != null) {
         writer.format("<cloudDomain><![CDATA[%s]]></cloudDomain>%n", cloudDomain);
      }

      if(localUrl != null) {
         writer.format("<localUrl><![CDATA[%s]]></localUrl>%n", localUrl);
      }

      if(apiToken != null) {
         writer.format("<apiToken><![CDATA[%s]]></apiToken>%n", apiToken);
      }

      if(localPassword != null) {
         writer.format("<localPassword><![CDATA[%s]]></localPassword>%n",
                       Tool.encryptPassword(localPassword));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      serverType = Tool.getChildValueByTagName(root, "serverType");
      cloudDomain = Tool.getChildValueByTagName(root, "cloudDomain");
      localUrl = Tool.getChildValueByTagName(root, "localUrl");
      apiToken = Tool.getChildValueByTagName(root, "apiToken");
      localPassword = Tool.decryptPassword(Tool.getChildValueByTagName(root, "localPassword"));
   }

   @Override
   protected String getTestSuffix() {
      return "/rest/api/2/myself";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof JiraDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      final JiraDataSource that = (JiraDataSource) o;
      return Objects.equals(serverType, that.serverType) &&
         Objects.equals(cloudDomain, that.cloudDomain) &&
         Objects.equals(localUrl, that.localUrl) &&
         Objects.equals(apiToken, that.apiToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), serverType, cloudDomain, localUrl, apiToken);
   }

   private String serverType = "cloud";
   private String cloudDomain;
   private String localUrl;
   private String apiToken;
   private String localPassword;
}
