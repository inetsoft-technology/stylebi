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
package inetsoft.uql.rest.datasource.quickbooks;

import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81964: a QuickBooks Reports data source that has not been authorized yet has no company
 * id. It was saved as the text "null" and loaded back as the string "null", so the URL ended
 * in /company/null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, CredentialTestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class QuickbooksReportsDataSourceTest {
   @Test
   void dataSourceWithoutCompanyIdStaysWithoutOneAfterSaving() throws Exception {
      final QuickbooksReportsDataSource copy = roundTrip(new QuickbooksReportsDataSource());

      assertNull(copy.getCompanyId());
   }

   @Test
   void companyIdIsKeptAfterSaving() throws Exception {
      final QuickbooksReportsDataSource dataSource = new QuickbooksReportsDataSource();
      dataSource.setCompanyId("9130350000000001");
      dataSource.setProduction(true);

      final QuickbooksReportsDataSource copy = roundTrip(dataSource);

      assertEquals("9130350000000001", copy.getCompanyId());
      assertTrue(copy.isProduction());
   }

   private static QuickbooksReportsDataSource roundTrip(QuickbooksReportsDataSource dataSource)
      throws Exception
   {
      final StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         dataSource.writeXML(writer);
      }

      final Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      final QuickbooksReportsDataSource copy = new QuickbooksReportsDataSource();
      copy.parseXML(root);
      return copy;
   }
}
