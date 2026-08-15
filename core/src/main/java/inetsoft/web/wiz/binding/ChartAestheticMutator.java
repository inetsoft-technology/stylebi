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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.BindingRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.aesthetic.*;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-modify-write over the thirteen aesthetic fields in {@link ChartBindingFields#AESTHETIC}.
 *
 * <p>The mirror image of {@link ChartBindingMutator}. {@code changeChartAesthetic} posts the
 * same {@code ChangeChartRefEvent} carrying the entire {@code ChartBindingModel}, so the
 * preservation obligation runs both ways: 2b must not disturb these fields, and this class
 * must not disturb the shelves, chart type or options. Callers must pass the model
 * {@code VSBindingService.createModel} returned and mutate it in place — a fresh model would
 * silently drop everything neither class sets.
 */
public final class ChartAestheticMutator {
   private ChartAestheticMutator() {
   }

   /** Binds a field to a channel. Replaces whatever that channel held; leaves the rest alone. */
   public static void setField(ChartBindingModel model, String channel, FieldRef field) {
      String name = AestheticChannels.requireFieldChannel(channel);

      if(field == null) {
         throw new IllegalArgumentException(
            "Binding the " + name + " channel needs a field. To unbind it, use " +
            "clear_aesthetic_field instead — an absent field is not the same request as an " +
            "explicit clear.");
      }

      AestheticInfo info = new AestheticInfo();
      info.setFullName(field.column());
      info.setDataInfo(FieldRefFactory.toChartRef(field));
      assign(model, name, info);
   }

   /** Unbinds a channel. */
   public static void clearField(ChartBindingModel model, String channel) {
      assign(model, AestheticChannels.requireFieldChannel(channel), null);
   }

   /** Sets a channel's visual frame from the agent vocabulary. */
   public static void setFrame(ChartBindingModel model, String channel,
                               Map<String, Object> spec)
   {
      String name = AestheticChannels.requireFrameChannel(channel);
      VisualFrameModel frame = VisualFrameAliases.create(name, spec);

      switch(name) {
         case "color" -> model.setColorFrame((ColorFrameModel) frame);
         case "shape" -> model.setShapeFrame((ShapeFrameModel) frame);
         case "size" -> model.setSizeFrame((SizeFrameModel) frame);
         case "line" -> model.setLineFrame((LineFrameModel) frame);
         case "texture" -> model.setTextureFrame((TextureFrameModel) frame);
         default -> throw new IllegalArgumentException("Unhandled frame channel '" + name + "'.");
      }
   }

   /**
    * Every channel the vocabulary knows, with what it currently holds. Channels are reported
    * even when unbound, so an agent reading a chart learns what it *could* set rather than
    * only what someone already set.
    */
   public static Map<String, Object> describe(ChartBindingModel model) {
      Map<String, Object> out = new LinkedHashMap<>();

      for(String channel : AestheticChannels.FIELD_CHANNELS) {
         out.put(channel, channelView(model, channel));
      }

      for(String channel : AestheticChannels.FRAME_CHANNELS) {
         out.computeIfAbsent(channel, name -> channelView(model, name));
      }

      return out;
   }

   private static Map<String, Object> channelView(ChartBindingModel model, String channel) {
      Map<String, Object> view = new LinkedHashMap<>();
      AestheticInfo info = AestheticChannels.FIELD_CHANNELS.contains(channel)
         ? read(model, channel) : null;

      view.put("field", fieldNameOf(info));
      view.put("frame", VisualFrameAliases.describe(frameOf(model, channel)));
      view.put("acceptsField", AestheticChannels.FIELD_CHANNELS.contains(channel));
      view.put("acceptsFrame", AestheticChannels.FRAME_CHANNELS.contains(channel));
      return view;
   }

   /**
    * The name of the field bound to a channel.
    *
    * <p>Read from the {@code dataInfo}, not from {@link AestheticInfo#getFullName()}. The read
    * path — {@code AestheticRefModelFactory.createAestheticInfo} — sets only {@code dataInfo} and
    * {@code frame}; nothing there ever sets the {@code AestheticInfo}'s own {@code fullName}. The
    * only writer of that field is {@link #setField}, ours. So asking the {@code AestheticInfo} for
    * its name reported {@code null} for every channel of every chart, while the aesthetic was
    * visibly rendering — the write worked, the read lied.
    *
    * <p>{@code fullName} is kept only as a fallback, for the models our own writer built.
    */
   private static String fieldNameOf(AestheticInfo info) {
      if(info == null) {
         return null;
      }

      BindingRefModel dataInfo = info.getDataInfo();

      if(dataInfo != null && dataInfo.getFullName() != null) {
         return dataInfo.getFullName();
      }

      return info.getFullName();
   }

   private static VisualFrameModel frameOf(ChartBindingModel model, String channel) {
      return switch(channel) {
         case "color" -> model.getColorFrame();
         case "shape" -> model.getShapeFrame();
         case "size" -> model.getSizeFrame();
         case "line" -> model.getLineFrame();
         case "texture" -> model.getTextureFrame();
         default -> null;
      };
   }

   private static AestheticInfo read(ChartBindingModel model, String channel) {
      return switch(channel) {
         case "color" -> model.getColorField();
         case "shape" -> model.getShapeField();
         case "size" -> model.getSizeField();
         case "text" -> model.getTextField();
         default -> null;
      };
   }

   private static void assign(ChartBindingModel model, String channel, AestheticInfo info) {
      switch(channel) {
         case "color" -> model.setColorField(info);
         case "shape" -> model.setShapeField(info);
         case "size" -> model.setSizeField(info);
         case "text" -> model.setTextField(info);
         default -> throw new IllegalArgumentException("Unhandled field channel '" + channel + "'.");
      }
   }
}
