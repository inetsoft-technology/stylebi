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
package inetsoft.uql.rest.datasource.gitlab;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("token"),
})
public class GitLabDataSource extends EndpointJsonDataSource<GitLabDataSource> {
   public static final String TYPE = "Rest.GitLab";

   public GitLabDataSource() {
      super(TYPE, GitLabDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "Token", required = true, password = true)
   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   @Override
   public String getURL() {
      return "https://gitlab.com/api";
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
            .name("Private-Token")
            .value(token)
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

      if(token != null) {
         writer.format("<token><![CDATA[%s]]></token>%n", Tool.encryptPassword(token));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      token = Tool.decryptPassword(Tool.getChildValueByTagName(root, "token"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v4/user";
   }

   @Override
   public boolean equals(Object obj) {
      try {
         GitLabDataSource ds = (GitLabDataSource) obj;

         return Objects.equals(token, ds.token);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String token;
}
