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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The wire shape of CreateViewsheetResult.assemblyState (turn-provenance spec 3.3.1). */
@Tag("core")
class CreateViewsheetResultAssemblyStateTest {
   private final ObjectMapper mapper = new ObjectMapper();

   @Test
   void neitherAssemblyStateKeyIsPresentWhenTheEndpointNeverFilledIt() {
      JsonNode json = mapper.valueToTree(new CreateViewsheetResult());

      assertFalse(json.has("assemblyState"));
      assertFalse(json.has("assemblyStateUnavailable"));
   }

   @Test
   void aFailedFillSerializesTheUnavailableFlagWithoutAnAssemblyState() {
      CreateViewsheetResult result = new CreateViewsheetResult();
      result.setAssemblyStateUnavailable(Boolean.TRUE);

      JsonNode json = mapper.valueToTree(result);

      assertFalse(json.has("assemblyState"));
      assertTrue(json.get("assemblyStateUnavailable").asBoolean());
   }

   @Test
   void assemblyStateSerializesEverySection() {
      WorksheetStructure.ConditionLeaf leaf = new WorksheetStructure.ConditionLeaf();
      leaf.setField("Region");
      leaf.setOperation("EQUAL_TO");
      leaf.setValues(List.of("East"));
      leaf.setPhase("chart");
      leaf.setJunction("AND");
      leaf.setJunctionLevel(1);

      CreateViewsheetResult.AssemblyState state = new CreateViewsheetResult.AssemblyState();
      state.setSource(new CreateViewsheetResult.AssemblySource("worksheet", "1^2^__NULL__^ws1", "Orders_2"));
      state.setConditions(List.of(leaf));
      state.setDateComparison(Map.of("enabled", true));
      state.setHighlights(List.of(new CreateViewsheetResult.AssemblyHighlight(
         "big", "Sum(Sales)", List.of("Sales GREATER_THAN 100"))));
      state.setTableColumns(new CreateViewsheetResult.TableColumns(List.of("Region")));

      CreateViewsheetResult result = new CreateViewsheetResult();
      result.setAssemblyState(state);
      JsonNode node = mapper.valueToTree(result).get("assemblyState");

      assertEquals("worksheet", node.at("/source/kind").asText());
      assertEquals("1^2^__NULL__^ws1", node.at("/source/worksheet").asText());
      assertEquals("Orders_2", node.at("/source/table").asText());
      assertEquals("Region", node.at("/conditions/0/field").asText());
      assertEquals("chart", node.at("/conditions/0/phase").asText());
      assertEquals(1, node.at("/conditions/0/junctionLevel").asInt());
      assertTrue(node.at("/dateComparison/enabled").asBoolean());
      assertEquals("big", node.at("/highlights/0/name").asText());
      assertEquals("Sum(Sales)", node.at("/highlights/0/target").asText());
      assertEquals("Sales GREATER_THAN 100", node.at("/highlights/0/clauses/0").asText());
      assertEquals("Region", node.at("/tableColumns/columns/0").asText());
   }

   @Test
   void absentSectionsAreOmittedAndEmptyListsKept() {
      CreateViewsheetResult.AssemblyState state = new CreateViewsheetResult.AssemblyState();
      state.setSource(new CreateViewsheetResult.AssemblySource("physical", null, "T"));

      CreateViewsheetResult result = new CreateViewsheetResult();
      result.setAssemblyState(state);
      JsonNode node = mapper.valueToTree(result).get("assemblyState");

      assertFalse(node.has("dateComparison"));
      assertFalse(node.has("tableColumns"));
      assertFalse(node.get("source").has("worksheet"));
      assertTrue(node.get("conditions").isArray());
      assertEquals(0, node.get("conditions").size());
      assertTrue(node.get("highlights").isArray());
      assertEquals(0, node.get("highlights").size());
   }
}
