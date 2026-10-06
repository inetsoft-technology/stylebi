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

package inetsoft.uql.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The frame rule of {@link XUtil#isDirectScriptCall(Class)}: only a script calling the
 * guarded class itself counts, and any product code in between, including an
 * {@code XUtil} static, makes the call a Java one. (Bug #77827)
 */
@Tag("core")
class XUtilDirectScriptCallTest {
   private static final String XUTIL = "inetsoft.uql.util.XUtil";
   private static final String CALLEE = "inetsoft.uql.asset.internal.AssetUtil";
   private static final String TRUFFLE = "com.oracle.truffle.host.HostMethodDesc";

   static Stream<Arguments> stacks() {
      return Stream.of(
         // a direct script call, with the helper's own frames
         Arguments.of("direct", true, List.of(
            XUTIL, "java.lang.StackStreamFactory", XUTIL, CALLEE,
            "jdk.internal.reflect.DirectMethodHandleAccessor",
            "java.lang.invoke.LambdaForm", TRUFFLE)),
         // no helper frames; the graalvm prefix
         Arguments.of("graalvm", true, List.of(CALLEE, "org.graalvm.polyglot.Value")),
         // XUtil.runQuery / addDescriptionsFromSource called by a script is a Java caller
         Arguments.of("xutilCaller", false, List.of(CALLEE, XUTIL, TRUFFLE)),
         Arguments.of("xutilCallerWithHelper", false, List.of(XUTIL, CALLEE, XUTIL, TRUFFLE)),
         // VSUtil.getBookmarks (#77822)
         Arguments.of("vsutilCaller", false, List.of(
            XUTIL, CALLEE, "inetsoft.uql.viewsheet.internal.VSUtil", TRUFFLE)),
         // the #77828 helpers are closed by their type denies, not by this guard
         Arguments.of("layoutToolCaller", false, List.of(
            XUTIL, CALLEE, "inetsoft.report.LayoutTool", TRUFFLE)),
         Arguments.of("javaxSkipped", true, List.of(XUTIL, CALLEE, "javax.x.Y", TRUFFLE)),
         Arguments.of("calleeSelfCall", true, List.of(XUTIL, CALLEE, CALLEE, TRUFFLE)),
         // the helper called from a class other than the callee
         Arguments.of("noCalleeFrame", false, List.of(
            XUTIL, "inetsoft.uql.viewsheet.internal.VSUtil", TRUFFLE)),
         Arguments.of("endAfterCallee", false, List.of(XUTIL, CALLEE)),
         Arguments.of("empty", false, List.<String>of()),
         // the Groovy shell DSL
         Arguments.of("groovyCaller", false, List.of(XUTIL, CALLEE, "org.codehaus.groovy.X"))
      );
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("stacks")
   void frameRule(String name, boolean expected, List<String> frames) {
      assertEquals(expected, XUtil.isDirectScriptCall(CALLEE, frames.iterator()), name);
   }
}
