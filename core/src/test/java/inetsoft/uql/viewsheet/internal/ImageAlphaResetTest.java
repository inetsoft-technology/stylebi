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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ImageAlphaResetTest {
   @Test
   void imageAlphaScriptValueIsRevertedOnResetForImage() {
      ImageVSAssemblyInfo info = new ImageVSAssemblyInfo();
      info.setImageAlphaValue("100");
      info.setImageAlpha("55");
      assertEquals("55", info.getImageAlpha());

      info.resetRuntimeValues();

      assertEquals("100", info.getImageAlpha());
   }

   @Test
   void imageAlphaScriptValueIsRevertedOnResetForGroupContainer() {
      GroupContainerVSAssemblyInfo info = new GroupContainerVSAssemblyInfo();
      info.setImageAlphaValue("100");
      info.setImageAlpha("55");
      assertEquals("55", info.getImageAlpha());

      info.resetRuntimeValues();

      assertEquals("100", info.getImageAlpha());
   }
}
