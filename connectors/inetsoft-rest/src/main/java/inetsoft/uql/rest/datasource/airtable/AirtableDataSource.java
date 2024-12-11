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
package inetsoft.uql.rest.datasource.airtable;

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
   @View1(value = "apiToken", visibleMethod = "useCredential"),
   @View1("testBaseID"),
   @View1("testTable")
})
public class AirtableDataSource extends EndpointJsonDataSource<AirtableDataSource> {
   static final String TYPE = "Rest.Airtable";

   public AirtableDataSource() {
      super(TYPE, AirtableDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_TOKEN;
   }

   @Property(label = "API Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiToken() {
      return ((ApiTokenCredential) getCredential()).getApiToken();
   }

   public void setApiToken(String apiToken) {
      ((ApiTokenCredential) getCredential()).setApiToken(apiToken);
   }

   @Property(label = "Test Base ID")
   public String getTestBaseID() {
      return testBaseID;
   }

   public void setTestBaseID(String testBaseID) {
      this.testBaseID = testBaseID;
   }

   @Property(label = "Test Table")
   public String getTestTable() {
      return testTable;
   }

   public void setTestTable(String testTable) {
      this.testTable = testTable;
   }

   @Override
   protected String getTestSuffix() {
      if(testBaseID == null || testBaseID.isEmpty() || testTable == null || testTable.isEmpty()) {
         return null;
      }
      else {
         return "v0/" + testBaseID + "/" + testTable;
      }
   }

   @Override
   public String getURL() {
      return "https://api.airtable.com/";
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
            .name("Authorization")
            .value("Bearer " + getApiToken())
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

      if(testBaseID != null) {
         writer.format("<testBaseID><![CDATA[%s]]></testBaseID>%n", testBaseID);
      }

      if(testTable != null) {
         writer.format("<testTable><![CDATA[%s]]></testTable>%n", testTable);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      testBaseID = Tool.getChildValueByTagName(root, "testBaseID");
      testTable = Tool.getChildValueByTagName(root, "testTable");
   }

   @Override
   public boolean equals(Object obj) {
      try {
         AirtableDataSource ds = (AirtableDataSource) obj;
         return Objects.equals(testBaseID, ds.testBaseID) && Objects.equals(testTable, ds.testTable);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String testBaseID;
   private String testTable;
}
