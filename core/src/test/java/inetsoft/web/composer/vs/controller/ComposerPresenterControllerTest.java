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
package inetsoft.web.composer.vs.controller;

import inetsoft.report.Presenter;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.BarPresenter;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.TreeNodeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ComposerPresenterControllerTest {
   @BeforeEach
   void setUp() {
      oldPresenters = SreeEnv.getProperty("user.presenters");
   }

   @AfterEach
   void tearDown() {
      if(oldPresenters == null) {
         SreeEnv.remove("user.presenters");
      }
      else {
         SreeEnv.setProperty("user.presenters", oldPresenters);
      }
   }

   // Bug #78300: user-defined nodes must carry the same {path, class, hasDescriptors}
   // data as built-in nodes, the format pane reads data.class and data.hasDescriptors.
   @Test
   void userDefinedPresenterNodesCarryClassAndDescriptors() throws Exception {
      String withProps = PropertyPresenter.class.getName();
      String noProps = PlainPresenter.class.getName();
      SreeEnv.setProperty("user.presenters", withProps + "|" + noProps);

      TreeNodeModel root = new ComposerPresenterController().getPresenterTree();
      String userLabel = Catalog.getCatalog().getString("common.userDefinedParam");
      TreeNodeModel userNode = root.children().stream()
         .filter(n -> userLabel.equals(n.label()))
         .findFirst()
         .orElseThrow();

      assertEquals(2, userNode.children().size());
      assertTrue(new PresenterRef(withProps).getPropertyDescriptors().length > 0);
      assertEquals(0, new PresenterRef(noProps).getPropertyDescriptors().length);

      assertUserNode(userNode.children().get(0), userLabel, "My Property Bar", withProps, true);
      assertUserNode(userNode.children().get(1), userLabel, "My Plain", noProps, false);
   }

   private static void assertUserNode(TreeNodeModel node, String userLabel, String displayName,
                                      String cls, boolean hasDescriptors)
   {
      assertEquals(displayName, node.label());
      assertTrue(node.leaf());
      assertInstanceOf(Map.class, node.data());

      Map<?, ?> data = (Map<?, ?>) node.data();
      assertEquals(userLabel + "^" + displayName, data.get("path"));
      assertEquals(cls, data.get("class"));
      assertEquals(hasDescriptors, data.get("hasDescriptors"));
   }

   private String oldPresenters;

   public static class PropertyPresenter extends BarPresenter {
      @Override
      public String getDisplayName() {
         return "My Property Bar";
      }
   }

   public static class PlainPresenter implements Presenter {
      @Override
      public void paint(Graphics g, Object v, int x, int y, int w, int h) {
      }

      @Override
      public Dimension getPreferredSize(Object v) {
         return new Dimension(0, 0);
      }

      @Override
      public boolean isPresenterOf(Class type) {
         return true;
      }

      @Override
      public boolean isPresenterOf(Object obj) {
         return true;
      }

      @Override
      public boolean isFill() {
         return false;
      }

      @Override
      public void setFont(Font font) {
      }

      @Override
      public void setBackground(Color bg) {
      }

      @Override
      public String getDisplayName() {
         return "My Plain";
      }

      @Override
      public boolean isRawDataRequired() {
         return false;
      }
   }
}
