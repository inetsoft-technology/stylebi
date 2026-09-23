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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76931 (VCA-002): {@code loadVisualFrames()}'s color/shape read used {@code isRuntime()}
 * alone to decide whether a ref should be reported. Date comparison
 * ({@code ChartDcProcessor#createAestheticRef}) installs a genuine, currently-rendered color (and,
 * via its shape-diversion branch, shape) ref exactly this way -- {@code runtime=true},
 * {@code dataRef == rtDataRef} (both set from the same object) -- so the blanket
 * {@code !isRuntime()} check hid a real binding, not stale runtime cruft. The fix narrows the
 * check: a runtime ref is still surfaced when its data ref and runtime data ref are the very same
 * object (a freshly {@code createAestheticRef}'d ref), and still hidden otherwise -- the shape
 * {@code VSChartInfo#clearDateComparisonRuntimeRef()} discards wholesale at refresh/persist time.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartAestheticServiceRuntimeColorTest {
   private final ChartAestheticService service = new ChartAestheticService();

   ChartAestheticServiceRuntimeColorTest() {
      // a fresh VSChartInfo carries default Static*FrameWrapper instances on every channel
      // (AbstractChartInfo's constructor) -- register their factories so loadVisualFrames()'s
      // frame-wrapper reads (unrelated to the color/shape ref fix under test) don't blow up.
      VisualFrameModelFactoryService vfactoryService = new VisualFrameModelFactoryService(List.of(
         new ColorFrameModelFactory.StaticColorFrameFactory(),
         new ShapeFrameModelFactory.StaticShapeFrameModelFactory(),
         new SizeFrameModelFactory.StaticSizeFrameModelFactory(),
         new LineFrameModelFactory.StaticLineFrameModelFactory(),
         new TextureFrameModelFactory.StaticTextureFrameModelFactory()));
      AestheticRefModelFactory afactoryService = new AestheticRefModelFactory(
         vfactoryService,
         new ChartRefModelFactoryService(List.of(
            new ChartDimensionInfoFactory.VSChartDimensionInfoFactory(
               new DataRefModelFactoryService(List.of())))));
      ReflectionTestUtils.setField(service, "afactoryService", afactoryService);
      ReflectionTestUtils.setField(service, "vfactoryService", vfactoryService);
   }

   private static VSChartDimensionRef periodDim() {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(new AttributeRef("Period"));
      return dim;
   }

   /**
    * The exact shape {@code ChartDcProcessor.createAestheticRef()} produces: a brand-new ref
    * installed through the ordinary design-level {@code setColorField()} setter, with
    * {@code dataRef} and {@code rtDataRef} set to the identical object.
    */
   private static VSAestheticRef dcInjectedRef(VSChartDimensionRef dim) {
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(dim);
      aref.setRTDataRef(dim);
      aref.setRuntime(true);
      return aref;
   }

   @Test
   void aDcInjectedColorRefIsNowVisible() {
      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setColorField(dcInjectedRef(periodDim()));
      ChartBindingModel model = new ChartBindingModel();

      service.loadVisualFrames(model, cinfo, cinfo);

      assertNotNull(model.getColorField(),
                     "a date-comparison-injected color ref is the chart's real, currently "
                        + "rendered binding and must not be reported as unbound");
   }

   @Test
   void aDcInjectedShapeRefIsNowVisible() {
      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setShapeField(dcInjectedRef(periodDim()));
      ChartBindingModel model = new ChartBindingModel();

      service.loadVisualFrames(model, cinfo, cinfo);

      assertNotNull(model.getShapeField(),
                     "the shape channel shares the identical isRuntime() filter and the "
                        + "identical createAestheticRef() shape (updateAestheticField's "
                        + "color-to-shape diversion branch) -- it must be fixed the same way");
   }

   /**
    * The negative case: a runtime ref whose {@code rtDataRef} has diverged from its
    * {@code dataRef} -- e.g. what a {@code .clone()} of a DC-injected ref produces, since
    * {@code AbstractAestheticRef.clone()} deep-clones {@code dataRef} but the default
    * {@code Object.clone()} it delegates to only shallow-copies {@code VSAestheticRef}'s own
    * {@code rdataRef}/{@code runtime} fields. This is the stale-runtime-cruft shape
    * {@code VSChartInfo.clearDateComparisonRuntimeRef()} exists to discard wholesale, and the
    * narrowed filter must still hide it -- a blanket "runtime ref is always visible" fix would
    * wrongly re-expose exactly this case.
    */
   @Test
   void aRuntimeRefWhoseDataRefAndRtDataRefHaveDivergedStaysHidden() {
      VSChartDimensionRef dim = periodDim();
      VSAestheticRef stale = dcInjectedRef(dim);
      VSAestheticRef clone = (VSAestheticRef) stale.clone();
      assertTrue(clone.isRuntime(), "precondition: clone() does not clear the runtime flag");
      assertNotSame(clone.getDataRef(), clone.getRTDataRef(),
                    "precondition: clone() deep-clones dataRef but not rtDataRef, so the two "
                       + "diverge -- this is the shape under test");

      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setColorField(clone);
      ChartBindingModel model = new ChartBindingModel();

      service.loadVisualFrames(model, cinfo, cinfo);

      assertNull(model.getColorField(),
                 "a runtime ref whose rtDataRef no longer matches its dataRef is stale runtime "
                    + "cruft, not the chart's real binding, and must stay hidden");
   }

   /**
    * An ordinary, non-runtime, user-bound color ref must be unaffected by the narrower check --
    * it was never filtered before, and still is not.
    */
   @Test
   void anOrdinaryDesignTimeColorRefIsUnaffected() {
      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(periodDim());
      ref.setRTDataRef(periodDim());
      assertFalse(ref.isRuntime(), "precondition: an ordinary user-bound ref is not runtime");

      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setColorField(ref);
      ChartBindingModel model = new ChartBindingModel();

      service.loadVisualFrames(model, cinfo, cinfo);

      assertNotNull(model.getColorField(), "a genuine design-time binding must still be reported");
   }
}
