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
 * {@link #QUERY_BUILD} is the harness's own: a run reads its lens inside one worksheet query
 * build, as AssetQuery.getTableLens opens it (pool on only).
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

   /**
    * @return this configuration whose runs read their lens inside one query build.
    */
   public RelConfig inQueryBuild() {
      return with(QUERY_BUILD, "true");
   }

   /**
    * @return whether a run reads its lens inside one query build (G10 piece Q, #5932): all
    * its scripts share one claim, so an implicit global lives for the whole run.
    */
   public boolean queryBuild() {
      return pool && "true".equals(props.get(QUERY_BUILD));
   }

   public void apply() {
      SreeEnv.setProperty(PoolConfig.ENABLED, String.valueOf(pool));
      props.forEach((k, v) -> {
         if(!QUERY_BUILD.equals(k)) {
            SreeEnv.setProperty(k, v);
         }
      });
   }

   public void clear() {
      SreeEnv.remove(PoolConfig.ENABLED);
      props.keySet().stream().filter(k -> !QUERY_BUILD.equals(k)).forEach(SreeEnv::remove);
   }

   @Override
   public String toString() {
      StringBuilder str = new StringBuilder(pool ? "on" : "off");
      props.forEach((k, v) -> str.append(',').append(k.substring(k.lastIndexOf('.') + 1))
         .append('=').append(v));
      return str.toString();
   }

   /** the harness key of {@link #inQueryBuild()}, not a SreeEnv property */
   public static final String QUERY_BUILD = "rel.queryBuild";
}
