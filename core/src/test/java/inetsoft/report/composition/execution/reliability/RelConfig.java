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
package inetsoft.report.composition.execution.reliability;

import inetsoft.sree.SreeEnv;
import inetsoft.util.script.graal.pool.PoolConfig;

import java.util.*;

/**
 * One pool configuration of the reliability harness (Testing #77123): the pool on or off plus
 * {@link PoolConfig} property overrides. A sandbox reads the pool mode when it is built and
 * the tuning when it creates its env, so {@link #apply()} runs before the sandbox is built.
 */
public record RelConfig(boolean pool, Map<String, String> props) {
   public RelConfig {
      props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
   }

   public static RelConfig off() {
      return new RelConfig(false, Map.of());
   }

   public static RelConfig on() {
      return new RelConfig(true, Map.of());
   }

   public RelConfig with(String key, String value) {
      Map<String, String> map = new LinkedHashMap<>(props);
      map.put(key, value);
      return new RelConfig(pool, map);
   }

   public void apply() {
      SreeEnv.setProperty(PoolConfig.ENABLED, String.valueOf(pool));
      props.forEach(SreeEnv::setProperty);
   }

   public void clear() {
      SreeEnv.remove(PoolConfig.ENABLED);
      props.keySet().forEach(SreeEnv::remove);
   }

   @Override
   public String toString() {
      StringBuilder str = new StringBuilder(pool ? "on" : "off");
      props.forEach((k, v) -> str.append(',').append(k.substring(k.lastIndexOf('.') + 1))
         .append('=').append(v));
      return str.toString();
   }
}
