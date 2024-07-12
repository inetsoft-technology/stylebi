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
package inetsoft.uql.rest.datasource.harvest;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("accountId"),
   @View1("accessToken")
})
public class HarvestDataSource extends EndpointJsonDataSource<HarvestDataSource> {
   static final String TYPE = "Rest.Harvest";

   public HarvestDataSource() {
      super(TYPE, HarvestDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "Account ID", required = true)
   public String getAccountId() {
      return accountId;
   }

   public void setAccountId(String accountId) {
      this.accountId = accountId;
   }

   @Property(label = "Personal Access Token", required = true, password = true)
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public String getURL() {
      return "https://api.harvestapp.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter accountParam = new HttpParameter();
      accountParam.setName("Harvest-Account-ID");
      accountParam.setValue(accountId);
      accountParam.setType(HttpParameter.ParameterType.HEADER);

      HttpParameter authParam = new HttpParameter();
      authParam.setName("Authorization");
      authParam.setValue("Bearer " + accessToken);
      authParam.setType(HttpParameter.ParameterType.HEADER);

      HttpParameter agentParam = new HttpParameter();
      agentParam.setName("User-Agent");
      agentParam.setValue("InetSoft");
      agentParam.setType(HttpParameter.ParameterType.HEADER);

      return new HttpParameter[] { accountParam, authParam, agentParam };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(accountId != null) {
         writer.format("<accountId><![CDATA[%s]]></accountId>%n", accountId);
      }

      if(accessToken != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(accessToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      accountId = Tool.getChildValueByTagName(root, "accountId");
      accessToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "accessToken"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v2/users/me";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof HarvestDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      HarvestDataSource that = (HarvestDataSource) o;
      return Objects.equals(accountId, that.accountId) &&
         Objects.equals(accessToken, that.accessToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), accountId, accessToken);
   }

   private String accountId;
   private String accessToken;
}
