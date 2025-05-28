/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.internal.cluster.ignite.serializer;

import org.apache.ignite.binary.*;

import java.awt.*;

public class RectangleSerializer implements BinarySerializer {
   @Override
   public void writeBinary(Object o, BinaryWriter binaryWriter) throws BinaryObjectException {
      Rectangle rectangle = (Rectangle) o;
      binaryWriter.writeInt("x", rectangle.x);
      binaryWriter.writeInt("y", rectangle.y);
      binaryWriter.writeInt("width", rectangle.width);
      binaryWriter.writeInt("height", rectangle.height);
   }

   @Override
   public void readBinary(Object o, BinaryReader binaryReader) throws BinaryObjectException {
      Rectangle rectangle = (Rectangle) o;
      int x = binaryReader.readInt("x");
      int y = binaryReader.readInt("y");
      int width = binaryReader.readInt("width");
      int height = binaryReader.readInt("height");
      rectangle.setBounds(x, y, width, height);
   }
}
