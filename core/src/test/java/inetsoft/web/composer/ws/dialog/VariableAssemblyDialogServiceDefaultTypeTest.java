/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.VariableAssemblyDialogModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #79607, the default value of a worksheet variable must be stored with the variable's
 * declared type. A typeless value node turned a long default into an integer node, and a long
 * default above the int range then made the worksheet fail to load.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VariableAssemblyDialogServiceDefaultTypeTest {
   @Test
   void longDefaultAboveIntRangeSurvivesRoundTrip() throws Exception {
      XValueNode node = roundTrip("long", "3000000000");

      assertInstanceOf(LongValue.class, node);
      assertEquals(Long.valueOf(3000000000L), node.getValue());
   }

   @Test
   void longDefaultWithinIntRangeKeepsLongType() throws Exception {
      XValueNode node = roundTrip("long", "42");

      assertInstanceOf(LongValue.class, node);
      assertEquals(Long.valueOf(42L), node.getValue());
   }

   @Test
   void shortDefaultKeepsShortType() throws Exception {
      XValueNode node = roundTrip("short", "12");

      assertInstanceOf(ShortValue.class, node);
      assertEquals(Short.valueOf((short) 12), node.getValue());
   }

   @Test
   void byteDefaultKeepsByteType() throws Exception {
      XValueNode node = roundTrip("byte", "7");

      assertInstanceOf(ByteValue.class, node);
      assertEquals(Byte.valueOf((byte) 7), node.getValue());
   }

   @Test
   void charDefaultKeepsCharacterType() throws Exception {
      XValueNode node = roundTrip("char", "x");

      assertInstanceOf(CharacterValue.class, node);
      assertEquals(Character.valueOf('x'), node.getValue());
   }

   @Test
   void integerDefaultStillIntegerType() throws Exception {
      XValueNode node = roundTrip("integer", "5");

      assertInstanceOf(IntegerValue.class, node);
      assertEquals(Integer.valueOf(5), node.getValue());
   }

   @Test
   void stringAndDoubleDefaultsUnchanged() throws Exception {
      assertInstanceOf(StringValue.class, roundTrip("string", "abc"));
      XValueNode node = roundTrip("double", "1.5");
      assertInstanceOf(DoubleValue.class, node);
      assertEquals(Double.valueOf(1.5), node.getValue());
   }

   @Test
   void otherTypesKeepTheirNodeClass() throws Exception {
      assertInstanceOf(FloatValue.class, roundTrip("float", "2.5"));
      assertEquals(Boolean.TRUE, roundTrip("boolean", "true").getValue());
      assertInstanceOf(BooleanValue.class, roundTrip("boolean", "true"));
      assertInstanceOf(DateValue.class, roundTrip("date", "2024-03-05"));
      assertInstanceOf(TimeValue.class, roundTrip("time", "10:20:30"));
   }

   @Test
   void emptyAndNullDefaultsStillRoundTrip() throws Exception {
      for(String type : new String[] { "long", "integer", "string", "double" }) {
         for(String def : new String[] { "", null }) {
            XValueNode node = roundTrip(type, def);

            if("string".equals(type) && "".equals(def)) {
               assertInstanceOf(StringValue.class, node);
               assertEquals("", node.getValue());
            }
            else {
               assertNull(node, type + " with default [" + def + "] should have no value node");
            }
         }
      }
   }

   /**
    * Converts a dialog model to a variable, writes it in a worksheet and reads the worksheet back.
    */
   private XValueNode roundTrip(String type, String defaultValue) throws Exception {
      VariableAssemblyDialogModel model = new VariableAssemblyDialogModel();
      model.setNewName("v79607");
      model.setType(type);
      model.setDefaultValue(defaultValue);
      model.setSelectionList("none");

      VariableAssemblyDialogService service = new VariableAssemblyDialogService(
         mock(ViewsheetService.class), mock(DataSourceRegistry.class));
      Method convert = VariableAssemblyDialogService.class.getDeclaredMethod(
         "convertModelToAssetVariable", VariableAssemblyDialogModel.class, Worksheet.class);
      convert.setAccessible(true);

      Worksheet ws = new Worksheet();
      AssetVariable variable = (AssetVariable) convert.invoke(service, model, ws);
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, "v79607");
      assembly.setVariable(variable);
      ws.addAssembly(assembly);

      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      ws.writeXML(writer);
      writer.flush();

      Element elem = Tool.parseXML(new StringReader(out.toString())).getDocumentElement();
      Worksheet loaded = new Worksheet();
      loaded.parseXML(elem);

      DefaultVariableAssembly reloaded = (DefaultVariableAssembly) loaded.getAssembly("v79607");
      return reloaded.getVariable().getValueNode();
   }
}
