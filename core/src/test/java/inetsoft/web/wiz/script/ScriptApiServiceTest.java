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
package inetsoft.web.wiz.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.web.wiz.script.model.FunctionSignature;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptApiServiceTest {
   private final ScriptApiService service = new ScriptApiService();

   @Test
   void looksUpTopLevelGlobalFromHandAuthoredCorpus() {
      FunctionSignature runQuery = service.lookup("runQuery");
      assertTrue(runQuery.found());
      assertNotNull(runQuery.type());
      assertNotNull(runQuery.url());

      FunctionSignature alert = service.lookup("alert");
      assertTrue(alert.found());
      assertNotNull(alert.type());

      FunctionSignature setCellValue = service.lookup("setCellValue");
      assertTrue(setCellValue.found());
      assertNotNull(setCellValue.type());
   }

   @Test
   void looksUpPrototypeMethodFromGeneratedCorpus() {
      FunctionSignature signature = service.lookup("AreaElement.addDim");
      assertTrue(signature.found());
      assertNotNull(signature.type());
   }

   @Test
   void returnsNotFoundForUnknownName() {
      FunctionSignature signature = service.lookup("doesNotExist");
      assertFalse(signature.found());
      assertNull(signature.type());
      assertNull(signature.url());
   }

   @Test
   void looksUpCalcFunctionUnqualified() {
      // CALC.day/eomonth are nested under "CALC" in js-functions.json but are callable bare in a
      // viewsheet script, same as VSScriptableService.createStaticDefinitions resolves them for
      // the script editor's own autocomplete.
      FunctionSignature day = service.lookup("day");
      assertTrue(day.found());
      assertNotNull(day.type());

      FunctionSignature eomonth = service.lookup("eomonth");
      assertTrue(eomonth.found());
      assertNotNull(eomonth.type());
   }

   @Test
   void hidesSreeOnlyReportingFunctions() {
      // showReport/reprint are Report/Sree-scoped globals the viewsheet script editor's own
      // autocomplete never offers (VSScriptableService.createStaticDefinitions strips them);
      // this lookup must agree, not just relay whatever the raw metadata file contains.
      assertFalse(service.lookup("showReport").found());
      assertFalse(service.lookup("reprint").found());
   }

   @Test
   void flagsDocumentedSampleCodeFunctions() {
      // Special Chart Functions are documented in UtilityObjects.md as sample code that must be
      // copied into a Script Library function before they're callable, not registered globals.
      assertTrue(service.lookup("createMekkoGraph").sample());
      assertTrue(service.lookup("createTreemap").sample());
      assertTrue(service.lookup("createIcicleGraph").sample());
   }

   @Test
   void doesNotFlagGenuineGlobalsAsSampleCode() {
      assertFalse(service.lookup("dateAdd").sample());
   }

   @Test
   void doesNotFlagUnknownNameAsSampleCode() {
      assertFalse(service.lookup("doesNotExist").sample());
   }

   @Test
   void missWithDifferentCaseSuggestsCanonicalBareName() {
      FunctionSignature dateAdd = service.lookup("dateadd");
      assertFalse(dateAdd.found());
      assertEquals("dateAdd", dateAdd.didYouMean());

      // countDistinct lives under CALC and is flattened into the root
      FunctionSignature countDistinct = service.lookup("countdistinct");
      assertFalse(countDistinct.found());
      assertEquals("countDistinct", countDistinct.didYouMean());
   }

   @Test
   void missWithDifferentCaseSuggestsCanonicalTypeMethod() {
      assertEquals("AreaElement.addDim", service.lookup("areaelement.adddim").didYouMean());
      assertEquals("AreaElement.addDim", service.lookup("AreaElement.adddim").didYouMean());
      assertEquals("AreaElement.addDim", service.lookup("areaelement.addDim").didYouMean());
      assertFalse(service.lookup("areaelement.adddim").found());
   }

   @Test
   void trueMissAndExactHitHaveNoSuggestion() {
      assertNull(service.lookup("doesNotExist").didYouMean());
      assertNull(service.lookup("AreaElement.noSuchMethod").didYouMean());
      assertNull(service.lookup("dateAdd").didYouMean());
      assertTrue(service.lookup("dateAdd").found());
   }

   @Test
   void sreeOnlyNamesAreNeverSuggested() {
      assertNull(service.lookup("showreport").didYouMean());
   }

   @Test
   void ambiguousCaseInsensitiveMatchesReturnSortedList() throws Exception {
      ObjectNode root = (ObjectNode) new ObjectMapper().readTree(
         "{\"fooBar\":{\"!type\":\"fn()\"},\"FOOBAR\":{\"!type\":\"fn()\"},"
         + "\"T\":{\"prototype\":{\"doIt\":{},\"DOIT\":{}}},\"!name\":{}}");
      ScriptApiService svc = new ScriptApiService(root);

      FunctionSignature bare = svc.lookup("foobar");
      assertFalse(bare.found());
      assertEquals(List.of("FOOBAR", "fooBar"), bare.didYouMean());

      assertEquals(List.of("T.DOIT", "T.doIt"), svc.lookup("t.doit").didYouMean());
      assertNull(svc.lookup("!NAME").didYouMean());
   }

   @Test
   void didYouMeanSerializationShapes() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode single = mapper.valueToTree(service.lookup("dateadd"));
      assertEquals("dateAdd", single.get("didYouMean").asText());

      JsonNode list = mapper.valueToTree(
         new FunctionSignature("x", false, null, null, false, List.of("A", "b")));
      assertTrue(list.get("didYouMean").isArray());
      assertEquals(2, list.get("didYouMean").size());

      JsonNode none = mapper.valueToTree(service.lookup("doesNotExist"));
      assertFalse(none.has("didYouMean"));
   }
}
