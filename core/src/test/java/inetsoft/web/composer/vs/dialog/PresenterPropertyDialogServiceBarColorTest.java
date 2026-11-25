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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.Bar2Presenter;
import inetsoft.report.painter.BarPresenter;
import inetsoft.report.painter.BulletGraphPresenter;
import inetsoft.report.painter.TrafficLightPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.BooleanDescriptorModel;
import inetsoft.web.composer.model.vs.ColorDescriptorModel;
import inetsoft.web.composer.model.vs.PresenterDescriptorModel;
import inetsoft.web.composer.model.vs.PresenterPropertyDialogModel;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * A modern table fills in a default colour on a bar presenter that has none. The dialog must not
 * show that default as the Color, or pressing OK would save it as the author's choice.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
public class PresenterPropertyDialogServiceBarColorTest {
   private static final String BAR = "inetsoft.report.painter.BarPresenter";
   private static final String BAR2 = "inetsoft.report.painter.Bar2Presenter";
   private static final String BULLET = "inetsoft.report.painter.BulletGraphPresenter";
   private static final String LIGHT = "inetsoft.report.painter.TrafficLightPresenter";
   private static final Color TINT = new Color(0x75C2FF);
   private static final Color AUTHOR = new Color(0x123456);

   @BeforeEach
   public void setup() throws Exception {
      service = new PresenterPropertyDialogService(
         engine, lifecycle, modelFactory, layoutService, imagePreview);
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(viewsheet.getAssembly(anyString())).thenReturn(table);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(table.getAbsoluteName()).thenReturn("Table1");
      when(box.getVSTableLens(anyString(), anyBoolean())).thenReturn(lens);
      when(lens.getTableDataPath(anyInt(), anyInt())).thenReturn(path);
   }

   @Test
   public void aDefaultTintTheTableFilledInIsNotShownAsTheColour() throws Exception {
      BarPresenter live = new BarPresenter(100, TINT);
      store(new PresenterRef(BAR), new PresenterPainter(5, live));

      assertEquals("", colour(model(BAR), "color"));
   }

   @Test
   public void anAuthorColourIsShown() throws Exception {
      PresenterRef ref = new PresenterRef(BAR);
      ref.setParameter("color", AUTHOR);
      store(ref, new PresenterPainter(5, new BarPresenter(100, TINT)));

      assertEquals("#" + Tool.colorToHTMLString(AUTHOR), colour(model(BAR), "color"));
   }

   @Test
   public void aSignedBarsFilledInColoursAreNotShownEither() throws Exception {
      Bar2Presenter live = new Bar2Presenter(100, TINT, new Color(0xD2A881));
      store(new PresenterRef(BAR2), new PresenterPainter(5, live));
      PresenterPropertyDialogModel model = model(BAR2);

      assertEquals("", colour(model, "color"));
      assertEquals("#" + Tool.colorToHTMLString(Bar2Presenter.DEFAULT_NEGATIVE_COLOR),
                   colour(model, "negativeColor"));
   }

   @Test
   public void aBulletGraphsFilledInBandsAreNotShown() throws Exception {
      BulletGraphPresenter live = new BulletGraphPresenter();
      live.setColor1(new Color(0x8A8F99));
      store(new PresenterRef(BULLET), new PresenterPainter(5, live));

      assertEquals("", colour(model(BULLET), "color1"));
   }

   @Test
   public void aTrafficLightsFilledInColoursShowTheStoredDefault() throws Exception {
      TrafficLightPresenter live = new TrafficLightPresenter();
      live.setColor1(new Color(0x22C55E));
      store(new PresenterRef(LIGHT), new PresenterPainter(5, live));

      assertEquals("#" + Tool.colorToHTMLString(TrafficLightPresenter.DEFAULT_GREEN),
                   colour(model(LIGHT), "color1"));
   }

   @Test
   public void aModernTableShowsAnUnsetAutoMaximumTicked() throws Exception {
      info.setVizMark(VizMark.MODERN_LIGHT);
      store(new PresenterRef(BAR), new PresenterPainter(5, new BarPresenter(100, TINT)));

      assertEquals(Boolean.TRUE, flag(model(BAR), "autoMaximum"));
   }

   @Test
   public void aModernTableKeepsAnAuthorsUntickedAutoMaximum() throws Exception {
      info.setVizMark(VizMark.MODERN_LIGHT);
      PresenterRef ref = new PresenterRef(BAR);
      ref.setParameter("autoMaximum", Boolean.FALSE);
      store(ref, new PresenterPainter(5, new BarPresenter(100, TINT)));

      assertEquals(Boolean.FALSE, flag(model(BAR), "autoMaximum"));
   }

   @Test
   public void aModernTableShowsAnUnsetBulletGraphsAutoScaleTicked() throws Exception {
      info.setVizMark(VizMark.MODERN_LIGHT);
      store(new PresenterRef(BULLET), new PresenterPainter(5, new BulletGraphPresenter()));

      assertEquals(Boolean.TRUE, flag(model(BULLET), "autoScale"));
   }

   @Test
   public void aClassicTableShowsAutoMaximumUnticked() throws Exception {
      store(new PresenterRef(BAR), new PresenterPainter(5, new BarPresenter(100, TINT)));

      assertEquals(Boolean.FALSE, flag(model(BAR), "autoMaximum"));
   }

   private static Boolean flag(PresenterPropertyDialogModel model, String name) {
      for(PresenterDescriptorModel descriptor : model.descriptors()) {
         if(name.equals(descriptor.name())) {
            return ((BooleanDescriptorModel) descriptor).value();
         }
      }

      fail("no descriptor named " + name);
      return null;
   }

   private void store(PresenterRef ref, PresenterPainter painter) {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getUserDefinedFormat().setPresenterValue(ref);
      info.getFormatInfo().setFormat(path, format);
      when(lens.getObject(anyInt(), anyInt())).thenReturn(painter);
   }

   private PresenterPropertyDialogModel model(String presenter) throws Exception {
      return service.getPresenterPropertyDialogModel(
         "Viewsheet1", "Table1", 1, 1, presenter, false, 0, null);
   }

   private static String colour(PresenterPropertyDialogModel model, String name) {
      for(PresenterDescriptorModel descriptor : model.descriptors()) {
         if(name.equals(descriptor.name())) {
            return ((ColorDescriptorModel) descriptor).value();
         }
      }

      fail("no descriptor named " + name);
      return null;
   }

   private PresenterPropertyDialogService service;
   private final TableVSAssemblyInfo info = new TableVSAssemblyInfo();
   private final TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
   @Mock ViewsheetService engine;
   @Mock CoreLifecycleService lifecycle;
   @Mock VSObjectModelFactoryService modelFactory;
   @Mock VSLayoutService layoutService;
   @Mock ImagePreviewPaneService imagePreview;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock TableVSAssembly table;
   @Mock ViewsheetSandbox box;
   @Mock VSTableLens lens;
}
