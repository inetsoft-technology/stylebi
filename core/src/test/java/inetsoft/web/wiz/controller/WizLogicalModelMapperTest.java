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

import inetsoft.uql.DrillPath;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wire to DTO mapping. The central test puts a sentinel in EVERY wire field and follows it to
 * the DTO and back, so a field added to the wire without a mapping (a silent drop, charter C2)
 * fails here.
 */
class WizLogicalModelMapperTest extends WizLogicalModelTestBase {
   private final WizLogicalModelMapper mapper = new WizLogicalModelMapper();

   private static Definition sentinel() {
      String worksheet = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "ws1", null).toIdentifier();
      WizLogicalModelWire.DrillPath path = new WizLogicalModelWire.DrillPath(
         "drill", "viewsheet", "vs/link", "_blank", "the tip", false, true,
         List.of(new DrillParam("p", "FIELD", "string")),
         new DrillQuery(worksheet, List.of(new DrillQueryParam("k", "v"))), List.of());
      Attribute column = new Attribute(
         "Amount", "Amount", "the amount", false, "column", "T1", "AMOUNT", null, null, null,
         "double", "measure", "Sum", true, "select 1", new Format("Decimal", "#,##0", true),
         new Drill(List.of(path)), false, null);
      Attribute expression = new Attribute(
         "Doubled", "Doubled", "computed", false, "expression", null, null, "T1.AMOUNT * 2", true,
         false, "double", "dimension", "Max", true, "select 2", null, null, false, null);
      Entity entity = new Entity("Sales", "Sales", "the entity", false, false, null,
                                 List.of(column, expression));
      return new Definition("Model", "the model", "pv", "F", "conn", null, false,
                            List.of(entity));
   }

   @Test
   void everyWireFieldIsCarried() throws Exception {
      Definition wire = sentinel();
      LogicalModelDefinition dto = mapper.toDto(wire, null, "pv");

      assertEquals("Model", dto.getName());
      assertEquals("pv", dto.getPartition());
      assertEquals("the model", dto.getDescription());
      assertEquals("conn", dto.getConnection());
      assertEquals("F", dto.getFolder());
      EntityModel entity = dto.getEntities().get(0);
      assertEquals("Sales", entity.getName());
      assertEquals("the entity", entity.getDescription());
      assertFalse(entity.isVisible());
      XAttributeModel amount = entity.getAttributes().get(0);
      assertEquals("column", amount.getType());
      assertEquals("Amount", amount.getName());
      assertEquals("the amount", amount.getDescription());
      assertFalse(amount.isVisible());
      assertEquals("T1", amount.getTable());
      assertEquals("AMOUNT", amount.getColumn());
      assertEquals("double", amount.getDataType());
      assertEquals(2, amount.getRefType().getType());
      assertEquals("Sum", amount.getRefType().getFormula());
      assertTrue(amount.isBrowseData());
      assertEquals("select 1", amount.getBrowseQuery());
      assertEquals("Decimal", amount.getFormat().getFormat());
      assertEquals("#,##0", amount.getFormat().getFormatSpec());
      AutoDrillPathModel drill = amount.getDrillInfo().getPaths().get(0);
      assertEquals("drill", drill.getName());
      assertEquals(DrillPath.VIEWSHEET_LINK, drill.getLinkType());
      assertEquals("vs/link", drill.getLink());
      assertEquals("_blank", drill.getTargetFrame());
      assertEquals("the tip", drill.getTip());
      assertFalse(drill.isPassParams());
      assertTrue(drill.isDisablePrompting());
      assertEquals("FIELD", drill.getParams().get(0).getField());
      assertEquals("k", drill.getQuery().getParams().get(0).getKey());
      assertEquals("ws1", drill.getQuery().getEntry().getName());
      XAttributeModel doubled = entity.getAttributes().get(1);
      assertEquals("expression", doubled.getType());
      assertEquals("T1.AMOUNT * 2", doubled.getExpression());
      assertTrue(doubled.isAggregate());
      assertFalse(doubled.isParseable());
      assertEquals(1, doubled.getRefType().getType());

      // and back: nothing the wire carried is lost on the way
      Definition back = mapper.toWire(dto, false);
      assertNull(WizLogicalModelReadBack.diff(wire, back));
      assertEquals(WizLogicalModelReadBack.canon(wire), WizLogicalModelReadBack.canon(back));
   }

   @Test
   void theSentinelCoversEveryComponentOfEveryRecord() throws Exception {
      // a wire field added later but not given a sentinel would make the test above vacuous
      assertAllSet(sentinel(), "Definition",
                   List.of("Definition.parent", "Entity.errorMessage", "Attribute.errorMessage",
                           "DrillPath.queryFields"));
   }

   @Test
   void typeIsAlwaysSetFromWhichOfColumnOrExpressionIsPresent() {
      // the service builds an expression attribute for any type but exactly "column"
      Attribute noType = new Attribute("A", null, null, null, null, "T1", "ID", null, null, null,
                                       null, null, null, null, null, null, null, null, null);
      Attribute conflicting = new Attribute("B", null, null, null, "column", null, null,
                                            "T1.ID + 1", null, null, null, null, null, null,
                                            null, null, null, null, null);
      Definition wire = new Definition("M", null, "pv", null, null, null, null,
                                       List.of(new Entity("E", null, null, null, null, null,
                                                          List.of(noType, conflicting))));

      LogicalModelDefinition dto = mapper.toDto(wire, null, "pv");

      assertEquals("column", dto.getEntities().get(0).getAttributes().get(0).getType());
      assertEquals("expression", dto.getEntities().get(0).getAttributes().get(1).getType(),
                   "the presence of an expression decides, never a stale type");
   }

   @Test
   void durationFormatWithoutPaddingRoundTripsThroughItsStoredSpelling() {
      Attribute attribute = new Attribute("A", null, null, null, null, "T1", "ID", null, null,
                                          null, "integer", null, null, null, null,
                                          new Format("DurationFormat", null, false), null, null,
                                          null);
      Definition wire = new Definition("M", null, "pv", null, null, null, null,
                                       List.of(new Entity("E", null, null, null, null, null,
                                                          List.of(attribute))));
      LogicalModelDefinition dto = mapper.toDto(wire, null, "pv");
      XFormatInfoModel stored = new XFormatInfoModel();
      stored.setFormat(inetsoft.web.adhoc.model.FormatInfoModel.getDurationFormat(
         dto.getEntities().get(0).getAttributes().get(0).getFormat().getFormat(),
         dto.getEntities().get(0).getAttributes().get(0).getFormat().isDurationPadZeros()));
      dto.getEntities().get(0).getAttributes().get(0).setFormat(stored);

      Format read = mapper.toWire(dto, false).entities().get(0).attributes().get(0).format();

      assertEquals("DurationFormat", read.format());
      assertFalse(read.durationPadZeros());
   }

   @Test
   void markersResolveAgainstTheStoredModel() {
      assertEquals("A", WizLogicalModelMapper.resolveOldName(null, "A", true, true),
                   "absent and stored: identity");
      assertNull(WizLogicalModelMapper.resolveOldName(null, "A", true, false),
                 "absent and not stored: a new element");
      assertNull(WizLogicalModelMapper.resolveOldName("", "A", true, true),
                 "explicit null: a new element even when the name is stored");
      assertEquals("Old", WizLogicalModelMapper.resolveOldName("Old", "A", true, true));
      assertNull(WizLogicalModelMapper.resolveOldName("Old", "A", false, true),
                 "a create has nothing to migrate");
   }

   // -- helpers ----------------------------------------------------------------------------------

   /**
    * Collects, over the whole tree, every record component that is set in at least one instance
    * and every component that exists; the difference must be exactly the exempt list.
    */
   private static void assertAllSet(Object root, String ignored, List<String> exempt)
      throws Exception
   {
      java.util.Set<String> all = new java.util.TreeSet<>();
      java.util.Set<String> set = new java.util.TreeSet<>();
      collect(root, all, set);
      java.util.Set<String> unset = new java.util.TreeSet<>(all);
      unset.removeAll(set);
      unset.removeIf(key -> exempt.stream().anyMatch(e -> e.equals(key) || e.startsWith(key + "=")));
      assertTrue(unset.isEmpty(), "the sentinel leaves these wire fields unset: " + unset);
   }

   private static void collect(Object value, java.util.Set<String> all, java.util.Set<String> set)
      throws Exception
   {
      if(value instanceof List<?>) {
         for(Object item : (List<?>) value) {
            collect(item, all, set);
         }

         return;
      }

      if(value == null || !value.getClass().isRecord()) {
         return;
      }

      String type = value.getClass().getSimpleName();

      for(RecordComponent component : value.getClass().getRecordComponents()) {
         Object member = component.getAccessor().invoke(value);
         String key = type + "." + component.getName();
         all.add(key);

         if(member != null && !(member instanceof List<?> && ((List<?>) member).isEmpty())) {
            set.add(key);
         }

         collect(member, all, set);
      }
   }
}
