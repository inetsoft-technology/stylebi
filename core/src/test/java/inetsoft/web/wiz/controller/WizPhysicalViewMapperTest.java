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
package inetsoft.web.wiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wire form of a physical view: every wire field is carried both ways, every unknown key is
 * named, and nothing that is server state (runtime id, bounds, columns, path, auto aliases) is in
 * the wire form.
 */
@Tag("core")
class WizPhysicalViewMapperTest {
   private static Map<String, Object> full() {
      Map<String, Object> definition = new LinkedHashMap<>();
      definition.put("name", "sales");
      definition.put("description", "the sales view");
      definition.put("folder", "F");
      definition.put("connection", "conn");
      definition.put("parent", "base");
      definition.put("tables", new ArrayList<>(List.of(
         new LinkedHashMap<>(Map.of("name", "ORDERS", "schema", "dbo", "catalog", "main")),
         new LinkedHashMap<>(Map.of("name", "O2", "aliasOf", "ORDERS", "oldName", "O1")),
         new LinkedHashMap<>(Map.of("name", "V", "sql", "select 1")))));
      Map<String, Object> join = new LinkedHashMap<>();
      join.put("from", new LinkedHashMap<>(Map.of("table", "O2", "column", "CUST")));
      join.put("to", new LinkedHashMap<>(Map.of("table", "V", "column", "ID")));
      join.put("type", "LEFT_OUTER");
      join.put("mergingRule", "or");
      join.put("weak", true);
      join.put("priority", 3);
      join.put("cardinality", "ONE_TO_MANY");
      definition.put("joins", new ArrayList<>(List.of(join)));
      return definition;
   }

   @Test
   void everyWireFieldIsCarried() {
      Desired desired = WizPhysicalViewMapper.parse(full());
      View view = desired.view();

      assertEquals("sales", view.name());
      assertEquals("the sales view", view.description());
      assertEquals("F", view.folder());
      assertEquals("conn", view.connection());
      assertEquals("base", view.parent());
      assertTrue(desired.hasDescription() && desired.hasFolder() && desired.hasConnection() &&
                    desired.hasTables() && desired.hasJoins());
      TableDef plain = view.tables().get(0);
      assertEquals(Kind.PLAIN, plain.kind());
      assertEquals("dbo", plain.schema());
      assertEquals("main", plain.catalog());
      TableDef alias = view.tables().get(1);
      assertEquals(Kind.ALIAS, alias.kind());
      assertEquals("ORDERS", alias.aliasOf());
      assertEquals("O1", alias.oldName());
      TableDef inline = view.tables().get(2);
      assertEquals(Kind.VIEW, inline.kind());
      assertEquals("select 1", inline.sql());
      JoinDef join = view.joins().get(0);
      assertEquals("O2", join.fromTable());
      assertEquals("CUST", join.fromColumn());
      assertEquals("V", join.toTable());
      assertEquals("ID", join.toColumn());
      assertEquals("LEFT_OUTER", join.type());
      assertEquals("or", join.mergingRule());
      assertEquals(Boolean.TRUE, join.weak());
      assertEquals(3, join.priority());
      assertEquals("ONE_TO_MANY", join.cardinality());

      // and back out, in the wire form
      Map<String, Object> wire = WizPhysicalViewMapper.toWire(view);
      assertEquals("sales", wire.get("name"));
      assertEquals("conn", wire.get("connection"));
      Map<?, ?> wireJoin = (Map<?, ?>) ((List<?>) wire.get("joins")).get(0);
      assertEquals("LEFT_OUTER", wireJoin.get("type"));
      assertEquals(3, wireJoin.get("priority"));
      assertEquals("ONE_TO_MANY", wireJoin.get("cardinality"));
   }

