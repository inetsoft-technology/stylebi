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
package inetsoft.web.wiz.controller;

import inetsoft.test.*;
import inetsoft.uql.service.DataSourceRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finding i of the design, decided: a subquery operand needs a {@code SQLQueryDialogModel}, and the
 * Portal's own write parses its SQL with {@code UniformSQL.setParseSQL} and {@code sql.wait()}.
 * The simple {@code sqlEdited:true} path works from the bridge against a real JDBC data source and
 * the SQL reads back verbatim (multi-line, quoted identifiers, a trailing semicolon, a variable
 * reference, Chinese text), so subquery operands stay in phase 1. SQL the parser cannot read is
 * refused by field before the write.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizVpmTestSupport.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class WizVpmSubqueryTest {
   @Test
   void subqueryOperandsRoundTripAndUnparsableSqlIsRefused() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      support.jdbc.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      support.jdbc.setURL("jdbc:derby:memory:" + support.ds + ";create=true");
      DataSourceRegistry.getRegistry().setDataSource(support.jdbc, true);
      WizVpmController controller = support.controller();
      String[] sqls = {
         "select 1 from ORDERS where ORDERS.REGION = 'East'",
         "SELECT ORDERS.ID\n  FROM ORDERS\n WHERE ORDERS.AMT > 10",
         "select ID from ORDERS;",
         "select \"ID\" from \"ORDERS\" where \"REGION\" in ('East','West')",
         "select count(*) from ORDERS o, CUSTOMERS c where o.CID = c.ID and c.NAME like '%\u533a%'",
         "this is not sql",
         "select 1 from ORDERS where ORDERS.REGION = $(region)" };

      for(int n = 0; n < sqls.length; n++) {
         Map<String, Object> left = new LinkedHashMap<>();
         left.put("type", "subquery");
         left.put("sql", sqls[n]);
         Map<String, Object> clause = new LinkedHashMap<>();
         clause.put("left", left);
         clause.put("op", "EXISTS");
         Map<String, Object> condition = new LinkedHashMap<>();
         condition.put("name", "c");
         condition.put("target", Map.of("kind", "table", "name", "ORDERS"));
         condition.put("tree", clause);
         Map<String, Object> definition = new LinkedHashMap<>();
         definition.put("name", "Sub" + n);
         definition.put("conditions", List.of(condition));
         String label = "#" + n;

         Object result = assertTimeoutPreemptively(Duration.ofSeconds(60), () ->
            controller.create(new WizVpmWire.WriteRequest(
               support.ds, null, definition, false), support.bob));

         if(n == 5) {
            WizDataModelWire.Failure failure = assertInstanceOf(
               WizDataModelWire.Failure.class, result, label);
            assertEquals("INVALID", failure.reason());
            assertEquals("conditions[c].tree.left.sql", failure.field());
            assertNull(support.fresh().getVirtualPrivateModel("Sub" + n));
         }
         else {
            WizVpmWire.WriteResponse response = assertInstanceOf(
               WizVpmWire.WriteResponse.class, result, label);
            Map<String, Object> back = WizVpmMapper.map(WizVpmMapper.map(
               WizVpmMapper.list(response.definition().get("conditions")).get(0)).get("tree"));
            assertEquals(sqls[n], WizVpmMapper.map(back.get("left")).get("sql"), label);
         }
      }
   }
}
