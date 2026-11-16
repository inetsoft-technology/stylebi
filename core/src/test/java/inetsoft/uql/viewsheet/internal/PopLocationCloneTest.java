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

package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81464: cloning a Text or Image assembly info must not share popLocationValue.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PopLocationCloneTest {
   @Test
   void clonedTextInfoDoesNotShareRuntimePopLocation() {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      TextVSAssemblyInfo copy = (TextVSAssemblyInfo) info.clone();
      copy.setPopLocation(PopVSAssemblyInfo.PopLocation.CENTER);
      assertEquals(PopVSAssemblyInfo.PopLocation.CENTER, copy.getPopLocation());
      assertEquals(PopVSAssemblyInfo.PopLocation.MOUSE, info.getPopLocation());
   }

   @Test
   void clonedImageInfoDoesNotShareRuntimePopLocation() {
      ImageVSAssemblyInfo info = new ImageVSAssemblyInfo();
      ImageVSAssemblyInfo copy = (ImageVSAssemblyInfo) info.clone();
      copy.setPopLocation(PopVSAssemblyInfo.PopLocation.CENTER);
      assertEquals(PopVSAssemblyInfo.PopLocation.CENTER, copy.getPopLocation());
      assertEquals(PopVSAssemblyInfo.PopLocation.MOUSE, info.getPopLocation());
   }
}
