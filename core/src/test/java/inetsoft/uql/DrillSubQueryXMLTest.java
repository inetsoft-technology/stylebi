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
package inetsoft.uql;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78061: the auto-drill sub-query parameter value (the field a worksheet parameter is mapped
 * to) must be escaped when the logical model is written, or a field name containing an XML
 * special character makes the stored model unreadable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DrillSubQueryXMLTest {
   @ParameterizedTest
   @ValueSource(strings = { "Region", "Customer:R&D", "a<b", "a>b", "say \"hi\"", "it's", "&amp;" })
   void parameterValueRoundTripsThroughXML(String value) throws Exception {
      DrillSubQuery query = new DrillSubQuery();
      query.setQuery("q1");
      query.setParameter("start&Counter", value);

      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         query.writeXML(writer);
      }

      DrillSubQuery reloaded = new DrillSubQuery();
      reloaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());

      assertEquals(value, reloaded.getParameter("start&Counter"));
   }
}
