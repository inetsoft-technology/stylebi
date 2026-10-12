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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78326: the drill field key was written raw into the {@code field} attribute of
 * {@code <expanded>} and {@code <collapsedRef>}, so a key holding {@code &}, {@code <} or
 * {@code "} made the saved viewsheet XML malformed. A control character in a key is written
 * as a space by {@link Tool#escape}, so it is well-formed but not read back exactly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CrosstabTreeFieldAttrTest {
   private static final String[] KEYS = { "State", "Year(R&D Date)", "a<b \"c\"" };

   @Test
   void expandedFieldKeyRoundTrips() throws Exception {
      for(String key : KEYS) {
         CrosstabTree tree = new CrosstabTree();
         tree.getExpandedPaths().put(key, new HashSet<>(Set.of("[NJ]", "[NY]")));

         CrosstabTree back = new CrosstabTree();
         back.parseXML(parse(tree::writeXML));

         assertEquals(Map.of(key, Set.of("[NJ]", "[NY]")), back.getExpandedPaths(), key);
      }
   }

   @Test
   void collapsedRefFieldKeyRoundTrips() throws Exception {
      for(String key : KEYS) {
         CrosstabTree tree = new CrosstabTree();
         tree.updateChildRef("State", dim(key));
         String xml = xml(tree::writeXML);

         CrosstabTree back = new CrosstabTree();
         back.parseXML(Tool.parseXML(stream(xml), "UTF-8", false, false)
                          .getDocumentElement());

         // the key survives: writing the parsed tree again gives the same xml
         assertEquals(xml, xml(back::writeXML), key);
         assertTrue(xml.contains("<collapsedRef field=\"" + Tool.escape(key) + "\">"), xml);
      }
   }

   @Test
   void fullCrosstabInfoRoundTrips() throws Exception {
      for(String key : KEYS) {
         CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
         info.setName("Crosstab1");
         info.getCrosstabTree().getExpandedPaths().put(key, new HashSet<>(Set.of("[NJ]")));
         info.getCrosstabTree().updateChildRef("State", dim(key + "2"));

         CrosstabVSAssemblyInfo back = new CrosstabVSAssemblyInfo();
         back.parseXML(parse(info::writeXML));

         assertEquals(Map.of(key, Set.of("[NJ]")), back.getCrosstabTree().getExpandedPaths(),
                      key);
         assertEquals(xml(info.getCrosstabTree()::writeXML),
                      xml(back.getCrosstabTree()::writeXML), key);
      }
   }

   /**
    * A control character in a key is written as a space: the XML stays well-formed, the key is
    * not read back exactly (same limit as {@code DrillPath}).
    */
   @Test
   void controlCharacterKeyIsWellFormed() throws Exception {
      String key = "x\u0001y";
      CrosstabTree tree = new CrosstabTree();
      tree.getExpandedPaths().put(key, new HashSet<>(Set.of("[NJ]")));
      tree.updateChildRef("State", dim(key));

      CrosstabTree back = new CrosstabTree();
      back.parseXML(parse(tree::writeXML));

      assertEquals(Set.of("[NJ]"), back.getExpandedPaths().get("x y"));
   }

   private static VSDimensionRef dim(String name) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setGroupColumnValue(name);
      return ref;
   }

   private static String xml(Consumer<PrintWriter> writer) {
      StringWriter buf = new StringWriter();
      PrintWriter out = new PrintWriter(buf);
      writer.accept(out);
      out.flush();
      return buf.toString();
   }

   private static ByteArrayInputStream stream(String xml) {
      return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
   }

   private static Element parse(Consumer<PrintWriter> writer) throws Exception {
      return Tool.parseXML(stream(xml(writer)), "UTF-8", false, false).getDocumentElement();
   }
}
