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

package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.ignite.serializer.Object2ObjectOpenHashMapSerializer;
import inetsoft.sree.internal.cluster.ignite.serializer.RectangleSerializer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class IgniteUtils {
   public static void configBinaryTypes(IgniteConfiguration config) {
      BinaryConfiguration binaryCfg = new BinaryConfiguration();
      binaryCfg.setTypeConfigurations(getBinaryTypeConfigurations());
      config.setBinaryConfiguration(binaryCfg);
   }

   private static List<BinaryTypeConfiguration> getBinaryTypeConfigurations() {
      List<BinaryTypeConfiguration> binaryTypeConfigurations = new ArrayList<>();

      BinaryTypeConfiguration typeCfg = new BinaryTypeConfiguration();
      typeCfg.setTypeName(Object2ObjectOpenHashMap.class.getName());
      typeCfg.setSerializer(new Object2ObjectOpenHashMapSerializer());
      binaryTypeConfigurations.add(typeCfg);

      typeCfg = new BinaryTypeConfiguration();
      typeCfg.setTypeName(Rectangle.class.getName());
      typeCfg.setSerializer(new RectangleSerializer());
      binaryTypeConfigurations.add(typeCfg);

      return binaryTypeConfigurations;
   }
}
