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
package inetsoft.uql.rest.datasource.monday;

import inetsoft.uql.rest.datasource.graphql.AbstractGraphQLDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.apache.http.HttpHeaders;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(value= {
   @View1("apiKey")
})
public class MondayDataSource extends AbstractGraphQLDataSource<MondayDataSource> {
   public MondayDataSource() {
      super(TYPE, MondayDataSource.class);
   }

   @Override
   public HttpParameter[] getRequestParameters() {
      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name(HttpHeaders.AUTHORIZATION)
            .value(apiKey)
            .build()
      };
   }

   @Override
   public String getURL() {
      return "https://api.monday.com/v2";
   }

   @Property(label = "API Key", required = true)
   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(apiKey != null) {
         writer.format("<apiKey>%s</apiKey>\n", Tool.encryptPassword(apiKey));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      apiKey = Tool.decryptPassword(CoreTool.getChildValueByTagName(root, "apiKey"));
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

      MondayDataSource that = (MondayDataSource) o;
      return Objects.equals(apiKey, that.apiKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), apiKey);
   }

   static String TYPE = "monday.com";
   private String apiKey;
}
