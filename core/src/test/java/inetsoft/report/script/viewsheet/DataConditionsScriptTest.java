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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.ScriptArrayScope;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The {@code dataConditions} property of a data-tip viewsheet object: the tip conditions as an array whose elements
 * have {@code attr} and {@code value}. Bug #75569: an element must be readable as {@code conds[i].attr} (a plain
 * array of scopes would hand the elements to the engine unconverted, and both members came out undefined).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataConditionsScriptTest {
   private TextVSAssembly assembly;
   private TextVSAScriptable text;

   @BeforeEach
   void setUp() {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      assembly = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(assembly);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);

      text = new TextVSAScriptable(box);
      text.setAssembly("Text1");
   }

   private static ConditionItem item(String column, Object value) {
      AssetCondition c = new AssetCondition(XSchema.STRING);
      c.setOperation(XCondition.EQUAL_TO);

      if(value != null) {
         c.addValue(value);
      }

      return new ConditionItem(new ColumnRef(new AttributeRef(null, column)), c, 0);
   }

   @Test
   void noTipConditionsGiveNoArray() {
      assertNull(text.getMember("dataConditions"));

      assembly.setTipConditionList(new ConditionList());
      assertNull(text.getMember("dataConditions"), "an empty list is not offered either");
   }

   @Test
   void eachConditionIsAnElementWithAttrAndValue() {
      ConditionList list = new ConditionList();
      list.append(item("State", "NJ"));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(item("City", "Newark"));
      assembly.setTipConditionList(list);

      Object conds = text.getMember("dataConditions");

      assertInstanceOf(ScriptArrayScope.class, conds);
      ScriptArrayScope array = (ScriptArrayScope) conds;
      assertEquals(2L, array.getArraySize());
      assertEquals(2, array.getMember("length"));
      assertTrue(array.hasMember("length"));
      assertFalse(array.hasMember("other"));
      assertNull(array.getMember("other"));
      assertArrayEquals(new Object[] { "length" }, array.getMemberKeys());

      ScriptScope first = (ScriptScope) array.getArrayElement(0);
      assertEquals("State", first.getMember("attr"));
      assertEquals("NJ", first.getMember("value"));
      assertTrue(first.hasMember("attr"));
      assertTrue(first.hasMember("value"));
      assertFalse(first.hasMember("other"));
      assertNull(first.getMember("other"));
      assertArrayEquals(new Object[] { "attr", "value" }, first.getMemberKeys());

      ScriptScope second = (ScriptScope) array.getArrayElement(1);
      assertEquals("City", second.getMember("attr"));
      assertEquals("Newark", second.getMember("value"));
   }

   @Test
   void anIndexOutsideTheArrayGivesNull() {
      ConditionList list = new ConditionList();
      list.append(item("State", "NJ"));
      assembly.setTipConditionList(list);

      ScriptArrayScope array = (ScriptArrayScope) text.getMember("dataConditions");

      assertNull(array.getArrayElement(-1));
      assertNull(array.getArrayElement(1));
   }

   @Test
   void aConditionWithoutValueHasANullValue() {
      ConditionList list = new ConditionList();
      list.append(item("State", null));
      assembly.setTipConditionList(list);

      ScriptArrayScope array = (ScriptArrayScope) text.getMember("dataConditions");
      ScriptScope only = (ScriptScope) array.getArrayElement(0);

      assertEquals("State", only.getMember("attr"));
      assertNull(only.getMember("value"));
   }

   @Test
   void theElementsAreReadOnly() {
      ConditionList list = new ConditionList();
      list.append(item("State", "NJ"));
      assembly.setTipConditionList(list);

      ScriptArrayScope array = (ScriptArrayScope) text.getMember("dataConditions");
      ScriptScope element = (ScriptScope) array.getArrayElement(0);

      element.putMember("attr", "Other");
      element.putMember("value", "XX");
      array.putMember("length", 99);

      assertEquals("State", element.getMember("attr"));
      assertEquals("NJ", element.getMember("value"));
      assertEquals(1, array.getMember("length"));
   }
}
