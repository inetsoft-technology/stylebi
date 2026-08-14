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
 * <p>Node channels belong to relation charts and arrive with spec 2c Phase 3.
 */
public final class AestheticChannels {
   /** Channels that accept a field binding. */
   public static final List<String> FIELD_CHANNELS = List.of("color", "shape", "size", "text");

   /** Channels that accept a visual frame. */
   public static final List<String> FRAME_CHANNELS =
      List.of("color", "shape", "size", "line", "texture");

   /** Frame channels this phase can write. The rest arrive in Phase 2. */
   public static final List<String> SUPPORTED_FRAME_CHANNELS = List.of("color");

   private AestheticChannels() {
   }

   public static String normalize(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase();
   }

   public static String requireFieldChannel(String channel) {
      String name = normalize(channel);

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
      String name = normalize(channel);

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
