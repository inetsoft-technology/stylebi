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
package inetsoft.uql.rest.datasource.fortytwomatters;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "accessToken", visibleMethod = "useCredential"),
   @View1("freeTrial")
})
public class FortyTwoMattersDataSource extends EndpointJsonDataSource<FortyTwoMattersDataSource> {
   public static final String TYPE = "Rest.FortyTwoMatters";

   public FortyTwoMattersDataSource() {
      super(TYPE, FortyTwoMattersDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.ACCESS_TOKEN;
   }

   @Property(label = "Access Token", password = true, required = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getAccessToken() {
      String accessToken = null;

      if(getCredential() instanceof AccessTokenCredential &&
         !Tool.isEmptyString(((AccessTokenCredential) getCredential()).getAccessToken()))
      {
         accessToken = ((AccessTokenCredential) getCredential()).getAccessToken();
      }

      HttpParameter[] parameters = getQueryHttpParameters();

      if(parameters != null) {
         for(HttpParameter parameter : parameters) {
            if("access_token".equals(parameter.getName())) {
               if(accessToken == null) {
                  return parameter.getValue();
               }
               else if(!Tool.equals(parameter.getValue(), accessToken)) {
                  parameter.setValue(accessToken);
                  return accessToken;
               }
            }
         }
      }

      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      HttpParameter token = new HttpParameter();
      token.setSecret(true);
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
