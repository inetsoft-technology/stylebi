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
package inetsoft.uql.rest.datasource.chartmogul;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("accountToken"),
   @View1("secretKey")
})
public class ChartMogulDataSource extends EndpointJsonDataSource<ChartMogulDataSource> {
   public static final String TYPE = "Rest.ChartMogul";

   public ChartMogulDataSource() {
      super(TYPE, ChartMogulDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "Account Token", required = true, password = true)
   public String getAccountToken() {
      return accountToken;
   }

   public void setAccountToken(String accountToken) {
      this.accountToken = accountToken;
   }

   @Property(label = "Secret Key", required = true, password = true)
   public String getSecretKey() {
      return secretKey;
   }

   public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
   }

   @Override
   public String getURL() {
      return "https://api.chartmogul.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public String getUser() {
      return accountToken;
   }

   @Override
   public void setUser(String user) {
      // no-op
   }

   @Override
   public String getPassword() {
      return secretKey;
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(accountToken != null) {
         writer.format(
            "<accountToken><![CDATA[%s]]></accountToken>%n", Tool.encryptPassword(accountToken));
      }

      if(secretKey != null) {
         writer.format(
            "<secretKey><![CDATA[%s]]></secretKey>%n", Tool.encryptPassword(secretKey));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      accountToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "accountToken"));
      secretKey = Tool.decryptPassword(Tool.getChildValueByTagName(root, "secretKey"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v1/customers";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof ChartMogulDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      ChartMogulDataSource that = (ChartMogulDataSource) o;
      return Objects.equals(accountToken, that.accountToken) &&
         Objects.equals(secretKey, that.secretKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), accountToken, secretKey);
   }

   private String accountToken;
   private String secretKey;
}
