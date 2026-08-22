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
package inetsoft.uql.viewsheet;

import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.CrosstabTree;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code updateGroupExpandPath}'s two casts to {@code SNamedGroupInfo} widen to
 * {@code XNamedGroupInfo} -- a pure widen, since the method only ever calls {@code isEmpty()}/
 * {@code equals()}, both already interface-generic. Invoked via reflection since the method
 * itself is private and its public entry point ({@code setVSAssemblyInfo}) needs a full
 * cube/aggregate-change scenario unrelated to what this method actually does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabVSAssemblyTest {
   private static VSDimensionRef regionRef() {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new ColumnRef(new AttributeRef("REGION")));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static void markDrilled(CrosstabVSAssembly assembly, String field) throws Exception {
      Field expandedField = CrosstabTree.class.getDeclaredField("expanded");
      expandedField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Set<String>> expanded =
         (Map<String, Set<String>>) expandedField.get(assembly.getCrosstabTree());
      expanded.put(field, new HashSet<>(Set.of("some-drilled-value")));
   }

   private static void invokeUpdateGroupExpandPath(CrosstabVSAssembly assembly,
                                                    VSDimensionRef nref, VSDimensionRef oref)
      throws Exception
   {
      Method method = CrosstabVSAssembly.class.getDeclaredMethod(
         "updateGroupExpandPath", inetsoft.uql.erm.DataRef.class, inetsoft.uql.erm.DataRef.class);
      method.setAccessible(true);
      method.invoke(assembly, nref, oref);
   }

   @Test
   void removesTheDrillWhenAnExpertNamedGroupIsAddedToAPreviouslyUngroupedDimension() throws Exception {
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      VSDimensionRef oref = regionRef();
      markDrilled(assembly, oref.getFullName());

      VSDimensionRef nref = regionRef();
      nref.setNamedGroupInfo(new ExpertNamedGroupInfo());
      ExpertNamedGroupInfo info = (ExpertNamedGroupInfo) nref.getNamedGroupInfo();
      info.setGroupCondition("West", new inetsoft.uql.ConditionList());

      assertDoesNotThrow(() -> invokeUpdateGroupExpandPath(assembly, nref, oref));
      assertFalse(assembly.getCrosstabTree().isDrilled(oref.getFullName()),
         "grouping a previously-ungrouped dimension should invalidate its drill path");
   }

   @Test
   void removesTheDrillWhenAnExpertNamedGroupIsChanged() throws Exception {
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();

      VSDimensionRef oref = regionRef();
      ExpertNamedGroupInfo oldInfo = new ExpertNamedGroupInfo();
      oldInfo.setGroupCondition("West", new inetsoft.uql.ConditionList());
      oref.setNamedGroupInfo(oldInfo);
      markDrilled(assembly, oref.getFullName());

      VSDimensionRef nref = regionRef();
      ExpertNamedGroupInfo newInfo = new ExpertNamedGroupInfo();
      newInfo.setGroupCondition("East", new inetsoft.uql.ConditionList());
      nref.setNamedGroupInfo(newInfo);

      assertDoesNotThrow(() -> invokeUpdateGroupExpandPath(assembly, nref, oref));
      assertFalse(assembly.getCrosstabTree().isDrilled(oref.getFullName()),
         "changing an Expert named group's conditions should invalidate the drill path");
   }

   @Test
   void leavesTheDrillAloneWhenTheExpertNamedGroupIsUnchanged() throws Exception {
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();

      VSDimensionRef oref = regionRef();
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("West", new inetsoft.uql.ConditionList());
      oref.setNamedGroupInfo(info);
      markDrilled(assembly, oref.getFullName());

      VSDimensionRef nref = regionRef();
      nref.setNamedGroupInfo((ExpertNamedGroupInfo) info.clone());

      assertDoesNotThrow(() -> invokeUpdateGroupExpandPath(assembly, nref, oref));
      assertTrue(assembly.getCrosstabTree().isDrilled(oref.getFullName()),
         "an unchanged Expert named group must not disturb an existing drill path");
   }
}
