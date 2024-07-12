/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
