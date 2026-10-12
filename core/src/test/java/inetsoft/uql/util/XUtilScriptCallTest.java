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
 * The frame rule of {@link XUtil#isScriptCall()}: only {@code inetsoft.uql} and JDK code may
 * sit between the caller and the script, and Spring AOP proxy plumbing is skipped, so the
 * frames of a proxy's target decide. (Bugs #77793, #78237)
 */
@Tag("core")
class XUtilScriptCallTest {
   private static final String TRUFFLE = "com.oracle.truffle.host.HostMethodDesc$SingleMethod";
   private static final String REGISTRY = "inetsoft.uql.service.DataSourceRegistry";
   private static final String AOP_UTILS = "org.springframework.aop.support.AopUtils";
   private static final String CGLIB_INTERCEPT =
      "org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor";

   static Stream<Arguments> stacks() {
      return Stream.of(
         Arguments.of("script", true, List.of(
            REGISTRY, "inetsoft.uql.service.XEngine", "inetsoft.uql.asset.internal.AssetUtil",
            "jdk.internal.reflect.DirectMethodHandleAccessor", TRUFFLE)),
         Arguments.of("productCode", false, List.of(
            REGISTRY, "inetsoft.report.composition.execution.PhysicalBoundQuery", TRUFFLE)),
         Arguments.of("noScript", false, List.of(REGISTRY, "inetsoft.uql.service.XEngine")),
         // the @Lazy registry proxy XEngine holds (#78237)
         Arguments.of("lazyProxy", true, List.of(
            REGISTRY, AOP_UTILS, CGLIB_INTERCEPT,
            "inetsoft.uql.service.DataSourceRegistry$$SpringCGLIB$$0",
            "inetsoft.uql.service.XEngine", "inetsoft.uql.asset.internal.AssetUtil", TRUFFLE)),
         // a proxy with advice, and a JDK interface proxy
         Arguments.of("advisedProxy", true, List.of(
            REGISTRY, AOP_UTILS, "org.springframework.aop.framework.ReflectiveMethodInvocation",
            "org.springframework.aop.interceptor.DebugInterceptor", CGLIB_INTERCEPT,
            "org.springframework.cglib.proxy.MethodProxy",
            "inetsoft.uql.service.XEngine", TRUFFLE)),
         Arguments.of("jdkProxy", true, List.of(
            REGISTRY, AOP_UTILS, "org.springframework.aop.framework.JdkDynamicAopProxy",
            "jdk.proxy2.$Proxy42", "inetsoft.uql.asset.internal.AssetUtil", TRUFFLE)),
         // a proxied product bean still makes the call its own
         Arguments.of("proxiedProduct", false, List.of(
            REGISTRY, "inetsoft.uql.asset.internal.AssetUtil", "inetsoft.web.Foo",
            AOP_UTILS, CGLIB_INTERCEPT, "inetsoft.web.Foo$$SpringCGLIB$$0", TRUFFLE)),
         // other Spring code is not proxy plumbing
         Arguments.of("springOther", false, List.of(
            REGISTRY, "org.springframework.transaction.interceptor.TransactionInterceptor",
            TRUFFLE)),
         Arguments.of("aopOnly", false, List.of(REGISTRY, AOP_UTILS, CGLIB_INTERCEPT))
      );
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("stacks")
   void frameRule(String name, boolean expected, List<String> frames) {
      assertEquals(expected, XUtil.isScriptCall(frames.iterator()), name);
   }
}
