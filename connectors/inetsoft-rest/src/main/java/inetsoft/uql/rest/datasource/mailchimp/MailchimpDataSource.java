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
package inetsoft.uql.rest.datasource.mailchimp;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("datacenter"),
   @View1("apiKey"),
   @View1("URL")
})
public class MailchimpDataSource extends EndpointJsonDataSource<MailchimpDataSource> {
   public static final String TYPE = "Rest.Mailchimp";

   public MailchimpDataSource() {
      super(TYPE, MailchimpDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "Data Center", required = true)
   public String getDatacenter() {
      return datacenter;
   }

   public void setDatacenter(String datacenter) {
      this.datacenter = datacenter;
   }

   @Property(label = "API Key", required = true, password = true)
   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public String getPassword() {
      return apiKey;
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = "datacenter")
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("https://");

      if(datacenter == null) {
         url.append("[dc]");
      }
      else {
         url.append(datacenter);
      }

      return url.append(".api.mailchimp.com").toString();
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(datacenter != null) {
         writer.format("<datacenter><![CDATA[%s]]></datacenter>%n", datacenter);
      }

      if(apiKey != null) {
         writer.format("<apiKey><![CDATA[%s]]></apiKey>%n", Tool.encryptPassword(apiKey));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      datacenter = Tool.getChildValueByTagName(root, "datacenter");
      apiKey = Tool.decryptPassword(Tool.getChildValueByTagName(root, "apiKey"));
   }

   @Override
   protected String getTestSuffix() {
      return "/3.0/";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof MailchimpDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      MailchimpDataSource that = (MailchimpDataSource) o;
      return Objects.equals(datacenter, that.datacenter) &&
         Objects.equals(apiKey, that.apiKey);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), datacenter, apiKey);
   }

   private String datacenter;
   private String apiKey;
}
