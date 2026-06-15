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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.erm.AttributeRef;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractChartInfoTextFieldsTest {

   @Test
   void textLayoutFieldsEnterAestheticRefsWhenResolved() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSAestheticRef ref = new VSAestheticRef();
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, "state"));
      ref.setDataRef(dim);
      ref.setRTDataRef(dim);            // simulate resolved
      info.addTextLayoutField(ref);

      java.util.List<AestheticRef> refs = info.getAestheticRefs(info);
      assertTrue(refs.stream().anyMatch(a -> a == ref),
         "resolved textLayoutField must appear in aesthetic refs");
   }

   @Test
   void textLayoutFieldsRoundTripThroughXml() throws Exception {
      VSChartInfo info = new DefaultVSChartInfo();
      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(new VSChartDimensionRef(new AttributeRef(null, "state")));
      info.addTextLayoutField(ref);

      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      info.writeXML(pw);
      pw.flush();

      VSChartInfo parsed = new DefaultVSChartInfo();
      parsed.parseXML(Tool.parseXML(new StringReader(sw.toString())).getDocumentElement());
      assertEquals(1, parsed.getTextLayoutFieldCount());
   }
}
