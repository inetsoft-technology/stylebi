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
package inetsoft.uql.rest.datasource.sfreports;

import inetsoft.uql.rest.datasource.salesforce.SalesforceDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("accountType"),
   @View1("apiVersion"),
   @View1("user"),
   @View1("password"),
   @View1("securityToken")
})
public class SalesforceReportsDataSource extends SalesforceDataSource<SalesforceReportsDataSource> {
   static final String TYPE = "Rest.SalesforceReports";
   
   public SalesforceReportsDataSource() {
      super(TYPE, SalesforceReportsDataSource.class);
   }

   @Property(label = "Account Type", required = true)
   @PropertyEditor(tagsMethod = "getAccountTypes")
   public String getAccountType() {
      return accountType;
   }

   public void setAccountType(String accountType) {
      this.accountType = accountType;
   }

   public String[][] getAccountTypes() {
      return new String[][] {
         { "Developer", "developer" },
         { "Enterprise/Professional", "enterprise" },
         { "Unlimited", "unlimited" },
         { "Sandbox", "sandbox" }
      };
   }

   @Property(label = "API Version", required = true)
   public String getApiVersion() {
      return apiVersion;
   }

   public void setApiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
   }

   @Override
   protected String getUrlSuffix() {
      return "/data";
   }

   @Override
   protected String getTestSuffix() {
      return "/" + apiVersion + "/analytics/dashboards";
   }

   @Override
   public double getRequestsPerSecond() {
      if(accountType == null) {
         accountType = "developer";
      }

      double callsPerDay;

      switch(accountType) {
      case "enterprise":
      case "unlimited":
         callsPerDay = 100000;
         break;
      case "sandbox":
         callsPerDay = 500000;
         break;
      default:
         callsPerDay = 15000;
      }

      return Math.max(1D, callsPerDay / 86400);
   }

   @Override
   public void setRequestsPerSecond(double requestsPerSecond) {
      // no-op
   }

   @Override
   public int getMaxConnections() {
      return "developer".equals(accountType) ? 5 : 25;
   }

   @Override
   public void setMaxConnections(int maxConnections) {
      // no-op
   }

   @Override
   protected String getLoginUrl() {
      if("sandbox".equals(accountType)) {
         return "https://test.salesforce.com/services/Soap/u/35.0";
      }

      return super.getLoginUrl();
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(accountType != null) {
         writer.format("<accountType><![CDATA[%s]]></accountType>%n", accountType);
      }

      if(apiVersion != null) {
         writer.format("<apiVersion><![CDATA[%s]]></apiVersion>%n", apiVersion);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      accountType = Tool.getChildValueByTagName(root, "accountType");
      apiVersion = Tool.getChildValueByTagName(root, "apiVersion");
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      SalesforceReportsDataSource that = (SalesforceReportsDataSource) o;
      return Objects.equals(apiVersion, that.apiVersion) &&
         Objects.equals(accountType, that.accountType);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), apiVersion, accountType);
   }

   private String apiVersion = "v48.0";
   private String accountType = "developer";
}
