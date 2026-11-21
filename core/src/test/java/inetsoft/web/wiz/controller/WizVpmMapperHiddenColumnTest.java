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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.web.portal.model.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finding h of the design: VPM hidden columns are polymorphic {@code DataRefModel}s and the
 * controller does not show how the Portal builds them. The fixture
 * {@code vpm-hidden-columns-portal.json} is the {@code HiddenColumnsModel} the Portal's hidden
 * column picker sends (shape of {@code vpm-hidden-columns.component.ts:275-300}: an
 * {@code AttributeRef} per column, {@code entity} the table). The mapper must store the same
 * {@code entity}/{@code attribute} pairs the Portal path stores, read them back as
 * {@code {table, column}}, and treat anything that is not a plain table.column reference as
 * {@code unsupported}: refused when an update would change or drop it, written back untouched when
 * it is left alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizVpmTestSupport.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class WizVpmMapperHiddenColumnTest {
   @Test
   void theMapperStoresTheSameRefsAsThePortalPath() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      HiddenColumnsModel portalHidden;

      try(InputStream in = getClass().getResourceAsStream("vpm-hidden-columns-portal.json")) {
         portalHidden = new ObjectMapper().readValue(in, HiddenColumnsModel.class);
      }

      VPMDefinition viaPortal = new VPMDefinition();
      viaPortal.setName("ViaPortal");
      viaPortal.setHidden(portalHidden);
      support.service.create(support.ds, viaPortal, support.bob);

      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("name", "ViaMapper");
      wire.put("hidden", hidden(List.of("Analyst", "Auditor"),
                                column("ORDERS", "COST"), column("ORDERS", "UNIT_PRICE"),
                                column(null, "SALARY")));
      support.service.create(support.ds, support.mapper.toDefinition(wire, support.ds, null),
                             support.bob);

      HiddenColumns portal = support.service.getStored(support.ds, "ViaPortal", support.bob)
         .getHiddenColumns();
      HiddenColumns mapped = support.service.getStored(support.ds, "ViaMapper", support.bob)
         .getHiddenColumns();

      assertEquals(pairs(portal), pairs(mapped));
      assertEquals(List.of("ORDERS.COST", "ORDERS.UNIT_PRICE", ".SALARY"), pairs(mapped));
      assertEquals(Collections.list(portal.getRoles()), Collections.list(mapped.getRoles()));
      assertTrue(Collections.list(mapped.getHiddenColumns()).stream()
                    .allMatch(ref -> ref.getClass() == AttributeRef.class));
   }

   @Test
   void aStoredPortalHiddenListReadsBackAsTableAndColumn() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      HiddenColumnsModel portalHidden;

      try(InputStream in = getClass().getResourceAsStream("vpm-hidden-columns-portal.json")) {
         portalHidden = new ObjectMapper().readValue(in, HiddenColumnsModel.class);
      }

      VPMDefinition viaPortal = new VPMDefinition();
      viaPortal.setName("ViaPortal");
      viaPortal.setHidden(portalHidden);
      support.service.create(support.ds, viaPortal, support.bob);

      Map<String, Object> wire = support.mapper.toWire(
         support.service.getStored(support.ds, "ViaPortal", support.bob), support.jdbc);

      assertEquals(hidden(List.of("Analyst", "Auditor"), column("ORDERS", "COST"),
                          column("ORDERS", "UNIT_PRICE"), column(null, "SALARY")),
                   wire.get("hidden"));
   }

   @Test
   void rolesScriptAndAnEmptyHiddenObject() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      Map<String, Object> hidden = hidden(List.of("R1"), column("T", "C"));
      hidden.put("script", "hide(\"x\");");
      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("name", "WithScript");
      wire.put("hidden", hidden);
      support.service.create(support.ds, support.mapper.toDefinition(wire, support.ds, null),
                             support.bob);
      VirtualPrivateModel stored = support.service.getStored(support.ds, "WithScript", support.bob);

      assertEquals("hide(\"x\");", stored.getHiddenColumns().getScript());
      assertEquals(support.mapper.normalize(wire),
                   support.mapper.normalize(support.mapper.toWire(stored, support.jdbc)));

      // an empty hidden object is the same as none
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("name", "Empty");
      empty.put("hidden", hidden(List.of()));
      support.service.create(support.ds, support.mapper.toDefinition(empty, support.ds, null),
                             support.bob);
      assertNull(support.mapper.toWire(
         support.service.getStored(support.ds, "Empty", support.bob), support.jdbc).get("hidden"));
   }

   @Test
   void anItemThatIsNotAPlainColumnIsUnsupportedAndKeptOrRefused() throws Exception {
      WizVpmTestSupport support = new WizVpmTestSupport();
      VirtualPrivateModel vpm = new VirtualPrivateModel("Odd");
      HiddenColumns hc = new HiddenColumns();
      hc.addHiddenColumn(new AttributeRef("ORDERS", "COST"));
      ExpressionRef expression = new ExpressionRef(null, "calc");
      expression.setExpression("ORDERS.A + ORDERS.B");
      hc.addHiddenColumn(expression);
      vpm.setHiddenColumns(hc);
      XDataModel dataModel = support.fresh();
      dataModel.addVirtualPrivateModel(vpm, true);

      VirtualPrivateModel stored = support.service.getStored(support.ds, "Odd", support.bob);
      Map<String, Object> wire = support.mapper.toWire(stored, support.jdbc);
      List<Object> columns = WizVpmMapper.list(WizVpmMapper.map(wire.get("hidden")).get("columns"));
      assertEquals(2, columns.size());
      assertEquals(column("ORDERS", "COST"), columns.get(0));
      Map<String, Object> odd = WizVpmMapper.map(columns.get(1));
      assertEquals(true, odd.get("unsupported"));
      assertNull(odd.get("table"));
      assertEquals("calc", odd.get("column"));

      WizVpmValidator validator = new WizVpmValidator(null);

      // fed back unchanged: valid, and written back untouched
      assertTrue(validator.validate(support.fresh(), wire, WizVpmValidator.Mode.UPDATE, stored,
                                    null).errors().isEmpty());
      VPMDefinition rebuilt = support.mapper.toDefinition(wire, support.ds, stored);
      VirtualPrivateModel again = support.service.fromModel(rebuilt);
      List<DataRef> refs = Collections.list(again.getHiddenColumns().getHiddenColumns());
      assertEquals(2, refs.size());
      assertTrue(refs.get(1) instanceof ExpressionRef);
      assertEquals("ORDERS.A + ORDERS.B", ((ExpressionRef) refs.get(1)).getExpression());

      // dropping it, by omitting the entry or clearing hidden, is refused by name
      Map<String, Object> dropped = new LinkedHashMap<>(wire);
      dropped.put("hidden", hidden(List.of(), column("ORDERS", "COST")));
      assertEquals("HIDDEN_COLUMN_INVALID", firstCode(validator.validate(
         support.fresh(), dropped, WizVpmValidator.Mode.UPDATE, stored, null)));
      Map<String, Object> cleared = new LinkedHashMap<>(wire);
      cleared.put("hidden", null);
      assertEquals("HIDDEN_COLUMN_INVALID", firstCode(validator.validate(
         support.fresh(), cleared, WizVpmValidator.Mode.UPDATE, stored, null)));

      // inventing an unsupported entry the stored model does not have is refused too
      Map<String, Object> invented = new LinkedHashMap<>(wire);
      Map<String, Object> fake = column(null, "ghost");
      fake.put("unsupported", true);
      invented.put("hidden", hidden(List.of(), column("ORDERS", "COST"), fake, odd));
      assertEquals("HIDDEN_COLUMN_INVALID", firstCode(validator.validate(
         support.fresh(), invented, WizVpmValidator.Mode.UPDATE, stored, null)));
   }

   private static String firstCode(WizVpmValidator.Result result) {
      assertFalse(result.errors().isEmpty());
      return result.errors().get(0).code();
   }

   private static List<String> pairs(HiddenColumns hidden) {
      List<String> pairs = new ArrayList<>();

      for(DataRef ref : Collections.list(hidden.getHiddenColumns())) {
         AttributeRef attribute = (AttributeRef) ref;
         pairs.add((attribute.getEntity() == null ? "" : attribute.getEntity()) + "." +
                   attribute.getAttribute());
      }

      return pairs;
   }

   private static Map<String, Object> column(String table, String column) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("table", table);
      map.put("column", column);
      return map;
   }

   @SafeVarargs
   private static Map<String, Object> hidden(List<String> roles, Map<String, Object>... columns) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("roles", new ArrayList<>(roles));
      map.put("columns", new ArrayList<>(Arrays.asList(columns)));
      return map;
   }
}
