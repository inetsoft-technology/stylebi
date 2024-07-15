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
package inetsoft.uql.viewsheet.internal;

import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import java.awt.image.BufferedImage;

/**
 * This class handles svg image conversion.
 * It is primarily used for getting bufferedImage from svg input stream.
 *
 * @version 12.3, 4/10/2018
 * @author InetSoft Technology Corp
 */
public class SVGImageTranscoder extends ImageTranscoder {
   @Override
   public BufferedImage createImage(int w, int h) {
      image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      return image;
   }

   @Override
   public void writeImage(BufferedImage img, TranscoderOutput out) {
   }

   public BufferedImage getImage() {
      return image;
   }

   private BufferedImage image = null;
}
