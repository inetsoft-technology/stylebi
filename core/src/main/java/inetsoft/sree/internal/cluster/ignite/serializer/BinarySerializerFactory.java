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

import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;

import java.util.*;

public interface BinarySerializerFactory {
   Class<?> getSerializedType();

   BinarySerializer createSerializer();

   static void registerSerializers(IgniteConfiguration config) {
      List<BinaryTypeConfiguration> types = new ArrayList<>();

      for(BinarySerializerFactory factory : ServiceLoader.load(BinarySerializerFactory.class)) {
         BinaryTypeConfiguration typeConfig = new BinaryTypeConfiguration();
         typeConfig.setTypeName(factory.getSerializedType().getName());
         typeConfig.setSerializer(factory.createSerializer());
         types.add(typeConfig);
      }

      BinaryConfiguration binaryConfig = new BinaryConfiguration();
      binaryConfig.setTypeConfigurations(types);
      config.setBinaryConfiguration(binaryConfig);
   }
}
