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
package inetsoft.web.binding.service.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.ColorMapModel;
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.HashMap;

public abstract class ColorFrameModelFactory<V extends ColorFrameWrapper,
   F extends ColorFrameModel> extends VisualFrameModelFactory<V, F>
{
   @Component
   public static final class StaticColorFrameFactory
      extends ColorFrameModelFactory<StaticColorFrameWrapper, StaticColorModel>
   {
      @Override
      public Class<StaticColorFrameWrapper> getVisualFrameWrapperClass() {
         return StaticColorFrameWrapper.class;
      }

      @Override
      public StaticColorModel createVisualFrameModel(StaticColorFrameWrapper wrapper) {
         return new StaticColorModel(wrapper);
      }

      @Override
      public StaticColorFrameWrapper updateVisualFrameWrapper0(
         StaticColorFrameWrapper nwrapper, StaticColorModel model)
      {
         Color color = Tool.getColorFromHexString(model.getColor());
         Color defaultColor = Tool.getColorFromHexString(model.getDefaultColor());

         if(!Tool.equals(nwrapper.getColor(), color)) {
            nwrapper.setUserColor(color);
            nwrapper.setDefaultColor(defaultColor);
         }
         
         nwrapper.setChanged(model.isChanged());
         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new StaticColorFrame();
      }
   }

   @Component
   public static final class CategoricalColorFactory
      extends ColorFrameModelFactory<CategoricalColorFrameWrapper, CategoricalColorModel>
   {
      @Override
      public Class<CategoricalColorFrameWrapper> getVisualFrameWrapperClass() {
         return CategoricalColorFrameWrapper.class;
      }

      @Override
      public CategoricalColorModel createVisualFrameModel(
         CategoricalColorFrameWrapper wrapper)
      {
         return new CategoricalColorModel(wrapper);
      }

      @Override
      public CategoricalColorFrameWrapper updateVisualFrameWrapper0(
         CategoricalColorFrameWrapper nwrapper, CategoricalColorModel model)
      {
         String[] colors = model.getColors();

         if(colors == null || colors.length == 0) {
            return nwrapper;
         }

         for(int i = 0; i < colors.length; i++) {
            Color ncolor = Tool.getColorFromHexString(colors[i]);

            if(!Tool.equals(nwrapper.getColor(i), ncolor)) {
               nwrapper.setUserColor(i, ncolor);
            }
         }

         final boolean useGlobal = model.isUseGlobal();
         final ColorMapModel[] colorMaps =
            useGlobal ? model.getGlobalColorMaps() : model.getColorMaps();
         assignMappedColors(nwrapper, nwrapper, colorMaps);
         nwrapper.setDateFormat(model.getDateFormat());
         nwrapper.setChanged(model.isChanged());
         nwrapper.setUseGlobal(useGlobal);
         nwrapper.setShareColors(model.isShareColors());
         nwrapper.setColorValueFrame(model.isColorValueFrame());
         return nwrapper;
      }

      private void assignMappedColors(CategoricalColorFrameWrapper wrapper,
                                      CategoricalColorFrameWrapper frame,
                                      ColorMapModel[] cmaps)
      {
         final HashMap<String, Color> colors = new HashMap<>();

         for(ColorMapModel colorMapModel: cmaps) {
            Color mapcolor = Tool.getColorFromHexString(colorMapModel.getColor());
            final String option = colorMapModel.getOption();
            frame.setColor(option, mapcolor);
            colors.put(option, mapcolor);
         }

         wrapper.setDimensionColors(colors);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new CategoricalColorFrame();
      }
   }

   @Component
   public static final class GradientColorFactory
      extends ColorFrameModelFactory<GradientColorFrameWrapper, GradientColorModel>
   {
      @Override
      public Class<GradientColorFrameWrapper> getVisualFrameWrapperClass() {
         return GradientColorFrameWrapper.class;
      }

      @Override
      public GradientColorModel createVisualFrameModel(
         GradientColorFrameWrapper wrapper)
      {
         return new GradientColorModel(wrapper);
      }

      @Override
      public GradientColorFrameWrapper updateVisualFrameWrapper0(
         GradientColorFrameWrapper nwrapper, GradientColorModel model)
      {
         Color fromColor = Tool.getColorFromHexString(model.getFromColor());
         Color toColor = Tool.getColorFromHexString(model.getToColor());

         if(!Tool.equals(nwrapper.getFromColor(), fromColor)) {
            nwrapper.setUserFromColor(fromColor);
         }

         if(!Tool.equals(nwrapper.getToColor(), toColor)) {
            nwrapper.setUserToColor(toColor);
         }

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new GradientColorFrame();
      }
   }

   @Component
   public static final class BrightnessColorFactory
      extends ColorFrameModelFactory<BrightnessColorFrameWrapper, BrightnessColorModel>
   {
      @Override
      public Class<BrightnessColorFrameWrapper> getVisualFrameWrapperClass() {
         return BrightnessColorFrameWrapper.class;
      }

      @Override
      public BrightnessColorModel createVisualFrameModel(
         BrightnessColorFrameWrapper wrapper)
      {
         return new BrightnessColorModel(wrapper);
      }

      @Override
      public BrightnessColorFrameWrapper updateVisualFrameWrapper0(
         BrightnessColorFrameWrapper nwrapper, BrightnessColorModel model)
      {
         Color color = Tool.getColorFromHexString(model.getColor());

         if(!Tool.equals(nwrapper.getColor(), color)) {
            nwrapper.setUserColor(color);
         }

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new BrightnessColorFrame();
      }
   }

   @Component
   public static final class SaturationColorFactory
      extends ColorFrameModelFactory<SaturationColorFrameWrapper, SaturationColorModel>
   {
      @Override
      public Class<SaturationColorFrameWrapper> getVisualFrameWrapperClass() {
         return SaturationColorFrameWrapper.class;
      }

      @Override
      public SaturationColorModel createVisualFrameModel(
         SaturationColorFrameWrapper wrapper)
      {
         return new SaturationColorModel(wrapper);
      }

      @Override
      public SaturationColorFrameWrapper updateVisualFrameWrapper0(
         SaturationColorFrameWrapper nwrapper, SaturationColorModel model)
      {
         Color color = Tool.getColorFromHexString(model.getColor());

         if(!Tool.equals(nwrapper.getColor(), color)) {
            nwrapper.setUserColor(color);
         }

         return nwrapper;
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new SaturationColorFrame();
      }
   }

   @Component
   public static final class BipolarColorFactory
      extends ColorFrameModelFactory<BipolarColorFrameWrapper, BipolarColorModel>
   {
      @Override
      public Class<BipolarColorFrameWrapper> getVisualFrameWrapperClass() {
         return BipolarColorFrameWrapper.class;
      }

      @Override
      public BipolarColorModel createVisualFrameModel(BipolarColorFrameWrapper wrapper) {
         return new BipolarColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new BipolarColorFrame();
      }
   }

   @Component
   public static final class RainbowColorFactory
      extends ColorFrameModelFactory<RainbowColorFrameWrapper, RainbowColorModel>
   {
      @Override
      public Class<RainbowColorFrameWrapper> getVisualFrameWrapperClass() {
         return RainbowColorFrameWrapper.class;
      }

      @Override
      public RainbowColorModel createVisualFrameModel(RainbowColorFrameWrapper wrapper) {
         return new RainbowColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RainbowColorFrame();
      }
   }

   @Component
   public static final class HeatColorFactory
      extends ColorFrameModelFactory<HeatColorFrameWrapper, HeatColorModel>
   {
      @Override
      public Class<HeatColorFrameWrapper> getVisualFrameWrapperClass() {
         return HeatColorFrameWrapper.class;
      }

      @Override
      public HeatColorModel createVisualFrameModel(HeatColorFrameWrapper wrapper) {
         return new HeatColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new HeatColorFrame();
      }
   }

   @Component
   public static final class CircularColorFactory
      extends ColorFrameModelFactory<CircularColorFrameWrapper, CircularColorModel>
   {
      @Override
      public Class<CircularColorFrameWrapper> getVisualFrameWrapperClass() {
         return CircularColorFrameWrapper.class;
      }

      @Override
      public CircularColorModel createVisualFrameModel(CircularColorFrameWrapper wrapper)
      {
         return new CircularColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new CircularColorFrame();
      }
   }

   @Component
   public static final class BluesColorFactory
      extends ColorFrameModelFactory<BluesColorFrameWrapper, BluesColorModel>
   {
      @Override
      public Class<BluesColorFrameWrapper> getVisualFrameWrapperClass() {
         return BluesColorFrameWrapper.class;
      }

      @Override
      public BluesColorModel createVisualFrameModel(BluesColorFrameWrapper wrapper) {
         return new BluesColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new BluesColorFrame();
      }
   }

   @Component
   public static final class BrBGColorFactory
      extends ColorFrameModelFactory<BrBGColorFrameWrapper, BrBGColorModel>
   {
      @Override
      public Class<BrBGColorFrameWrapper> getVisualFrameWrapperClass() {
         return BrBGColorFrameWrapper.class;
      }

      @Override
      public BrBGColorModel createVisualFrameModel(BrBGColorFrameWrapper wrapper) {
         return new BrBGColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new BrBGColorFrame();
      }
   }

   @Component
   public static final class BuGnColorFactory
      extends ColorFrameModelFactory<BuGnColorFrameWrapper, BuGnColorModel>
   {
      @Override
      public Class<BuGnColorFrameWrapper> getVisualFrameWrapperClass() {
         return BuGnColorFrameWrapper.class;
      }

      @Override
      public BuGnColorModel createVisualFrameModel(BuGnColorFrameWrapper wrapper) {
         return new BuGnColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new BuGnColorFrame();
      }
   }

   @Component
   public static final class BuPuColorFactory
      extends ColorFrameModelFactory<BuPuColorFrameWrapper, BuPuColorModel>
   {
      @Override
      public Class<BuPuColorFrameWrapper> getVisualFrameWrapperClass() {
         return BuPuColorFrameWrapper.class;
      }

      @Override
      public BuPuColorModel createVisualFrameModel(BuPuColorFrameWrapper wrapper) {
         return new BuPuColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new BuPuColorFrame();
      }
   }

   @Component
   public static final class GnBuColorFactory
      extends ColorFrameModelFactory<GnBuColorFrameWrapper, GnBuColorModel>
   {
      @Override
      public Class<GnBuColorFrameWrapper> getVisualFrameWrapperClass() {
         return GnBuColorFrameWrapper.class;
      }

      @Override
      public GnBuColorModel createVisualFrameModel(GnBuColorFrameWrapper wrapper) {
         return new GnBuColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new GnBuColorFrame();
      }
   }

   @Component
   public static final class GreensColorFactory
      extends ColorFrameModelFactory<GreensColorFrameWrapper, GreensColorModel>
   {
      @Override
      public Class<GreensColorFrameWrapper> getVisualFrameWrapperClass() {
         return GreensColorFrameWrapper.class;
      }

      @Override
      public GreensColorModel createVisualFrameModel(GreensColorFrameWrapper wrapper) {
         return new GreensColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new GreensColorFrame();
      }
   }

   @Component
   public static final class GreysColorFactory
      extends ColorFrameModelFactory<GreysColorFrameWrapper, GreysColorModel>
   {
      @Override
      public Class<GreysColorFrameWrapper> getVisualFrameWrapperClass() {
         return GreysColorFrameWrapper.class;
      }

      @Override
      public GreysColorModel createVisualFrameModel(GreysColorFrameWrapper wrapper) {
         return new GreysColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new GreysColorFrame();
      }
   }

   @Component
   public static final class OrangesColorFactory
      extends ColorFrameModelFactory<OrangesColorFrameWrapper, OrangesColorModel>
   {
      @Override
      public Class<OrangesColorFrameWrapper> getVisualFrameWrapperClass() {
         return OrangesColorFrameWrapper.class;
      }

      @Override
      public OrangesColorModel createVisualFrameModel(OrangesColorFrameWrapper wrapper) {
         return new OrangesColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new OrangesColorFrame();
      }
   }

   @Component
   public static final class OrRdColorFactory
      extends ColorFrameModelFactory<OrRdColorFrameWrapper, OrRdColorModel>
   {
      @Override
      public Class<OrRdColorFrameWrapper> getVisualFrameWrapperClass() {
         return OrRdColorFrameWrapper.class;
      }

      @Override
      public OrRdColorModel createVisualFrameModel(OrRdColorFrameWrapper wrapper) {
         return new OrRdColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new OrRdColorFrame();
      }
   }

   @Component
   public static final class PiYGColorFactory
      extends ColorFrameModelFactory<PiYGColorFrameWrapper, PiYGColorModel>
   {
      @Override
      public Class<PiYGColorFrameWrapper> getVisualFrameWrapperClass() {
         return PiYGColorFrameWrapper.class;
      }

      @Override
      public PiYGColorModel createVisualFrameModel(PiYGColorFrameWrapper wrapper) {
         return new PiYGColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PiYGColorFrame();
      }
   }

   @Component
   public static final class PRGnColorFactory
      extends ColorFrameModelFactory<PRGnColorFrameWrapper, PRGnColorModel>
   {
      @Override
      public Class<PRGnColorFrameWrapper> getVisualFrameWrapperClass() {
         return PRGnColorFrameWrapper.class;
      }

      @Override
      public PRGnColorModel createVisualFrameModel(PRGnColorFrameWrapper wrapper) {
         return new PRGnColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PRGnColorFrame();
      }
   }

   @Component
   public static final class PuBuColorFactory
      extends ColorFrameModelFactory<PuBuColorFrameWrapper, PuBuColorModel>
   {
      @Override
      public Class<PuBuColorFrameWrapper> getVisualFrameWrapperClass() {
         return PuBuColorFrameWrapper.class;
      }

      @Override
      public PuBuColorModel createVisualFrameModel(PuBuColorFrameWrapper wrapper) {
         return new PuBuColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PuBuColorFrame();
      }
   }

   @Component
   public static final class PuBuGnColorFactory
      extends ColorFrameModelFactory<PuBuGnColorFrameWrapper, PuBuGnColorModel>
   {
      @Override
      public Class<PuBuGnColorFrameWrapper> getVisualFrameWrapperClass() {
         return PuBuGnColorFrameWrapper.class;
      }

      @Override
      public PuBuGnColorModel createVisualFrameModel(PuBuGnColorFrameWrapper wrapper) {
         return new PuBuGnColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PuBuGnColorFrame();
      }
   }

   @Component
   public static final class PuOrColorFactory
      extends ColorFrameModelFactory<PuOrColorFrameWrapper, PuOrColorModel>
   {
      @Override
      public Class<PuOrColorFrameWrapper> getVisualFrameWrapperClass() {
         return PuOrColorFrameWrapper.class;
      }

      @Override
      public PuOrColorModel createVisualFrameModel(PuOrColorFrameWrapper wrapper) {
         return new PuOrColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PuOrColorFrame();
      }
   }

   @Component
   public static final class PuRdColorFactory
      extends ColorFrameModelFactory<PuRdColorFrameWrapper, PuRdColorModel>
   {
      @Override
      public Class<PuRdColorFrameWrapper> getVisualFrameWrapperClass() {
         return PuRdColorFrameWrapper.class;
      }

      @Override
      public PuRdColorModel createVisualFrameModel(PuRdColorFrameWrapper wrapper) {
         return new PuRdColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PuRdColorFrame();
      }
   }

   @Component
   public static final class PurplesColorFactory
      extends ColorFrameModelFactory<PurplesColorFrameWrapper, PurplesColorModel>
   {
      @Override
      public Class<PurplesColorFrameWrapper> getVisualFrameWrapperClass() {
         return PurplesColorFrameWrapper.class;
      }

      @Override
      public PurplesColorModel createVisualFrameModel(PurplesColorFrameWrapper wrapper) {
         return new PurplesColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new PurplesColorFrame();
      }
   }

   @Component
   public static final class RdBuColorFactory
      extends ColorFrameModelFactory<RdBuColorFrameWrapper, RdBuColorModel>
   {
      @Override
      public Class<RdBuColorFrameWrapper> getVisualFrameWrapperClass() {
         return RdBuColorFrameWrapper.class;
      }

      @Override
      public RdBuColorModel createVisualFrameModel(RdBuColorFrameWrapper wrapper) {
         return new RdBuColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RdBuColorFrame();
      }
   }

   @Component
   public static final class RdGyColorFactory
      extends ColorFrameModelFactory<RdGyColorFrameWrapper, RdGyColorModel>
   {
      @Override
      public Class<RdGyColorFrameWrapper> getVisualFrameWrapperClass() {
         return RdGyColorFrameWrapper.class;
      }

      @Override
      public RdGyColorModel createVisualFrameModel(RdGyColorFrameWrapper wrapper) {
         return new RdGyColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RdGyColorFrame();
      }
   }

   @Component
   public static final class RdPuColorFactory
      extends ColorFrameModelFactory<RdPuColorFrameWrapper, RdPuColorModel>
   {
      @Override
      public Class<RdPuColorFrameWrapper> getVisualFrameWrapperClass() {
         return RdPuColorFrameWrapper.class;
      }

      @Override
      public RdPuColorModel createVisualFrameModel(RdPuColorFrameWrapper wrapper) {
         return new RdPuColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RdPuColorFrame();
      }
   }

   @Component
   public static final class RdYlGnColorFactory
      extends ColorFrameModelFactory<RdYlGnColorFrameWrapper, RdYlGnColorModel>
   {
      @Override
      public Class<RdYlGnColorFrameWrapper> getVisualFrameWrapperClass() {
         return RdYlGnColorFrameWrapper.class;
      }

      @Override
      public RdYlGnColorModel createVisualFrameModel(RdYlGnColorFrameWrapper wrapper) {
         return new RdYlGnColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RdYlGnColorFrame();
      }
   }

   @Component
   public static final class RedsColorFactory
      extends ColorFrameModelFactory<RedsColorFrameWrapper, RedsColorModel>
   {
      @Override
      public Class<RedsColorFrameWrapper> getVisualFrameWrapperClass() {
         return RedsColorFrameWrapper.class;
      }

      @Override
      public RedsColorModel createVisualFrameModel(RedsColorFrameWrapper wrapper) {
         return new RedsColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RedsColorFrame();
      }
   }

   @Component
   public static final class SpectralColorFactory
      extends ColorFrameModelFactory<SpectralColorFrameWrapper, SpectralColorModel>
   {
      @Override
      public Class<SpectralColorFrameWrapper> getVisualFrameWrapperClass() {
         return SpectralColorFrameWrapper.class;
      }

      @Override
      public SpectralColorModel createVisualFrameModel(SpectralColorFrameWrapper wrapper) {
         return new SpectralColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new SpectralColorFrame();
      }
   }

   @Component
   public static final class RdYlBuColorFactory
      extends ColorFrameModelFactory<RdYlBuColorFrameWrapper, RdYlBuColorModel>
   {
      @Override
      public Class<RdYlBuColorFrameWrapper> getVisualFrameWrapperClass() {
         return RdYlBuColorFrameWrapper.class;
      }

      @Override
      public RdYlBuColorModel createVisualFrameModel(RdYlBuColorFrameWrapper wrapper) {
         return new RdYlBuColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new RdYlBuColorFrame();
      }
   }

   @Component
   public static final class YlGnBuColorFactory
      extends ColorFrameModelFactory<YlGnBuColorFrameWrapper, YlGnBuColorModel>
   {
      @Override
      public Class<YlGnBuColorFrameWrapper> getVisualFrameWrapperClass() {
         return YlGnBuColorFrameWrapper.class;
      }

      @Override
      public YlGnBuColorModel createVisualFrameModel(YlGnBuColorFrameWrapper wrapper) {
         return new YlGnBuColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new YlGnBuColorFrame();
      }
   }

   @Component
   public static final class YlGnColorFactory
      extends ColorFrameModelFactory<YlGnColorFrameWrapper, YlGnColorModel>
   {
      @Override
      public Class<YlGnColorFrameWrapper> getVisualFrameWrapperClass() {
         return YlGnColorFrameWrapper.class;
      }

      @Override
      public YlGnColorModel createVisualFrameModel(YlGnColorFrameWrapper wrapper) {
         return new YlGnColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new YlGnColorFrame();
      }
   }

   @Component
   public static final class YlOrBrColorFactory
      extends ColorFrameModelFactory<YlOrBrColorFrameWrapper, YlOrBrColorModel>
   {
      @Override
      public Class<YlOrBrColorFrameWrapper> getVisualFrameWrapperClass() {
         return YlOrBrColorFrameWrapper.class;
      }

      @Override
      public YlOrBrColorModel createVisualFrameModel(YlOrBrColorFrameWrapper wrapper) {
         return new YlOrBrColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new YlOrBrColorFrame();
      }
   }

   @Component
   public static final class YlOrRdColorFactory
      extends ColorFrameModelFactory<YlOrRdColorFrameWrapper, YlOrRdColorModel>
   {
      @Override
      public Class<YlOrRdColorFrameWrapper> getVisualFrameWrapperClass() {
         return YlOrRdColorFrameWrapper.class;
      }

      @Override
      public YlOrRdColorModel createVisualFrameModel(YlOrRdColorFrameWrapper wrapper) {
         return new YlOrRdColorModel(wrapper);
      }

      @Override
      protected VisualFrame getVisualFrame() {
         return new YlOrRdColorFrame();
      }
   }
}
