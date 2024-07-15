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
package inetsoft.uql.rest.datasource.gosquared;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("apiKey"),
   @View1("siteToken")
})
public class GoSquaredDataSource extends EndpointJsonDataSource<GoSquaredDataSource> {
   static final String TYPE = "GoSquared";

   public GoSquaredDataSource() {
      super(TYPE, GoSquaredDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "API Key", required = true, password = true)
   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Property(label = "Site Token", required = true, password = true)
   public String getSiteToken() {
      return siteToken;
   }

   public void setSiteToken(String siteToken) {
      this.siteToken = siteToken;
   }

   @Override
   public String getURL() {
      return "https://api.gosquared.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      HttpParameter keyParam = new HttpParameter();
      keyParam.setName("api_key");
      keyParam.setValue(apiKey);
      keyParam.setType(HttpParameter.ParameterType.QUERY);

      HttpParameter tokenParam = new HttpParameter();
      tokenParam.setName("site_token");
      tokenParam.setValue(siteToken);
      tokenParam.setType(HttpParameter.ParameterType.QUERY);

      HttpParameter acceptToken = new HttpParameter();
      acceptToken.setName("Accept");
      acceptToken.setValue("application/json");
      acceptToken.setType(HttpParameter.ParameterType.HEADER);

      return new HttpParameter[] { keyParam, tokenParam, acceptToken };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(apiKey != null) {
         writer.format("<apiKey><![CDATA[%s]]></apiKey>%n", Tool.encryptPassword(apiKey));
      }

      if(siteToken != null) {
         writer.format("<siteToken><![CDATA[%s]]></siteToken>%n", Tool.encryptPassword(siteToken));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      apiKey = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiKey"));
      siteToken = Tool.decryptPassword(Tool.getChildValueByTagName(root, "siteToken"));
   }

   @Override
   protected String getTestSuffix() {
      return "/auth/v1/tokeninfo";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof GoSquaredDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      GoSquaredDataSource that = (GoSquaredDataSource) o;
      return Objects.equals(apiKey, that.apiKey) &&
         Objects.equals(siteToken, that.siteToken);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), apiKey, siteToken);
   }

   private String apiKey;
   private String siteToken;
}
