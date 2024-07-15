/*
 * inetsoft-googledoc - StyleBI is a business intelligence web application.
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
package inetsoft.uql.gdata;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;

@SuppressWarnings("unused")
@View(vertical = true, value = {
   @View1("spreadsheetId"),
   @View1("worksheetId"),
   @View1("firstRowAsHeader")
})
public class GDataQuery extends TabularQuery {
   public GDataQuery() {
      super(GDataDataSource.TYPE);
   }

   @Property(label = "Spreadsheet")
   @PropertyEditor(tagsMethod = "getSpreadsheets")
   public String getSpreadsheetId() {
      return spreadsheetId;
   }

   public void setSpreadsheetId(String spreadsheetId) {
      this.spreadsheetId = spreadsheetId;
   }

   @Property(label = "Worksheet")
   @PropertyEditor(tagsMethod = "getWorksheets", dependsOn = { "spreadsheetId" })
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

      if(spreadsheetId != null) {
         writer.format("<spreadsheetId><![CDATA[%s]]></spreadsheetId>%n", spreadsheetId);
      }

      if(worksheetId != null) {
         writer.format("<worksheetId><![CDATA[%s]]></worksheetId>%n", worksheetId);
      }

      writer.format("<largeSpreadsheetCount>%s</largeSpreadsheetCount>%n", largeSpreadsheetCount);
      writer.format("<firstRowAsHeader>%s</firstRowAsHeader>%n", firstRowAsHeader);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      Element element;

      if((element = Tool.getChildNodeByTagName(root, "spreadsheetId")) != null) {
         spreadsheetId = Tool.getValue(element);
      }

      if((element = Tool.getChildNodeByTagName(root, "worksheetId")) != null) {
         worksheetId = Tool.getValue(element);
      }

      if((element = Tool.getChildNodeByTagName(root, "largeSpreadsheetCount")) != null) {
         String value = Tool.getValue(element);

         if(value == null) {
            largeSpreadsheetCount = false;
         }
         else {
            largeSpreadsheetCount = Boolean.parseBoolean(value);
         }
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

   public String[][] getSpreadsheets() {
      if(!largeSpreadsheetCount) {
         try {
            String[][] spreadsheets = GDataRuntime.listSpreadsheets((GDataDataSource) getDataSource());

            if(spreadsheets.length > 3500) {
               largeSpreadsheetCount = true;
            }
            else {
               return spreadsheets;
            }
         }
         catch(IOException e) {
            LOG.error("Failed to list spreadsheets", e);
         }
      }

      return new String[0][];
   }

   public String[][] getWorksheets() {
      if(spreadsheetId != null) {
         try {
            return GDataRuntime.listWorksheets((GDataDataSource) getDataSource(), spreadsheetId);
         }
         catch(IOException e) {
            LOG.error("Failed to list worksheets", e);
         }
      }

      return new String[0][];
   }

   private String spreadsheetId;
   private String worksheetId;
   private boolean largeSpreadsheetCount = false;
   private boolean firstRowAsHeader = true;

   private static final Logger LOG = LoggerFactory.getLogger(GDataQuery.class.getName());
}

