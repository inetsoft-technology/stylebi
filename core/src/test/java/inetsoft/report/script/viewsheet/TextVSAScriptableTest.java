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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.painter.QRCodePresenter;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;

import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class TextVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private TextVSAScriptable textVSAScriptable;
   private TextVSAssemblyInfo textVSAssemblyInfo;
   private TextVSAssembly textVSAssembly;
   private VSAScriptable vsaScriptable;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      textVSAssembly = new TextVSAssembly();
      textVSAssemblyInfo = (TextVSAssemblyInfo) textVSAssembly.getVSAssemblyInfo();
      textVSAssemblyInfo.setName("Text1");
      viewsheet.addAssembly(textVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      textVSAScriptable = new TextVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      textVSAScriptable.setAssembly("Text1");
      vsaScriptable.setAssembly("Text1");
   }

   @Test
   void testGetClassName() {
      assertEquals("TextVSA", textVSAScriptable.getClassName());
   }

   @ParameterizedTest
   @ValueSource(strings = {"wrapping", "autoSize", "scaleVertical", "embedAsURL", "shadow", "tooltipVisible"})
   void testAddProperties(String propertyName) {
      textVSAScriptable.addProperties();
      assert textVSAScriptable.get(propertyName, textVSAScriptable) instanceof Boolean;
   }

   @ParameterizedTest
   @CsvSource({
      "wrapping, true, true",
      "autoSize, true, true",
      "scaleVertical, false, false",
      "embedAsURL, true, true",
      "shadow, true, true",
      "toolTip, testTooltip, testTooltip"
   })
   void testSetProperty(String propertyName, Object propertyValue, Object expectedValue) {
      textVSAScriptable.setProperty(propertyName, propertyValue);
      assertEquals(expectedValue, textVSAScriptable.get(propertyName, textVSAScriptable));
   }

   @Test
   void testGetSetPopComponent() {
      textVSAScriptable.setPopComponent("testComponent");
      assertEquals("testComponent", textVSAScriptable.getPopComponent());

      textVSAScriptable.setPopComponentValue("testValue");
      assertEquals("testValue", textVSAScriptable.getPopComponent());
   }

   @Test
   void testGetSetPopLocation() {
      textVSAScriptable.setPopLocation("CENTER");
      assertEquals(PopVSAssemblyInfo.PopLocation.CENTER.value, textVSAScriptable.getPopLocation());
      textVSAScriptable.setPopLocation("MOUSE");
      assertEquals(PopVSAssemblyInfo.PopLocation.MOUSE.value, textVSAScriptable.getPopLocation());

      //invalid position
      RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> {
         textVSAScriptable.setPopLocation("test");
      });
      assertEquals("Invalid PopLocation", runtimeException.getMessage());
   }

   @Test
   void testSetPresenter() {
      textVSAScriptable.setPresenter("testPresenter");
      assertEquals("testPresenter",
                   textVSAssemblyInfo.getFormat().getUserDefinedFormat().getPresenter().getName());

      QRCodePresenter qrCodePresenter = mock(QRCodePresenter.class);
      textVSAScriptable.setPresenter(qrCodePresenter);
      assertEquals("inetsoft.report.painter.QRCodePresenter",
                   textVSAssemblyInfo.getFormat().getUserDefinedFormat().getPresenter().getName());
   }
}