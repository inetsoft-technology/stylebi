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
package inetsoft.uql.rest.datasource.fortytwomatters;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("accessToken"),
   @View1("freeTrial")
})
public class FortyTwoMattersDataSource extends EndpointJsonDataSource<FortyTwoMattersDataSource> {
   public static final String TYPE = "Rest.FortyTwoMatters";

   public FortyTwoMattersDataSource() {
      super(TYPE, FortyTwoMattersDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "Access Token", password = true, required = true)
   public String getAccessToken() {
      HttpParameter[] parameters = getQueryHttpParameters();

      if(parameters != null) {
         for(HttpParameter parameter : parameters) {
            if("access_token".equals(parameter.getName())) {
               return parameter.getValue();
            }
         }
      }

      return null;
   }

   public void setAccessToken(String accessToken) {
      HttpParameter token = new HttpParameter();
      token.setName("access_token");
      token.setValue(accessToken);
      token.setType(HttpParameter.ParameterType.QUERY);
      setQueryHttpParameters(new HttpParameter[]{token});
   }

   @Property(label = "Free Trial")
   public boolean isFreeTrial() {
      return freeTrial;
   }

   public void setFreeTrial(boolean freeTrial) {
      this.freeTrial = freeTrial;
   }

   @Override
   public String getURL() {
      return "https://data.42matters.com/api";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.format(" freeTrial=\"%s\"", freeTrial);
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      freeTrial = "true".equals(Tool.getAttribute(tag, "freeTrial"));
   }

   @Override
   protected String getTestSuffix() {
      return "/v2.0/account.json";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof FortyTwoMattersDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      FortyTwoMattersDataSource that = (FortyTwoMattersDataSource) o;
      return freeTrial == that.freeTrial;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), freeTrial);
   }

   private boolean freeTrial;
}
