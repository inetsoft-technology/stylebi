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
package inetsoft.uql.rest.datasource.liveagent;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("URL"),
   @View1("apiKey")
})
public class LiveAgentDataSource extends EndpointJsonDataSource<LiveAgentDataSource> {
   public static final String TYPE = "Rest.LiveAgent";

   public LiveAgentDataSource() {
      super(TYPE, LiveAgentDataSource.class);
      setURL("https://[Domain Name].ladesk.com/api");
      setAuthType(AuthType.NONE);
   }

   @Property(label = "API Key", required = true)
   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("apikey")
            .value(apiKey)
            .build()
      };
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(apiKey != null) {
         writer.format("<apiKey><![CDATA[%s]]></apiKey>%n", Tool.encryptPassword(apiKey));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      apiKey = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiKey"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v3/ping";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof LiveAgentDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      LiveAgentDataSource that = (LiveAgentDataSource) o;
      return Objects.equals(apiKey, that.apiKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), apiKey);
   }

   private String apiKey;
}
