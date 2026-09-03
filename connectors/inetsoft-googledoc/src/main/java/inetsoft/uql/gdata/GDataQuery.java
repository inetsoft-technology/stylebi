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
import inetsoft.util.*;
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

      if(dataSource == null) {
         throw new MessageException(Catalog.getCatalog().getString("data.datasources.problemRetrievingDataSource"));
      }

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

   /**
    * The selected spreadsheet's Drive file id, for a caller that fills this query through its bean
    * properties rather than through the Google Picker dialog. Its picker counterpart,
    * {@link #getSpreadsheet()}, cannot be filled that way at all: {@code GooglePicker} is a plain
    * bean, so {@code TabularUtil.compositeElementsOf} yields null and
    * {@code buildCompositeFragment} takes the skeleton branch, omitting {@code spreadsheet} from
    * the query schema entirely.
    *
    * <p>Deliberately outside this class's {@code @View} and carrying no {@code tagsMethod}: the
    * dialog already edits the same state through the picker, and reflection -- not the layout --
    * decides which properties are settable ({@code TabularSchemaExtractor}: "properties the
    * {@code @View} annotation never mentions are still settable").
    *
    * <p>Reads and writes the {@code spreadsheet} FIELD, never {@link #getSpreadsheet()}, which
    * refreshes the OAuth token and throws when the data source is null.
    *
    * <p>Stored verbatim: no trim, no case change, no validation, so reading this property back
    * returns exactly the string that was written -- {@code applyQueryContract} writes a property
    * and reads it back to confirm the write, and normalizing here would be reported as a write
    * that silently had no effect.
    */
   @Property(label = "Spreadsheet ID")
   public String getSpreadsheetId() {
      return spreadsheet == null || spreadsheet.getSelectedFile() == null
         ? null : spreadsheet.getSelectedFile().getId();
   }

   public void setSpreadsheetId(String spreadsheetId) {
      if(spreadsheet == null) {
         spreadsheet = new GooglePicker();
      }

      if(spreadsheet.getSelectedFile() == null) {
         spreadsheet.setSelectedFile(new GoogleFile());
      }

      spreadsheet.getSelectedFile().setId(spreadsheetId);
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

