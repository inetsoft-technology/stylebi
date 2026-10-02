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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.graal.ScriptHostAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;

/**
 * The HostAccess of pooled worksheet contexts (bug #76960, spec §6.8, §14.4, §14.12): the
 * common script policy plus copies for Java method arguments typed Object/Map/List, so what a
 * worksheet script hands a Java method never stays bound to its context. One static instance:
 * every Context on SHARED_WS_ENGINE must use the identical HostAccess (proto check 2).
 */
final class WsHostAccess {
   private WsHostAccess() {
   }

   static HostAccess get() {
      return INSTANCE;
   }

   private static <T> T rejected(Value v) {
      throw WsValueCopier.reject(v);
   }

   private static final HostAccess INSTANCE = HostAccess.newBuilder(ScriptHostAccess.hostAccess())
      // a foreign reference is its owner's live value, never a copy (spec §14.11)
      .<Value, Object>targetTypeMapping(Value.class, Object.class, WsValueCopier::isForeignRef,
                                        v -> WsValueCopier.foreignAs(v, Object.class),
                                        HostAccess.TargetMappingPrecedence.HIGHEST)
      .<Value, Map>targetTypeMapping(Value.class, Map.class, WsValueCopier::isForeignRef,
                                     v -> WsValueCopier.foreignAs(v, Map.class),
                                     HostAccess.TargetMappingPrecedence.HIGHEST)
      .<Value, List>targetTypeMapping(Value.class, List.class, WsValueCopier::isForeignRef,
                                      v -> WsValueCopier.foreignAs(v, List.class),
                                      HostAccess.TargetMappingPrecedence.HIGHEST)
      .<Value, Object>targetTypeMapping(Value.class, Object.class, WsValueCopier::isDate,
                                        WsValueCopier::toDate,
                                        HostAccess.TargetMappingPrecedence.HIGH)
      .<Value, Object>targetTypeMapping(Value.class, Object.class, WsValueCopier::isFunction,
                                        WsHostAccess::rejected)
      .<Value, Object>targetTypeMapping(Value.class, Object.class, WsValueCopier::isNonPlainObject,
                                        WsHostAccess::rejected)
      .<Value, Object>targetTypeMapping(Value.class, Object.class, WsValueCopier::isPlainObject,
                                        WsValueCopier::copyMap)
      .<Value, Object>targetTypeMapping(Value.class, Object.class, WsValueCopier::isArray,
                                        WsValueCopier::copyList)
      .<Value, Map>targetTypeMapping(Value.class, Map.class, WsValueCopier::isPlainObject,
                                     WsValueCopier::copyMap)
      .<Value, Map>targetTypeMapping(Value.class, Map.class,
                                     v -> WsValueCopier.isArray(v) || WsValueCopier.isFunction(v) ||
                                        WsValueCopier.isNonPlainObject(v),
                                     WsHostAccess::rejected)
      .<Value, List>targetTypeMapping(Value.class, List.class, WsValueCopier::isArray,
                                      WsValueCopier::copyList)
      .<Value, List>targetTypeMapping(Value.class, List.class,
                                      v -> WsValueCopier.isFunction(v) ||
                                         WsValueCopier.isNonPlainObject(v),
                                      WsHostAccess::rejected)
      .build();
}
