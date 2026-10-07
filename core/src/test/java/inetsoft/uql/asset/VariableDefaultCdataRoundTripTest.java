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
package inetsoft.uql.asset;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.schema.*;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77903: the default value of a worksheet or logical model variable was written raw into
 * CDATA, so a default holding {@code ]]>} or a control character that XML 1.0 can't carry made
 * the saved worksheet or logical model unreadable. Each case saves the asset through
 * {@link BlobIndexedStorage#putXMLSerializable} and loads it with
 * {@link BlobIndexedStorage#getXMLSerializable}; the default must come back exactly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VariableDefaultCdataRoundTripTest {
   private static final String[] PAYLOADS = {
      "x ]]> y", "arr[i[0]]>5", "a\u0001b\\x", "a\\u0001]]>b\u001F", "lit\\u0041z \\ c:\\x"
   };

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private final List<String> keys = new ArrayList<>();

   @BeforeEach
   void setUp() {
      storage = new BlobIndexedStorage(blobStorageManager);
   }

   @AfterEach
   void tearDown() {
      keys.forEach(storage::remove);
      keys.clear();
   }

   @Test
   void worksheetVariableValueAndExpressionDefaults() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Worksheet ws = new Worksheet();
         addVariable(ws, "V1", XValueNode.createValueNode((Object) p, "default"));
         ExpressionValue ev = new ExpressionValue();
         ev.setType(ExpressionValue.JAVASCRIPT);
         ev.setExpression(p);
         addVariable(ws, "V2", XValueNode.createValueNode(ev, "default", XSchema.STRING));

         Worksheet back = (Worksheet) roundTrip(worksheetEntry("ws77903v" + n++), ws);

         assertEquals(p, defaultValue(back, "V1"), p);
         Object expr = defaultValue(back, "V2");
         assertInstanceOf(ExpressionValue.class, expr, p);
         assertEquals(p, ((ExpressionValue) expr).getExpression(), p);
      }
   }

   @Test
   void worksheetVariableMultiValueAndCharacterDefaults() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         Worksheet ws = new Worksheet();
         addVariable(ws, "V1", XValueNode.createValueNode((Object) new Object[] { "m" + p, "m2" },
                                                          "default"));
         addVariable(ws, "V2", XValueNode.createValueNode((Object) Character.valueOf('\u0001'),
                                                          "default"));

         Worksheet back = (Worksheet) roundTrip(worksheetEntry("ws77903m" + n++), ws);

         assertNotNull(back.getAssembly("V1"), p);
         assertEquals(Character.valueOf('\u0001'), defaultValue(back, "V2"), p);
      }
   }

   @Test
   void logicalModelVariableDefault() throws Exception {
      int n = 0;

      for(String p : PAYLOADS) {
         UserVariable var = new UserVariable("p");
         var.setValueNode(XValueNode.createValueNode((Object) p, "default"));
         XLogicalModel lm = new XLogicalModel("LM");
         lm.addVariable(var);
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.LOGIC_MODEL, "DS/LM77903_" + n++,
                                           null, orgID());

         XLogicalModel back = (XLogicalModel) roundTrip(entry, lm);

         assertEquals(p, ((UserVariable) back.getVariable("p")).getValueNode().getValue(), p);
      }
   }

   private static void addVariable(Worksheet ws, String name, XValueNode value) {
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      AssetVariable var = new AssetVariable();
      var.setName(name);
      var.setTypeNode(XSchema.createPrimitiveType(value.getType()));
      var.setValueNode(value);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
   }

   private static Object defaultValue(Worksheet ws, String name) {
      DefaultVariableAssembly assembly = (DefaultVariableAssembly) ws.getAssembly(name);
      return assembly.getVariable().getValueNode().getValue();
   }

   private XMLSerializable roundTrip(AssetEntry entry, XMLSerializable obj) throws Exception {
      String key = entry.toIdentifier();
      keys.add(key);
      storage.putXMLSerializable(key, obj);
      XMLSerializable back = storage.getXMLSerializable(key, null, orgID());
      assertNotNull(back);
      return back;
   }

   private static AssetEntry worksheetEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null,
                            orgID());
   }

   private static String orgID() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }
}
