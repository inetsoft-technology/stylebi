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
package inetsoft.uql.rest.json;

import inetsoft.test.*;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DeprecatedPaginationTypeTest {
   @Test
   void savedTwitterIterationSpecStillParses() throws Exception {
      final String xml = "<pagination><type>TWITTER_ITERATION</type></pagination>";
      final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      final PaginationSpec spec = new PaginationSpec();
      spec.parseXML((Element) doc.getDocumentElement());
      assertEquals(PaginationType.TWITTER_ITERATION, spec.getType());
   }

   @Test
   void factoryGivesClearErrorForTwitterIteration() {
      final RestJsonQuery query = new RestJsonQuery();
      final RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL("http://host");
      query.setDataSource(dataSource);
      query.getPaginationSpec().setType(PaginationType.TWITTER_ITERATION);

      final IllegalStateException e = assertThrows(IllegalStateException.class,
         () -> new JsonRestDataIteratorStrategyFactory(new JsonTransformer(), null)
            .createStrategy(query));
      assertTrue(e.getMessage().contains("TWITTER_ITERATION is no longer supported"));
   }
}
