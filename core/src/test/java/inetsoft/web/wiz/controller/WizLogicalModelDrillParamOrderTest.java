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

import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The server stores a drill's parameters grouped by kind (field sources before constants) and its
 * query parameters in hash order, so the read-back compares them as a set, never by position.
 */
class WizLogicalModelDrillParamOrderTest extends WizLogicalModelTestBase {
   private static Definition model(List<DrillParam> params, DrillQuery query) {
      Drill drill = new Drill(List.of(new WizLogicalModelWire.DrillPath(
         "Open", "web", "http://example.test", null, null, null, null, params, query, null)));
      return def("M", entity("E", new Attribute(
         "A", null, null, null, null, "T1", "ID", null, null, null, null, null, null, null, null,
         null, drill, null, null)));
   }

   private static Definition model(List<DrillParam> params) {
      return model(params, null);
   }

   private static DrillParam constant(String name, String value) {
      return new DrillParam(name, value, "integer");
   }

   private static DrillParam field(String name, String column) {
      return new DrillParam(name, column, null);
   }

   @Test
   void aConstantListedBeforeAFieldReadsBackAsStored() {
      assertNull(WizLogicalModelReadBack.diff(
         model(List.of(constant("b", "42"), field("a", "Price"))),
         model(List.of(field("a", "Price"), constant("b", "42")))));
   }

   @Test
   void queryParametersReadBackInAnyOrder() {
      DrillQuery written = new DrillQuery("ws:global:S/o", null, List.of(
         new DrillQueryParam("z", "1"), new DrillQueryParam("a", "2")));
      DrillQuery stored = new DrillQuery("ws:global:S/o", null, List.of(
         new DrillQueryParam("a", "2"), new DrillQueryParam("z", "1")));

      assertNull(WizLogicalModelReadBack.diff(model(null, written), model(null, stored)));
   }

   @Test
   void aChangedParameterStillFails() {
      List<DrillParam> written = List.of(constant("b", "42"), field("a", "Price"));

      assertNotNull(WizLogicalModelReadBack.diff(
         model(written), model(List.of(field("a", "Price"), constant("b", "43")))));
      assertNotNull(WizLogicalModelReadBack.diff(
         model(written), model(List.of(field("a", "Cost"), constant("b", "42")))));
      assertNotNull(WizLogicalModelReadBack.diff(
         model(written), model(List.of(field("a", "Price"),
                                       new DrillParam("b", "42", "string")))));
   }

   @Test
   void aDroppedParameterStillFails() {
      assertNotNull(WizLogicalModelReadBack.diff(
         model(List.of(constant("b", "42"), field("a", "Price"))),
         model(List.of(field("a", "Price")))));
   }

   @Test
   void aChangedQueryParameterStillFails() {
      DrillQuery written = new DrillQuery("ws:global:S/o", null, List.of(
         new DrillQueryParam("z", "1"), new DrillQueryParam("a", "2")));
      DrillQuery changed = new DrillQuery("ws:global:S/o", null, List.of(
         new DrillQueryParam("a", "2"), new DrillQueryParam("z", "9")));
      DrillQuery dropped = new DrillQuery("ws:global:S/o", null, List.of(
         new DrillQueryParam("a", "2")));

      assertNotNull(WizLogicalModelReadBack.diff(model(null, written), model(null, changed)));
      assertNotNull(WizLogicalModelReadBack.diff(model(null, written), model(null, dropped)));
   }
}
