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
package inetsoft.uql.rest.datasource.quickbooks;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.Tool;
import org.apache.http.HttpHeaders;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Objects;

@View(
   vertical = true,
   value = {
      @View1(type = ViewType.BUTTON, text = "Authorize", button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = QuickbooksReportsDataSource.SERVICE_NAME)
      )),
      @View1("accessToken"),
      @View1("refreshToken"),
      @View1("tokenExpiration"),
      @View1("companyId"),
      @View1(
         type = ViewType.PANEL,
         align = ViewAlign.LEFT,
         elements = {
            @View2(value = "production")
         }
      )
   }
)
public class QuickbooksReportsDataSource
   extends OAuthEndpointJsonDataSource<QuickbooksReportsDataSource>
{
   public QuickbooksReportsDataSource() {
      super(TYPE, QuickbooksReportsDataSource.class);
   }

   @Override
   public boolean isBasicAuth() {
      return true;
   }

   @Override
   public String getServiceName() {
      return QuickbooksReportsDataSource.SERVICE_NAME;
   }

   @Override
   public AuthType getAuthType() {
      return AuthType.OAUTH;
   }

   public void updateTokens(Tokens tokens) {
      super.updateTokens(tokens);
      final Map<String, Object> properties = tokens.properties();

      if(properties != null && properties.containsKey("realmId")) {
         this.companyId = (String) properties.get("realmId");
      }
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name(HttpHeaders.AUTHORIZATION)
            .value("Bearer " + getAccessToken())
            .build()
      };
   }

   @Override
   public String getURL() {
      return (production ? productionUrl : sandboxUrl) + companyId;
   }

   @Override
   protected String getTestSuffix() {
      return "/query?query=select * from CompanyInfo";
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<companyId><![CDATA[" + companyId + "]]></companyId>");
      writer.println("<production><![CDATA[" + production + "]]></production>");
   }

   @Override
   public void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      companyId = Tool.getChildValueByTagName(tag, "companyId");
      production = Boolean.parseBoolean(Tool.getChildValueByTagName(tag, "production"));
   }

   @Property(label = "Company ID", required = true)
   @PropertyEditor(enabled = false)
   public String getCompanyId() {
      return companyId;
   }

   public void setCompanyId(String companyId) {
      this.companyId = companyId;
   }

   @Property(label = "Production Mode")
   public boolean isProduction() {
      return production;
   }

   public void setProduction(boolean production) {
      this.production = production;
   }

   @Override
   public boolean equals(Object obj) {
      try {
         QuickbooksReportsDataSource ds = (QuickbooksReportsDataSource) obj;

         return Objects.equals(companyId, ds.companyId) && production == ds.production;
      }
      catch(Exception ex) {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), companyId, production);
   }

   public static final String SERVICE_NAME = "quickbooks-online";
   public static final String TYPE = "Rest.QuickbooksReports";
   private static final String sandboxUrl = "https://sandbox-quickbooks.api.intuit.com/v3/company/";
   private static final String productionUrl = "https://quickbooks.api.intuit.com/v3/company/";
   private String companyId;
   private boolean production;
}
