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
package inetsoft.util.graphics;

import inetsoft.util.Encoder;
import inetsoft.util.ObjectWrapper;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Wrapper class for serializing and de-serializing an image.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ImageWrapper implements ObjectWrapper {
   public ImageWrapper() {
   }

   public ImageWrapper(Image img) {
      this.image = img;
   }

   @Override
   public Object unwrap() {
      return image;
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
      byte[] buf = (byte[]) s.readObject();

      if(buf != null) {
         int w = s.readInt();
         int h = s.readInt();

         if(w > 0 && h > 0) {
            image = Encoder.decodeImage(w, h, buf);
         }
      }
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      byte[] buf = (image != null) ? Encoder.encodeImage(image) : null;

      stream.writeObject(buf);

      if(buf != null) {
         stream.writeInt(image.getWidth(null));
         stream.writeInt(image.getHeight(null));
      }
   }

   private transient Image image;
}
