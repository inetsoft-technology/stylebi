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
package inetsoft.sree.internal;

import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins shipped property defaults that a call site duplicates.
 *
 * {@code DefaultProperties.getProperty(key, def)} returns the caller's default without
 * consulting the defaults layer, so a read that passes a default never sees the corresponding
 * defaults.properties declaration. Where that read cannot be converted to the no-default
 * overload, the two values have to agree by hand, and editing defaults.properties alone would
 * silently change nothing. These tests fail when the pair drifts apart.
 */
@Tag("core")
class PropertyDefaultsTest {
   private static Properties defaults;

   @BeforeAll
   static void loadDefaults() throws IOException {
      defaults = new Properties();

      try(InputStream in = PropertyDefaultsTest.class.getResourceAsStream(
         "/inetsoft/report/defaults.properties"))
      {
         assertNotNull(in, "defaults.properties is missing from the classpath");
         defaults.load(in);
      }
   }

   /**
    * SUtil.getVSToolBarElements() reads each toolbar property through the early-loaded path,
    * whose default layer is the JVM system properties, and supplies its own "true".
    */
   @Test
   void vsImportButtonDefaultMatchesCallSite() {
      assertEquals("true", defaults.getProperty("vs.import.button"),
                   "vs.import.button in defaults.properties must match the \"true\" fallback " +
                      "in SUtil.getVSToolBarElements()");
   }

   /**
    * FreeHelper reads through GTool/GImpl, which only exposes an overload taking a default.
    */
   @Test
   void textLayoutMaxStepDefaultMatchesCallSite() {
      assertEquals("1000", defaults.getProperty("graph.textlayout.maxstep"),
                   "graph.textlayout.maxstep in defaults.properties must match the \"1000\" " +
                      "fallback in FreeHelper");
   }

   /**
    * VGraphPair.isChangedByScript() reads this without a call-site default, so the declaration
    * is the only source of the shipped value and must stay present.
    */
   @Test
   void scriptActionSupportDefaultIsDeclared() {
      assertEquals("false", defaults.getProperty("graph.script.action.support"),
                   "VGraphPair.isChangedByScript() relies on this declaration for its default");
   }

   /**
    * Two toolbar properties do not match the button they control. The names are shipped and
    * appear in stored operator settings, so the mapping is frozen; this pins it so a reorder
    * of VSTOOLBAR_ELEMENTS cannot quietly repoint them.
    */
   @Test
   void undoAndRedoKeepTheirLegacyPropertyNames() {
      assertEquals("Undo", actionIdFor("vs.previous.button"));
      assertEquals("Redo", actionIdFor("vs.next.button"));
   }

   /**
    * AnnotationVSUtil.resetDataAnnotation() reads the Bookmark action's visibility to decide
    * whether to fold chart data annotations into the tooltip, so this pairing has a consumer
    * outside the toolbar.
    */
   @Test
   void bookmarkPairingIsStable() {
      assertEquals("Bookmark", actionIdFor("vs.bookmark.button"));
   }

   @Test
   void toolbarPropertiesAndActionIdsAreUnique() {
      List<SUtil.ToolBarElementDef> defs = SUtil.ToolBarElement.VSTOOLBAR_ELEMENTS;
      Set<String> properties = new HashSet<>();
      Set<String> actionIds = new HashSet<>();

      for(SUtil.ToolBarElementDef def : defs) {
         assertTrue(properties.add(def.property()),
                    "duplicate toolbar property: " + def.property());
         assertTrue(actionIds.add(def.actionId()),
                    "duplicate toolbar action id: " + def.actionId());
      }

      assertEquals(defs.size(), properties.size());
   }

   private static String actionIdFor(String property) {
      return SUtil.ToolBarElement.VSTOOLBAR_ELEMENTS.stream()
         .filter(def -> property.equals(def.property()))
         .map(SUtil.ToolBarElementDef::actionId)
         .findFirst()
         .orElseThrow(() -> new AssertionError("no toolbar entry for " + property));
   }
}
