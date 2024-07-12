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
package inetsoft.uql.rest.datasource.adobeanalytics;

import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.CoreTool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@View(vertical = true, value = {
   @View1("apiKey"),
   @View1("globalCompanyId"),
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = "adobe-analytics"))),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class AdobeAnalyticsDataSource extends OAuthEndpointJsonDataSource<AdobeAnalyticsDataSource> {
   static final String TYPE = "Rest.AdobeAnalytics";
   
   public AdobeAnalyticsDataSource() {
      super(TYPE, AdobeAnalyticsDataSource.class);
   }

   @PropertyEditor(enabled = false)
   @Property(label = "API Key", required = true)
   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Property(label = "Global Company ID", required = true)
   public String getGlobalCompanyId() {
      return globalCompanyId;
   }

   public void setGlobalCompanyId(String globalCompanyId) {
      this.globalCompanyId = globalCompanyId;
   }

   @Override
   public void updateTokens(Tokens tokens) {
      super.updateTokens(tokens);
      setTokenExpiration(tokens.issued() + TimeUnit.MILLISECONDS.convert(24L, TimeUnit.HOURS));
   }

   @Override
   public String getURL() {
      return "https://analytics.adobe.io/api/" + globalCompanyId;
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   protected String getTestSuffix() {
      return "/users/me";
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens();
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Content-Type")
            .value("application/json")
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Bearer " + getAccessToken())
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("x-proxy-global-company-id")
            .value(globalCompanyId)
            .build(),
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("x-api-key")
            .value(apiKey)
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
      writer.format("<globalCompanyId>%s</globalCompanyId>\n", globalCompanyId);
      writer.format("<apiKey>%s</apiKey>\n", apiKey);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      globalCompanyId = CoreTool.getChildValueByTagName(root, "globalCompanyId");
      apiKey = CoreTool.getChildValueByTagName(root, "apiKey");
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

      AdobeAnalyticsDataSource that = (AdobeAnalyticsDataSource) o;

      return globalCompanyId.equals(that.globalCompanyId) && apiKey.equals(that.apiKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), globalCompanyId, apiKey);
   }

   private String globalCompanyId;
   // client id of adobe oauth app
   private String apiKey = "78cd0fd9ba8d4e58a89379adcf37aaf5";
}
