/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.sree.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for Bug #78431: a scheduled CSV delimiter field left blank is sent as "",
 * stored in the task XML as delimiter="", and must fall back to a comma instead of producing
 * a CSV with no separator.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CSVBlankDelimiter78431Test {
   @Test
   void blankDelimiterFromScheduleFallsBackToComma() throws Exception {
      CSVConfig config =
         storedConfig("{\"delimiter\":\"\",\"quote\":\"\\\"\",\"keepHeader\":true}");
      assertEquals("",config.getDelimiter(), "the stored task keeps the blank delimiter");

      CSVVSExporter exporter = new CSVVSExporter(new ByteArrayOutputStream(), config);
      assertEquals(",", exporter.getDelim());
      assertEquals("\"Team\",\"Sales Rep\",\"Retn Amt\"", firstLine(exporter));
   }

   @Test
   void explicitAndTabDelimitersAreKept() throws Exception {
      CSVConfig pipe = storedConfig("{\"delimiter\":\"|\",\"keepHeader\":true}");
      assertEquals("|", new CSVVSExporter(new ByteArrayOutputStream(), pipe).getDelim());

      CSVConfig tab =
         storedConfig("{\"delimiter\":\"\",\"keepHeader\":true,\"tabDelimited\":true}");
      assertEquals("\t", new CSVVSExporter(new ByteArrayOutputStream(), tab).getDelim());
   }

   /**
    * Builds the CSV config the way a scheduled task does: the request JSON the schedule pane
    * sends, the model-to-config copy, then the task's XML write and parse.
    */
   private static CSVConfig storedConfig(String json) throws Exception {
      CSVConfigModel model = new ObjectMapper().readValue(json, CSVConfigModel.class);
      EmailInfo info = new EmailInfo();
      info.setFileFormat("CSV");
      info.setCSVConfig(new CSVConfig(model));

      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      info.writeXML(writer);
      writer.flush();

      Element elem = Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement();
      EmailInfo parsed = new EmailInfo();
      parsed.parseXML(elem);
      return parsed.getCSVConfig();
   }

   private static String firstLine(CSVVSExporter exporter) throws Exception {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "Team", "Sales Rep", "Retn Amt" },
         { "East", "Annie", 10 }
      });
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVUtil.writeTableDataAssembly(table, out, exporter.getDelim(), exporter.getQuote(),
                                     exporter.isKeepHeader());
      return out.toString().split("\\R", 2)[0];
   }
}
