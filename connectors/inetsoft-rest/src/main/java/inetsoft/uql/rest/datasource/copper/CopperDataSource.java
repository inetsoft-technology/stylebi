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
package inetsoft.uql.rest.datasource.copper;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("userEmail"),
   @View1("apiKey")
})
public class CopperDataSource extends EndpointJsonDataSource<CopperDataSource> {
   static String TYPE = "Rest.Copper";

   public CopperDataSource() {
      super(TYPE, CopperDataSource.class);
      setAuthType(AuthType.NONE);
   }

   /**
    * Gets the user email address for the account.
    *
    * @return the email address.
    */
   @Property(label = "Email", required = true)
   public String getUserEmail() {
      return userEmail;
   }

   /**
    * Sets the user email address for the account.
    *
    * @param userEmail the email address.
    */
   public void setUserEmail(String userEmail) {
      this.userEmail = userEmail;
   }

   /**
    * Gets the API key for the account.
    *
    * @return the API key.
    */
   @Property(label = "API Key", required = true)
   public String getApiKey() {
      return apiKey;
   }

   /**
    * Sets the API key for the account.
    *
    * @param apiKey the API key.
    */
   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public String getURL() {
      return "https://api.prosperworks.com/developer_api";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter typeParam = new HttpParameter();
      HttpParameter keyParam = new HttpParameter();
      HttpParameter emailParam = new HttpParameter();
      HttpParameter application = new HttpParameter();
      typeParam.setName("Content-Type");
      typeParam.setValue("application/json");
      typeParam.setType(HttpParameter.ParameterType.HEADER);
      keyParam.setName("X-PW-AccessToken");
      keyParam.setValue(apiKey);
      keyParam.setType(HttpParameter.ParameterType.HEADER);
      emailParam.setName("X-PW-UserEmail");
      emailParam.setValue(userEmail);
      emailParam.setType(HttpParameter.ParameterType.HEADER);
      application.setName("X-PW-Application");
      application.setValue("developer_api");
      application.setType(HttpParameter.ParameterType.HEADER);
      return new HttpParameter[] { typeParam, keyParam, emailParam, application };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(userEmail != null) {
         writer.format("<userEmail><![CDATA[%s]]></userEmail>%n", userEmail);
      }

      if(apiKey != null) {
         writer.format("<apiKey><![CDATA[%s]]></apiKey>%n", Tool.encryptPassword(apiKey));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      userEmail = Tool.getChildValueByTagName(root, "userEmail");
      apiKey = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiKey"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v1/account";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof CopperDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      CopperDataSource that = (CopperDataSource) o;
      return Objects.equals(userEmail, that.userEmail) &&
         Objects.equals(apiKey, that.apiKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), userEmail, apiKey);
   }

   private String userEmail;
   private String apiKey;
}
