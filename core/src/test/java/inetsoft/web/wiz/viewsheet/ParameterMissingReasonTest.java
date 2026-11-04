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
package inetsoft.web.wiz.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.schema.StringType;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.TextInputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the refusal text {@link ParameterDiscoveryService#find(List, String, Viewsheet)} gives
 * for a name absent from the discovered list (bug #80319). Needs the Spring context because
 * {@code Viewsheet}/{@code ViewsheetInfo} read {@code SreeEnv}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ParameterMissingReasonTest {
   private static final String GENERIC_PREFIX =
      "'%s' is not a parameter this viewsheet's source or viewsheet tree references";

   private static UserVariable var(String name) {
      UserVariable v = new UserVariable();
      v.setName(name);
      v.setTypeNode(new StringType());
      return v;
   }

   private static TextInputVSAssembly input(String name, String variableKey) {
      TextInputVSAssembly input = new TextInputVSAssembly();
      input.getVSAssemblyInfo().setName(name);

      if(variableKey != null) {
         input.setVariable(true);
         input.setTableName("$(" + variableKey + ")");
      }

      return input;
   }

   private static String refusal(Viewsheet vs, String name) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ParameterDiscoveryService.find(List.of(var("pA")), name, vs));
      return e.getMessage();
   }

   @Test
   void disabledOnlyNamesDisabledParametersAndHowToReEnable() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().addDisabledVariable("pB");

      String msg = refusal(vs, "pB");

      assertTrue(msg.startsWith("'pB' is listed in this viewsheet's disabledParameters"), msg);
      assertTrue(msg.contains("set_viewsheet_properties"), msg);
      assertFalse(msg.startsWith(String.format(GENERIC_PREFIX, "pB")), msg);
   }

   @Test
   void keyBoundNamesTheAssemblyAndPointsToSetInputValue() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(input("TextInput1", "pK"));

      String msg = refusal(vs, "pK");

      assertTrue(msg.contains("input assembly 'TextInput1'"), msg);
      assertTrue(msg.contains("set_input_value"), msg);
      assertFalse(msg.contains("disabled"), msg);
   }

   @Test
   void disabledAndKeyBoundDoesNotAdviseRemovingFromDisabled() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(input("TextInput1", "pK"));
      vs.getViewsheetInfo().addDisabledVariable("pK");

      String msg = refusal(vs, "pK");

      assertTrue(msg.contains("input assembly 'TextInput1'"), msg);
      assertTrue(msg.contains("disabledParameters"), msg);
      assertFalse(msg.contains("remove it from"), msg);
      assertFalse(msg.contains("set_viewsheet_properties"), msg);
   }

   @Test
   void nameOnlyMatchDoesNotAssertAParameterExists() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(input("TextInput1", null));

      String msg = refusal(vs, "TextInput1");

      assertTrue(msg.startsWith("'TextInput1' matches the name of input assembly 'TextInput1'"),
                 msg);
      assertTrue(msg.contains("set_input_value"), msg);
      assertFalse(msg.startsWith(String.format(GENERIC_PREFIX, "TextInput1")), msg);
   }

   @Test
   void nestedInputIsClassifiedByLeafNameAndKey() {
      Viewsheet outer = new Viewsheet();
      Viewsheet nested = new Viewsheet();
      nested.getVSAssemblyInfo().setName("Nested1");
      nested.addAssembly(input("LeafInput", "pN"));
      outer.addAssembly(nested);

      // Record which name Assembly::getName reports for a nested input (leaf or prefixed).
      String reported = null;

      for(Assembly assembly : outer.getAssemblies(true, true)) {
         if(assembly instanceof TextInputVSAssembly) {
            reported = assembly.getName();
         }
      }

      System.out.println("T80319 nested getName=" + reported);
      assertNotNull(reported);

      assertTrue(refusal(outer, "pN").contains("input assembly "), "key-bound nested");

      String leaf = refusal(outer, "LeafInput");
      assertFalse(leaf.startsWith(String.format(GENERIC_PREFIX, "LeafInput")), leaf);
      assertTrue(leaf.contains("matches the name of input assembly"), leaf);

      if(!"LeafInput".equals(reported)) {
         String prefixed = refusal(outer, reported);
         assertTrue(prefixed.contains("matches the name of input assembly"), prefixed);
      }
   }

   @Test
   void nestedViewsheetDisabledListIsHonoured() {
      Viewsheet outer = new Viewsheet();
      Viewsheet nested = new Viewsheet();
      nested.getVSAssemblyInfo().setName("Nested1");
      nested.getViewsheetInfo().addDisabledVariable("pD");
      outer.addAssembly(nested);

      assertTrue(refusal(outer, "pD").contains("disabledParameters"));
   }

   @Test
   void unknownNameKeepsGenericTextByteForByte() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().addDisabledVariable("pB");
      vs.addAssembly(input("TextInput1", "pK"));

      String expected = "'NoSuchParam' is not a parameter this viewsheet's source or viewsheet " +
         "tree references (or it is already bound to an on-canvas input assembly -- use " +
         "set_input_value for that). collect_parameters lists what is settable here.";

      assertEquals(expected, refusal(vs, "NoSuchParam"));
      assertFalse(expected.toLowerCase().contains("disabled"));
   }

   @Test
   void nullViewsheetFallsBackToGenericText() {
      String msg = refusal(null, "pB");

      assertTrue(msg.startsWith(String.format(GENERIC_PREFIX, "pB")), msg);
   }

   @Test
   void twoArgFindIsUnchanged() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ParameterDiscoveryService.find(List.of(var("pA")), "pB"));

      assertTrue(e.getMessage().startsWith(String.format(GENERIC_PREFIX, "pB")));
   }
}
