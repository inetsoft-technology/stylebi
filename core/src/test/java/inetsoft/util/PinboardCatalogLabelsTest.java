/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80984: the Pinned/Portal Dashboard feature is called Pinboard. The pinboard labels
 * must have their own catalog keys so the shared "Dashboard" strings (audit bookmark
 * history, schedule dashboard action, viewsheet pickers) keep their wording.
 */
@Tag("core")
class PinboardCatalogLabelsTest {
   private static final Properties props = new Properties();

   @BeforeAll
   static void load() throws Exception {
      try(InputStream in = PinboardCatalogLabelsTest.class
         .getResourceAsStream("/inetsoft/util/srinter.properties");
         InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
      {
         props.load(reader);
      }
   }

   @Test
   void pinboardLabelsUsePinboardWording() {
      assertEquals("Pinboard Tab", props.getProperty("Pinboard Tab"));
      assertEquals("User Pinboard Tab", props.getProperty("User Pinboard Tab"));
      assertEquals("Pinboard", props.getProperty("Pinboard"));
      assertEquals("Pinboard Configuration", props.getProperty("Pinboard Configuration"));
      assertEquals("Arrange Pinboard", props.getProperty("Arrange Pinboard"));
      assertEquals("Pin", props.getProperty("Pin"));
      assertEquals("Pin Name", props.getProperty("Pin Name"));
      assertEquals("Edit Pin", props.getProperty("Edit Pin"));
      assertEquals("Select Pin", props.getProperty("Select Pin"));
      assertEquals("New Pin", props.getProperty("New Pin"));
   }

   @Test
   void oldPinboardOnlyKeysAreRemoved() {
      assertNull(props.getProperty("Portal Dashboard Tab"));
      assertNull(props.getProperty("User Portal Dashboard Tab"));
      assertNull(props.getProperty("Dashboard Configuration"));
      assertNull(props.getProperty("Arrange Dashboards"));
      assertNull(props.getProperty("Edit Dashboard"));
   }

   @Test
   void sharedDashboardLabelsAreUnchanged() {
      assertEquals("Dashboard", props.getProperty("Dashboard"));
      assertEquals("Dashboard Name", props.getProperty("Dashboard Name"));
      assertEquals("Select Dashboard", props.getProperty("Select Dashboard"));
      assertEquals("Select Dashboard", props.getProperty("Select Viewsheet"));
   }
}
