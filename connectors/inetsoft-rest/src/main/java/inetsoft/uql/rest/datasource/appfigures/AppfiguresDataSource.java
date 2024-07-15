/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.appfigures;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("clientKey"),
   @View1("user"),
   @View1("password")
})
public class AppfiguresDataSource extends EndpointJsonDataSource<AppfiguresDataSource> {
   static final String TYPE = "Rest.Appfigures";

   public AppfiguresDataSource() {
      super(TYPE, AppfiguresDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "Client Key", required = true, password = true)
   public String getClientKey() {
      return clientKey;
   }

   public void setClientKey(String clientKey) {
      this.clientKey = clientKey;
   }

   @Override
   public String getURL() {
      return "https://api.appfigures.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("X-Client-Key")
            .value(clientKey)
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

      if(clientKey != null) {
         writer.format("<clientKey><![CDATA[%s]]></clientKey>%n", Tool.encryptPassword(clientKey));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      clientKey = Tool.decryptPassword(Tool.getChildValueByTagName(root, "clientKey"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v2/";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof AppfiguresDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      AppfiguresDataSource that = (AppfiguresDataSource) o;
      return Objects.equals(clientKey, that.clientKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), clientKey);
   }

   private String clientKey;
}
