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

import java.util.List;

/**
 * The aesthetic channel vocabulary.
 *
 * <p><b>Field channels and frame channels are not the same set</b>, and this class exposes
 * that asymmetry rather than hiding it. There are field bindings for colour, shape, size and
 * text; there are frames for colour, shape, size, line and texture. {@code line} and
 * {@code texture} have frames but no field binding, so a caller asking to bind a field to
 * {@code line} gets told why instead of a shrug.
 *
 * <p>Node channels belong to relation charts (spec 2c Phase 3): {@code node-color} and
 * {@code node-size} each take both a field binding and a frame, exactly like {@code color}/
 * {@code size} — the model reuses the same {@code ColorFrameModel}/{@code SizeFrameModel} types,
 * just on a second pair of properties ({@code nodeColorField}/{@code nodeSizeField}). They are
 * kept out of {@link #FIELD_CHANNELS}/{@link #FRAME_CHANNELS} rather than merged in: the fields
 * exist on {@code ChartBindingModel} for every chart type, but only render on a relation chart,
 * so a caller working a bar or line chart must never be told they apply.
 */
public final class AestheticChannels {
   /** Channels that accept a field binding, on every chart type. */
   public static final List<String> FIELD_CHANNELS = List.of("color", "shape", "size", "text");

   /** Channels that accept a visual frame, on every chart type. */
   public static final List<String> FRAME_CHANNELS =
      List.of("color", "shape", "size", "line", "texture");

   /** Frame channels that can be written. Every frame channel is supported since 2c Phase 2. */
   public static final List<String> SUPPORTED_FRAME_CHANNELS = FRAME_CHANNELS;

   /** Field+frame channels valid only when the target chart is a relation type. */
   public static final List<String> NODE_CHANNELS = List.of("node-color", "node-size");

   private AestheticChannels() {
   }

   public static String normalize(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase();
   }

   public static String requireFieldChannel(String channel) {
      return requireFieldChannel(channel, false);
   }

   /**
    * @param relationChart whether the target chart is a relation type (network, tree, chord).
    *                      Gates {@link #NODE_CHANNELS} — accepting one elsewhere would store a
    *                      binding that this chart type never renders.
    */
   public static String requireFieldChannel(String channel, boolean relationChart) {
      String name = normalize(channel);

      if(NODE_CHANNELS.contains(name)) {
         if(relationChart) {
            return name;
         }

         throw new IllegalArgumentException(
            "Channel '" + channel + "' only applies to relation charts (network, tree, chord) " +
            "— this chart is not one. Field channels: " + String.join(", ", FIELD_CHANNELS) + ".");
      }

      if(FIELD_CHANNELS.contains(name)) {
         return name;
      }

      if(FRAME_CHANNELS.contains(name)) {
         throw new IllegalArgumentException(
            "Channel '" + channel + "' takes a visual frame but no field binding. Use " +
            "set_visual_frame for it. Field channels: " + String.join(", ", FIELD_CHANNELS) + ".");
      }

      throw new IllegalArgumentException(
         "Unknown aesthetic channel '" + channel + "'. Field channels: " +
         String.join(", ", FIELD_CHANNELS) + ".");
   }

   public static String requireFrameChannel(String channel) {
      return requireFrameChannel(channel, false);
   }

   /** @param relationChart see {@link #requireFieldChannel(String, boolean)}. */
   public static String requireFrameChannel(String channel, boolean relationChart) {
      String name = normalize(channel);

      if(NODE_CHANNELS.contains(name)) {
         if(relationChart) {
            return name;
         }

         throw new IllegalArgumentException(
            "Channel '" + channel + "' only applies to relation charts (network, tree, chord) " +
            "— this chart is not one. Frame channels: " + String.join(", ", FRAME_CHANNELS) + ".");
      }

      if(SUPPORTED_FRAME_CHANNELS.contains(name)) {
         return name;
      }

      if(FRAME_CHANNELS.contains(name)) {
         throw new IllegalArgumentException(
            "Channel '" + channel + "' has visual frames, but only " +
            String.join(", ", SUPPORTED_FRAME_CHANNELS) + " frames are supported yet.");
      }

      if(FIELD_CHANNELS.contains(name)) {
         throw new IllegalArgumentException(
            "Channel '" + channel + "' accepts a field binding but has no visual frame. Use " +
            "set_aesthetic_field for it.");
      }

      throw new IllegalArgumentException(
         "Unknown aesthetic channel '" + channel + "'. Frame channels: " +
         String.join(", ", FRAME_CHANNELS) + ".");
   }
}