   @Test
   void unknownKeysAreNamedAtEveryLevel() {
      Map<String, Object> definition = full();
      definition.put("colour", "red");
      ((Map<String, Object>) ((List<?>) definition.get("tables")).get(0)).put("alis", "x");
      Map<String, Object> join = (Map<String, Object>) ((List<?>) definition.get("joins")).get(0);
      join.put("kind", "EQUAL");
      ((Map<String, Object>) join.get("from")).put("col", "x");

      DmFailure failure = assertThrows(DmFailure.class,
                                       () -> WizPhysicalViewMapper.parse(definition));
      List<String> fields = failure.toBody().errors().stream()
         .map(WizDataModelWire.Err::field).toList();

      assertTrue(fields.contains("definition.colour"), fields.toString());
      assertTrue(fields.contains("tables[ORDERS].alis"), fields.toString());
      assertTrue(fields.contains("joins[0].kind"), fields.toString());
      assertTrue(fields.contains("joins[0].from.col"), fields.toString());
      assertEquals("UNKNOWN_KEY", failure.toBody().errors().get(0).code());
   }

   @Test
   void theReadOnlyKeysOfAGetResultAreAcceptedAndIgnored() {
      Map<String, Object> definition = full();
      definition.put("readOnly", false);
      definition.put("extended", true);
      definition.put("warnings", List.of());
      ((Map<String, Object>) ((List<?>) definition.get("tables")).get(0))
         .put("baseElement", true);
      ((Map<String, Object>) ((List<?>) definition.get("joins")).get(0))
         .put("baseElement", true);

      Desired desired = WizPhysicalViewMapper.parse(definition);

      assertTrue(desired.view().tables().get(0).baseElement());
      assertTrue(desired.view().joins().get(0).baseElement());
   }

   @Test
   void enumsAreCanonicalizedAndABadOneIsNamed() {
      Map<String, Object> definition = full();
      Map<String, Object> join = (Map<String, Object>) ((List<?>) definition.get("joins")).get(0);
      join.put("type", "left_outer");
      join.put("mergingRule", "AND");
      join.put("cardinality", "many_to_one");
      JoinDef parsed = WizPhysicalViewMapper.parse(definition).view().joins().get(0);

      assertEquals("LEFT_OUTER", parsed.type());
      assertEquals("and", parsed.mergingRule());
      assertEquals("MANY_TO_ONE", parsed.cardinality());

      join.put("type", "LEFTISH");
      DmFailure failure = assertThrows(DmFailure.class,
                                       () -> WizPhysicalViewMapper.parse(definition));
      assertEquals("joins[0].type", failure.toBody().errors().get(0).field());
      assertTrue(failure.getMessage().contains("EQUAL"));
   }

   @Test
   void aTableIsExactlyOneKind() {
      Map<String, Object> definition = new LinkedHashMap<>();
      definition.put("name", "v");
      definition.put("tables", new ArrayList<>(List.of(
         new LinkedHashMap<>(Map.of("name", "A", "aliasOf", "T", "sql", "select 1")),
         new LinkedHashMap<>(Map.of("name", "B", "sql", "select 1", "schema", "s")),
         new LinkedHashMap<>(Map.of("name", "C", "oldName", "D")),
         new LinkedHashMap<>(Map.of("name", "E", "sql", "  ")))));
      DmFailure failure = assertThrows(DmFailure.class,
                                       () -> WizPhysicalViewMapper.parse(definition));
      List<String> fields = failure.toBody().errors().stream()
         .map(WizDataModelWire.Err::field).toList();

      assertTrue(fields.contains("tables[A]"), fields.toString());
      assertTrue(fields.contains("tables[B]"), fields.toString());
      assertTrue(fields.contains("tables[C].oldName"), fields.toString());
      assertTrue(fields.contains("tables[E].sql"), fields.toString());
   }

   @Test
   void wrongTypesAreNamedNotDropped() {
      Map<String, Object> definition = new LinkedHashMap<>();
      definition.put("name", 5);
      definition.put("tables", "T1");
      definition.put("joins", new ArrayList<>(List.of("x")));
      DmFailure failure = assertThrows(DmFailure.class,
                                       () -> WizPhysicalViewMapper.parse(definition));
      List<String> fields = failure.toBody().errors().stream()
         .map(WizDataModelWire.Err::field).toList();

      assertTrue(fields.contains("definition.name"), fields.toString());
      assertTrue(fields.contains("definition.tables"), fields.toString());
      assertTrue(fields.contains("joins[0]"), fields.toString());
   }

