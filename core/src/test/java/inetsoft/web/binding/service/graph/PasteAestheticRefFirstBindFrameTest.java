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
package inetsoft.web.binding.service.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import inetsoft.web.binding.service.graph.aesthetic.ColorFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finding 16 live-repro gap, second half. {@code ChartAestheticMutator.setField} correctly
 * carries a channel's existing frame model onto the new {@code AestheticInfo} (see
 * {@code ChartAestheticMutatorTest.aFrameSetBeforeAnyFieldIsBoundCarriesOntoTheFieldWhenOneIsBound}),
 * but persisting that {@code AestheticInfo} onto a brand-new {@code AestheticRef} -- the "first
 * bind" shape, where no {@code AestheticRef} existed on the channel yet -- discards it anyway:
 * {@code pasteAestheticRef}'s {@code wrapper == null} branch calls
 * {@code model.getFrame().createVisualFrame()} directly, which only instantiates a bare default
 * frame of the right class and never copies the model's own field values (colors, useGlobal, ...).
 * Only the rebind branch (an existing non-null wrapper) goes through
 * {@code VisualFrameModelFactoryService.updateVisualFrameWrapper}, which does copy them -- this is
 * exactly why the coordinator's live rebind case worked but the first-bind case did not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PasteAestheticRefFirstBindFrameTest {
   private final AestheticRefModelFactory factory = new AestheticRefModelFactory(
      new VisualFrameModelFactoryService(
         List.of(new ColorFrameModelFactory.CategoricalColorFactory())),
      new ChartRefModelFactoryService(List.of()));

   @Test
   void pastingAFrameOntoABrandNewAestheticRefPreservesTheModelsColors() {
      CategoricalColorModel frame = new CategoricalColorModel();
      frame.setColors(new String[]{"#111111", "#222222"});
      frame.setUseGlobal(false);

      AestheticInfo info = new AestheticInfo();
      info.setFrame(frame);

      // ref == null is exactly the first-bind shape: no AestheticRef existed on the channel
      // before this write, so pasteAestheticRef must fabricate one from scratch. cinfo is null
      // too -- createAestheticRef() ignores it, and model.getDataInfo() is null here so the
      // dataRef-pasting branch that would otherwise need it never runs.
      AestheticRef ref = factory.pasteAestheticRef(null, null, info);

      CategoricalColorFrame runtime =
         assertInstanceOf(CategoricalColorFrame.class, ref.getVisualFrame());
      assertFalse(runtime.isUseGlobal(),
                  "a brand-new AestheticRef must inherit the model's useGlobal setting, "
                     + "not the CategoricalColorFrame class default of true");
      assertEquals(Tool.getColorFromHexString("#111111"), runtime.getColor(0));
      assertEquals(Tool.getColorFromHexString("#222222"), runtime.getColor(1));
   }

   /**
    * The other half of the same branch, which the first-bind repair must not change. A ref that
    * already carries a wrapper, paired with a model carrying no frame, is an explicit clear and
    * has always been one — {@code createAestheticInfo} deliberately sends no frame model for the
    * text field ("textfield is edited by set and gettextformat request"), so this is the shape
    * the interactive Composer's own binding dialog produces, not a hypothetical.
    */
   @Test
   void pastingAModelWithNoFrameOntoARefThatHasOneStillClearsIt() {
      VSAestheticRef ref = new VSAestheticRef();
      ref.setVisualFrame(new CategoricalColorFrame());
      assertNotNull(ref.getVisualFrameWrapper(), "precondition: the ref carries a wrapper");

      AestheticRef pasted = factory.pasteAestheticRef(null, ref, new AestheticInfo());

      assertNull(pasted.getVisualFrameWrapper(),
                 "a model with no frame is a clear, and must stay one: the first-bind repair "
                    + "only concerns a ref that had no wrapper to begin with");
   }
}
