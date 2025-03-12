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
package inetsoft.uql.rest.datasource.azuresearch;

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
   @View1(value = "serviceName"),
   @View1(value = "apiKey", visibleMethod = "useCredential"),
   @View1("URL")
})
public class AzureSearchDataSource extends EndpointJsonDataSource<AzureSearchDataSource> {
   static final String TYPE = "Rest.AzureSearch";

   public AzureSearchDataSource() {
      super(TYPE, AzureSearchDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_KEY;
   }

   @Property(label = "Search Service Name", required = true)
   public String getServiceName() {
      return serviceName;
   }

   public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
   }

   @Property(label = "API Key", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiKey() {
      return ((ApiKeyCredential) getCredential()).getApiKey();
   }

   public void setApiKey(String apiKey) {
      ((ApiKeyCredential) getCredential()).setApiKey(apiKey);
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = "serviceName")
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("https://");

      if(serviceName == null) {
         url.append("[Search Service Name]");
      }
      else {
         url.append(serviceName);
      }

      return url.append(".search.windows.net").toString();
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter keyParam = new HttpParameter();
      keyParam.setName("api-key");
      keyParam.setSecret(true);
      keyParam.setValue(getApiKey());
      keyParam.setType(HttpParameter.ParameterType.HEADER);


      HttpParameter contentParam = new HttpParameter();
      contentParam.setName("Content-Type");
      contentParam.setValue("application/json");
      contentParam.setType(HttpParameter.ParameterType.HEADER);

      return new HttpParameter[] { keyParam, contentParam };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(serviceName != null) {
         writer.format("<serviceName><![CDATA[%s]]></serviceName>%n", serviceName);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      serviceName = Tool.getChildValueByTagName(root, "serviceName");
   }

   @Override
   protected String getTestSuffix() {
      return "/servicestats?api-version=2019-05-06";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof AzureSearchDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      AzureSearchDataSource that = (AzureSearchDataSource) o;
      return Objects.equals(serviceName, that.serviceName);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), serviceName, getApiKey());
   }

   private String serviceName;
}
