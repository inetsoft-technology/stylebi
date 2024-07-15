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
package inetsoft.uql.rest.datasource.shopify;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.datasource.graphql.AbstractGraphQLDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.CoreTool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value= {
   @View1("shop"),
   @View1("apiVersion"),
   @View1("URL"),
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements =  {
         @View2("clientId"),
         @View2("clientSecret"),
         @View2("authorizationUri"),
         @View2("tokenUri"),
         @View2("scope"),
         @View2(
            type = ViewType.BUTTON,
            text = "Authorize",
            button = @Button(
               type = ButtonType.OAUTH,
               method = "updateTokens",
               oauth = @Button.OAuth)
         ),
         @View2("accessToken")
      }
   )
})
public class ShopifyDataSource extends AbstractGraphQLDataSource<ShopifyDataSource> {
   public ShopifyDataSource() {
      super(TYPE, ShopifyDataSource.class);
   }

   @Override
   @Property(label = "Authorization URI", required = true)
   @PropertyEditor(enabled = false, dependsOn = "shop")
   public String getAuthorizationUri() {
      return String.format("https://%s.myshopify.com/admin/oauth/authorize", shop);
   }

   @Override
   @Property(label = "Token URI", required = true)
   @PropertyEditor(enabled = false, dependsOn = "shop")
   public String getTokenUri() {
      return String.format("https://%s.myshopify.com/admin/oauth/access_token", shop);
   }

   @Override
   @Property(label = "Scope", required = true)
   public String getScope() {
      return scope;
   }

   @Override
   public void setScope(String scope) {
      this.scope = scope;
   }

   @Override
   public String getOauthFlags() {
      return "credentialsInTokenRequestBody";
   }

   @Override
   public AuthType getAuthType() {
      return AuthType.OAUTH;
   }

   @Override
   public HttpParameter[] getRequestParameters() {
      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("X-Shopify-Access-Token")
            .value(getAccessToken())
            .build()
      };
   }

   @Property(label = "URL", required = true)
   @PropertyEditor(dependsOn = {"shop", "apiVersion"})
   public String getURL() {
      return String.format("https://%s.myshopify.com/admin/api/%s/graphql.json", shop, apiVersion);
   }

   @Property(label = "Shop")
   public String getShop() {
      return shop;
   }

   public void setShop(String shop) {
      this.shop = shop;
   }

   @Property(label = "API Version")
   public String getApiVersion() {
      return apiVersion;
   }

   public void setApiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.format("<shop>%s</shop>\n", shop);
      writer.format("<apiVersion>%s</apiVersion>\n", apiVersion);
      writer.format("<scope>%s</scope>\n", scope);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      shop = CoreTool.getChildValueByTagName(root, "shop");
      apiVersion = CoreTool.getChildValueByTagName(root, "apiVersion");
      scope = CoreTool.getChildValueByTagName(root, "scope");
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

      ShopifyDataSource that = (ShopifyDataSource) o;
      return Objects.equals(shop, that.shop) &&
         Objects.equals(apiVersion, that.apiVersion) &&
         Objects.equals(scope, that.scope);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), shop, apiVersion, scope);
   }

   static String TYPE = "shopify";
   private String shop = "{shop}";
   private String apiVersion = "2020-04";
   private String scope = "read_products read_customers read_draft_orders read_orders read_price_rules";
}
