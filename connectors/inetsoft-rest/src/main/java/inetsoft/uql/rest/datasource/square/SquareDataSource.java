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
package inetsoft.uql.rest.datasource.square;

import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("sandbox"),
   @View1("accessToken")
})
public class SquareDataSource extends EndpointJsonDataSource<SquareDataSource> {
   static final String TYPE = "Rest.Square";
   
   public SquareDataSource() {
      super(TYPE, SquareDataSource.class);
   }

   @Property(label = "Sandbox", required = true)
   public boolean isSandbox() {
      return sandbox;
   }

   public void setSandbox(boolean sandbox) {
      this.sandbox = sandbox;
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
      return sandbox ? "https://connect.squareupsandbox.com" : "https://connect.squareup.com";
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
            .name("Accept")
            .value("application/json")
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Cache-Control")
            .value("no-cache")
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Bearer " + accessToken)
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/v2/locations";
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.format(" sandbox=\"%s\"", sandbox);
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      sandbox = "true".equals(Tool.getAttribute(tag, "sandbox"));
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(accessToken != null) {
         writer.format(
            "<accessToken><![CDATA[%s]]></accessToken>%n", Tool.encryptPassword(accessToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      accessToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "accessToken"));
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof SquareDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      SquareDataSource that = (SquareDataSource) o;
      return sandbox == that.sandbox && Objects.equals(accessToken, that.accessToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), sandbox, accessToken);
   }

   private boolean sandbox;
   private String accessToken;
}
