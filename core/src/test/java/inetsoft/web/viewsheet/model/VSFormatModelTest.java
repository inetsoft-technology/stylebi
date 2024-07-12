/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SreeHome()
@ExtendWith(MockitoExtension.class)
class VSFormatModelTest {
   @Test
   void canApplyHighlightFormat() throws Exception {
      Color fg = new Color(120, 120, 100);
      Color bg = new Color(200, 220, 240);
      Font font = new Font("Dialog", Font.PLAIN, 18);
      when(textVSAssemblyInfo.getHighlightForeground()).thenReturn(fg);
      when(textVSAssemblyInfo.getHighlightBackground()).thenReturn(bg);
      when(textVSAssemblyInfo.getHighlightFont()).thenReturn(font);

      formatModel = new VSFormatModel(null, textVSAssemblyInfo);
      assertEquals(formatModel.getForeground(), VSCSSUtil.getForegroundColor(fg));
      assertEquals(formatModel.getBackground(), VSCSSUtil.getForegroundColor(bg));
      assertEquals(formatModel.getFont(), VSCSSUtil.getFont(font));
   }

   @Mock TextVSAssemblyInfo textVSAssemblyInfo;
   private VSFormatModel formatModel;
}
