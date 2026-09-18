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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #76791: GroupContainerVSAssemblyInfo duplicates ImageVSAssemblyInfo's
 * alpha setters byte-for-byte (no shared superclass for this logic), so it
 * carried the same two defects independently.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GroupContainerVSAssemblyInfoAlphaTest {
   @Test
   void setImageAlphaValueClampsOutOfRangeNumericInput() {
      GroupContainerVSAssemblyInfo info = new GroupContainerVSAssemblyInfo();

      info.setImageAlphaValue("999");
      assertEquals("100", info.getImageAlphaValue());

      info.setImageAlphaValue("-50");
      assertEquals("0", info.getImageAlphaValue());
   }

   @Test
   void setImageAlphaValueFallsBackToOpaqueOnNonNumericInput() {
      GroupContainerVSAssemblyInfo info = new GroupContainerVSAssemblyInfo();

      info.setImageAlphaValue("not-a-number");
      assertEquals("100", info.getImageAlphaValue());
   }

   @Test
   void setImageAlphaValueLeavesValidInputUnchanged() {
      GroupContainerVSAssemblyInfo info = new GroupContainerVSAssemblyInfo();

      info.setImageAlphaValue("42");
      assertEquals("42", info.getImageAlphaValue());
   }

   @Test
   void setImageAlphaNoLongerThrowsOnNonNumericInputAndClampsToDefault() {
      GroupContainerVSAssemblyInfo info = new GroupContainerVSAssemblyInfo();

      assertDoesNotThrow(() -> info.setImageAlpha("abc"));
      assertEquals("100", info.getImageAlpha());
   }

   @Test
   void setImageAlphaStillClampsOutOfRangeNumericInput() {
      GroupContainerVSAssemblyInfo info = new GroupContainerVSAssemblyInfo();

      info.setImageAlpha("999");
      assertEquals("100", info.getImageAlpha());

      info.setImageAlpha("-50");
      assertEquals("0", info.getImageAlpha());
   }

   @Test
   void designTimeAlphaSetViaSetAssemblyPropertiesReachesExportAsClampedInt() {
      // simulates GroupContainerPropertyDialogService's apply path
      // (imagePreviewPane.alpha() + "") feeding an out-of-range preview-pane
      // value straight into the design value
      GroupContainerVSAssemblyInfo info = new GroupContainerVSAssemblyInfo();
      info.setImageAlphaValue(999 + "");

      String alpha = info.getImageAlpha();

      // must be safely parseable/clamped for PDFCoordinateHelper/SVGCoordinateHelper's
      // Integer.parseInt(alpha) + new Color(1f, 1f, 1f, 1f - alpha / 100f) to not throw
      int parsed = Integer.parseInt(alpha);
      float alphaVal = 1.0f - parsed / 100.0f;
      assertDoesNotThrow(() -> new java.awt.Color(1.0f, 1.0f, 1.0f, alphaVal));
   }
}
