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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for tab positioning in {@link AbstractLayout#apply(Viewsheet)}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractLayoutTest {
   /**
    * Bug #76022: children of a bottom-tabs container must end flush with the
    * top of the tab bar. Only the tallest child fills the content band, so a
    * shorter child (a radio button next to a gauge) used to float above it.
    */
   @Test
   void applyBottomTabsChildrenFlushWithTabBar() {
      Viewsheet applied = createTabLayout().apply(createTabViewsheet(true));

      // tab bar goes at the end of the content band
      int tabBarY = LAYOUT_Y + TALL_HEIGHT;
      assertEquals(tabBarY, layoutTop(applied, "Tab1"), "tab bar position");

      assertEquals(LAYOUT_Y, layoutTop(applied, "Tall"),
                   "tallest child fills the content band");
      assertEquals(tabBarY - SHORT_HEIGHT, layoutTop(applied, "Short"),
                   "shorter child should be pushed down against the tab bar");
   }

   @Test
   void applyTopTabsChildrenBelowTabBar() {
      Viewsheet applied = createTabLayout().apply(createTabViewsheet(false));

      assertEquals(LAYOUT_Y, layoutTop(applied, "Tab1"), "tab bar position");
      assertEquals(LAYOUT_Y + TAB_HEIGHT, layoutTop(applied, "Tall"),
                   "children sit below the tab bar");
      assertEquals(LAYOUT_Y + TAB_HEIGHT, layoutTop(applied, "Short"),
                   "children sit below the tab bar");
   }

   private ViewsheetLayout createTabLayout() {
      ViewsheetLayout layout = new ViewsheetLayout();
      layout.setVSAssemblyLayouts(List.of(
         new VSAssemblyLayout("Tab1", new Point(LAYOUT_X, LAYOUT_Y),
                              new Dimension(200, TAB_HEIGHT + TALL_HEIGHT))));

      return layout;
   }

   private Viewsheet createTabViewsheet(boolean bottomTabs) {
      Viewsheet vs = new Viewsheet();

      TextVSAssembly tall = new TextVSAssembly(vs, "Tall");
      tall.setPixelOffset(new Point(160, 204));
      tall.setPixelSize(new Dimension(140, TALL_HEIGHT));

      TextVSAssembly shrt = new TextVSAssembly(vs, "Short");
      shrt.setPixelOffset(new Point(160, 304));
      shrt.setPixelSize(new Dimension(200, SHORT_HEIGHT));

      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      tab.setPixelOffset(new Point(160, 344));
      tab.setPixelSize(new Dimension(200, TAB_HEIGHT));
      ((TabVSAssemblyInfo) tab.getInfo()).setBottomTabsValue(bottomTabs);

      vs.addAssembly(tall);
      vs.addAssembly(shrt);
      vs.addAssembly(tab);
      tab.setAssemblies(new String[]{ "Tall", "Short" });

      return vs;
   }

   private int layoutTop(Viewsheet vs, String name) {
      return ((VSAssembly) vs.getAssembly(name)).getVSAssemblyInfo()
         .getLayoutPosition().y;
   }

   private static final int LAYOUT_X = 80;
   private static final int LAYOUT_Y = 80;
   private static final int TAB_HEIGHT = 24;
   private static final int TALL_HEIGHT = 140;
   private static final int SHORT_HEIGHT = 40;
}
