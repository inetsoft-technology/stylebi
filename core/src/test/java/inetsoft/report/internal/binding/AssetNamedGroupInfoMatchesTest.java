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
package inetsoft.report.internal.binding;

import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.NamedGroupAssembly;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78262: an attribute-scoped named group attached to a logical model attribute
 * stores the attached column as (entity, attr), while a viewsheet bound directly to the
 * model exposes that column as AttributeRef(null, "entity:attr").
 */
@Tag("core")
class AssetNamedGroupInfoMatchesTest {
   private static AssetNamedGroupInfo columnAttached(DataRef attached) {
      NamedGroupAssembly assembly = mock(NamedGroupAssembly.class);
      when(assembly.getName()).thenReturn("NG");
      when(assembly.getAttachedType()).thenReturn(AttachedAssembly.COLUMN_ATTACHED);
      when(assembly.getAttachedAttribute()).thenReturn(attached);
      return new AssetNamedGroupInfo(null, assembly);
   }

   private static DataRef vsColumn(String name) {
      return new AttributeRef(null, name);
   }

   @Test
   void logicalModelGroupMatchesEntityQualifiedViewsheetColumn() {
      AssetNamedGroupInfo info =
         columnAttached(new ColumnRef(new AttributeRef("Customer", "Region")));

      assertTrue(info.matches(vsColumn("Customer:Region")));
   }

   @Test
   void logicalModelGroupStillMatchesBareAttribute() {
      AssetNamedGroupInfo info =
         columnAttached(new ColumnRef(new AttributeRef("Customer", "Region")));

      assertTrue(info.matches(vsColumn("Region")));
   }

   @Test
   void logicalModelGroupRejectsOtherAttributeOfSameEntity() {
      AssetNamedGroupInfo info =
         columnAttached(new ColumnRef(new AttributeRef("Customer", "Region")));

      assertFalse(info.matches(vsColumn("Customer:City")));
   }

   @Test
   void logicalModelGroupRejectsSameAttributeOfOtherEntity() {
      AssetNamedGroupInfo info =
         columnAttached(new ColumnRef(new AttributeRef("Customer", "Region")));

      assertFalse(info.matches(vsColumn("Orders:Region")));
   }

   @Test
   void physicalGroupMatchesBareColumn() {
      AssetNamedGroupInfo info = columnAttached(new ColumnRef(new AttributeRef(null, "STATE")));

      assertTrue(info.matches(vsColumn("STATE")));
   }

   @Test
   void physicalGroupRejectsQualifiedColumn() {
      AssetNamedGroupInfo info = columnAttached(new ColumnRef(new AttributeRef(null, "STATE")));

      assertFalse(info.matches(vsColumn("X:STATE")));
   }
}
