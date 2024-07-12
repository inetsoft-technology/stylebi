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
package inetsoft.uql.rest.datasource.toggl;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("apiToken")
})
public class TogglDataSource extends EndpointJsonDataSource<TogglDataSource> {
   static final String TYPE = "Rest.Toggl";
   
   public TogglDataSource() {
      super(TYPE, TogglDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "API Token", required = true, password = true)
   public String getApiToken() {
      return apiToken;
   }

   public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
   }

   @Override
   public String getURL() {
      return "https://www.toggl.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public String getUser() {
      return apiToken;
   }

   @Override
   public void setUser(String user) {
      // no-op
   }

   @Override
   public String getPassword() {
      return "api_token";
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Accept")
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

      if(apiToken != null) {
         writer.format("<apiToken><![CDATA[%s]]></apiToken>", Tool.encryptPassword(apiToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      apiToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiToken"));
   }

   @Override
   protected String getTestSuffix() {
      return "/api/v8/me";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof TogglDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      TogglDataSource that = (TogglDataSource) o;
      return Objects.equals(apiToken, that.apiToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), apiToken);
   }

   private String apiToken;
}