   @Test
   void aJoinIsTheSameJoinInEitherDirection() {
      JoinDef a = new JoinDef("T2", "X", "T1", "Y", "EQUAL", "and", false, 0, null, false);
      JoinDef b = new JoinDef("T1", "Y", "T2", "X", "EQUAL", "and", false, 0, null, false);

      assertEquals(a.key(), b.key());
   }

   @Test
   void theWireFormCarriesNoServerState() throws Exception {
      PhysicalModelDefinition dto = new PhysicalModelDefinition();
      dto.setId("RUNTIMEID0123456789abcdef0123456789");
      dto.setName("v");
      dto.setFolder("F");
      PhysicalTableModel plain = new PhysicalTableModel();
      plain.setName("T1");
      plain.setQualifiedName("T1");
      plain.setType(TableType.PHYSICAL);
      plain.setPath("ds/TABLE/T1");
      plain.setBounds(new Rectangle(1, 2, 3, 4));
      plain.setCols(new ArrayList<>(List.of(new GraphColumnInfo())));
      plain.setAutoAliases(new ArrayList<>());
      plain.setJoins(new ArrayList<>());
      JoinModel join = new JoinModel();
      join.setTable("T1");
      join.setColumn("A");
      join.setForeignTable("T2");
      join.setForeignColumn("B");
      join.setType(JoinType.EQUAL);
      join.setMergingRule(MergingRule.AND);
      join.setCardinality(JoinCardinality.MANY_TO_ONE);
      join.setRelationship(new inetsoft.uql.erm.XRelationship());
      plain.getJoins().add(join);
      PhysicalTableModel other = new PhysicalTableModel();
      other.setName("T2");
      other.setQualifiedName("T2");
      other.setType(TableType.PHYSICAL);
      other.setJoins(new ArrayList<>());
      other.setAutoAliases(new ArrayList<>());
      dto.setTables(new ArrayList<>(List.of(plain, other)));

      View view = WizPhysicalViewMapper.fromDto(dto, new XPartition("v"), null);
      String json = new ObjectMapper().writeValueAsString(WizPhysicalViewMapper.toWire(view));

      assertFalse(json.contains("RUNTIMEID"), json);
      assertFalse(json.contains("\"id\""), json);
      assertFalse(json.contains("bounds"), json);
      assertFalse(json.contains("cols"), json);
      assertFalse(json.contains("path"), json);
      assertFalse(json.contains("autoAlias"), json);
      assertFalse(json.contains("relationship"), json);
      assertTrue(json.contains("\"MANY_TO_ONE\""), json);
      assertEquals(2, view.tables().size());
      assertEquals(1, view.joins().size());
   }

   @Test
   void aLegacyJoinWithoutACardinalityIsReadWithoutOne() {
      PhysicalModelDefinition dto = new PhysicalModelDefinition();
      dto.setName("v");
      PhysicalTableModel table = new PhysicalTableModel();
      table.setName("T1");
      table.setQualifiedName("T1");
      table.setType(TableType.PHYSICAL);
      table.setAutoAliases(new ArrayList<>());
      JoinModel join = new JoinModel();
      join.setTable("T1");
      join.setColumn("A");
      join.setForeignTable("T2");
      join.setForeignColumn("B");
      join.setType(JoinType.EQUAL);
      join.setMergingRule(MergingRule.AND);
      table.setJoins(new ArrayList<>(List.of(join)));
      dto.setTables(new ArrayList<>(List.of(table)));

      View view = WizPhysicalViewMapper.fromDto(dto, null, null);
      Map<?, ?> wireJoin = (Map<?, ?>) ((List<?>) WizPhysicalViewMapper.toWire(view)
         .get("joins")).get(0);

      assertFalse(wireJoin.containsKey("cardinality"));
   }
}
