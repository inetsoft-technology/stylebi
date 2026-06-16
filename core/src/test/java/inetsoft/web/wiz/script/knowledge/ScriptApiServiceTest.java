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
package inetsoft.web.wiz.script.knowledge;

import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.FunctionSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@WizAgentTestSupport
class ScriptApiServiceTest {

   private ScriptApiService service;

   @BeforeEach
   void setUp() {
      service = new ScriptApiService();
      service.load();
   }

   @Test
   void lookupTopLevelFunction() {
      // formatDate is a known top-level function in js-functions.json
      FunctionSignature sig = service.lookup("formatDate");
      assertNotNull(sig, "formatDate should exist in js-functions.json");
      assertEquals("formatDate", sig.name());
      // !type and !url should be populated
      assertNotNull(sig.type(), "formatDate should have a !type");
      assertNotNull(sig.url(), "formatDate should have a !url");
   }

   @Test
   void lookupPrototypeMethod() {
      // Number.toFixed is a prototype method in js-functions.json
      FunctionSignature sig = service.lookup("Number.toFixed");
      assertNotNull(sig, "Number.toFixed should exist in js-functions.json");
      assertEquals("Number.toFixed", sig.name());
      assertNotNull(sig.url());
   }

   @Test
   void lookupUnknownReturnsNull() {
      assertNull(service.lookup("nonExistentFunction_xyz_abc"));
   }

   @Test
   void lookupNullReturnsNull() {
      assertNull(service.lookup(null));
      assertNull(service.lookup(""));
   }

   @Test
   void treeIsNonEmpty() {
      assertFalse(service.tree().isEmpty(), "function tree should contain entries");
      // formatDate should be in the tree
      assertTrue(service.tree().containsKey("formatDate"));
   }
}
