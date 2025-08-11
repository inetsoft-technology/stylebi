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
package inetsoft.uql.gdata;

import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

@SuppressWarnings("unused")
@View(vertical = true, value = {
   @View1("spreadsheet"),
   @View1("worksheetId"),
   @View1("firstRowAsHeader")
})
public class GDataQuery extends TabularQuery {
   public GDataQuery() {
      super(GDataDataSource.TYPE);
   }

   @Property(label = "Spreadsheet")
   public GooglePicker getSpreadsheet() {
      if(spreadsheet == null) {
         spreadsheet = new GooglePicker();
      }

      GDataDataSource dataSource = (GDataDataSource) getDataSource();
      refreshToken();
      spreadsheet.setOauthToken(dataSource.getAccessToken());
      return spreadsheet;
   }

   private void refreshToken() {
      GDataDataSource dataSource = (GDataDataSource) getDataSource();

      if(!Instant.now().isAfter(Instant.ofEpochMilli(dataSource.getTokenExpiration()))) {
         return;
      }

      // Update the token expiration to prevent rapid retries when the refresh fails
      dataSource.setTokenExpiration(Instant.now().toEpochMilli() + 5000);

      try {
         Tokens tokens = AuthorizationClient.refresh("google-sheets-picker",
            dataSource.getRefreshToken(), null);
         dataSource.updateTokens(tokens);
      }
      catch(Exception e) {
         LOG.error("Failed to refresh Google Sheets picker access token", e);
      }
   }

   public void setSpreadsheet(GooglePicker spreadsheet) {
      this.spreadsheet = spreadsheet;
   }

   @Property(label = "Worksheet")
   @PropertyEditor(tagsMethod = "getWorksheets", dependsOn = { "spreadsheet" })
   public String getWorksheetId() {
      return worksheetId;
   }

   public void setWorksheetId(String worksheetId) {
      this.worksheetId = worksheetId;
   }

   @Property(label = "First Row as Header")
   public boolean isFirstRowAsHeader() {
      return firstRowAsHeader;
   }

   public void setFirstRowAsHeader(boolean firstRowAsHeader) {
      this.firstRowAsHeader = firstRowAsHeader;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(spreadsheet != null) {
         writer.println("<spreadsheet>");
         spreadsheet.writeXML(writer);
         writer.println("</spreadsheet>");
      }

      if(worksheetId != null) {
         writer.format("<worksheetId><![CDATA[%s]]></worksheetId>%n", worksheetId);
      }

      writer.format("<firstRowAsHeader>%s</firstRowAsHeader>%n", firstRowAsHeader);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      Element element;

      if((element = Tool.getChildNodeByTagName(root, "spreadsheet")) != null) {
         if((element = Tool.getChildNodeByTagName(element, "googlePicker")) != null) {
            spreadsheet = new GooglePicker();
            spreadsheet.parseXML(element);
         }
      }

      if((element = Tool.getChildNodeByTagName(root, "worksheetId")) != null) {
         worksheetId = Tool.getValue(element);
      }

      if((element = Tool.getChildNodeByTagName(root, "firstRowAsHeader")) != null) {
         String value = Tool.getValue(element);

         if(value == null) {
            firstRowAsHeader = true;
         }
         else {
            firstRowAsHeader = Boolean.parseBoolean(value);
         }
      }
   }

   public String[][] getWorksheets() {
      if(spreadsheet != null && spreadsheet.getSelectedFile() != null) {
         try {
            return GDataRuntime.listWorksheets((GDataDataSource) getDataSource(),
                                               spreadsheet.getSelectedFile().getId());
         }
         catch(IOException e) {
            LOG.error("Failed to list worksheets", e);
         }
      }

      return new String[0][];
   }

   private GooglePicker spreadsheet;
   private String worksheetId;
   private boolean firstRowAsHeader = true;

   private static final Logger LOG = LoggerFactory.getLogger(GDataQuery.class.getName());
}

