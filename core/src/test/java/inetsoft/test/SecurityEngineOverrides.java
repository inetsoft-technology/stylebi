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

package inetsoft.test;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * Per-context return-value overrides for the shared {@code SecurityEngine} spy, registered by
 * {@link SecurityEngineDispatchConfiguration}.
 *
 * <p>As a bean post processor it installs a dispatching answer on {@link #DISPATCHED_METHODS} of
 * the spy exactly once, before the spy's {@code @PostConstruct} registers it as a cluster listener
 * (hence {@code HIGHEST_PRECEDENCE}, ahead of the annotation post processor) and before any
 * dependent bean exists, i.e. before any other thread can reach it. The answer returns the
 * override set here for the invoked method, or calls the real method when none is set. Tests only
 * flip the overrides, which is thread safe, and never stub or reset the spy.
 *
 * <p>The overrides live in this bean, not in a static field, so they cannot leak into another
 * test class's context (all test classes of a module share one surefire fork).
 */
public class SecurityEngineOverrides implements BeanPostProcessor, PriorityOrdered {
   /**
    * The methods routed through the dispatcher. {@code SecurityEngine} has no overloads of them,
    * so the method name is a safe key.
    */
   public static final Set<String> DISPATCHED_METHODS =
      Set.of("getOrganizations", "getSecurityProvider", "isSecurityEnabled");

   /**
    * Makes {@code SecurityEngine.getOrganizations()} return {@code orgs}, or the real value when
    * {@code orgs} is {@code null}.
    */
   public void setOrganizations(String[] orgs) {
      set("getOrganizations", orgs);
   }

   /**
    * Makes {@code SecurityEngine.getSecurityProvider()} return {@code provider}, or the real
    * provider when {@code provider} is {@code null}.
    */
   public void setSecurityProvider(SecurityProvider provider) {
      set("getSecurityProvider", provider);
   }

   /**
    * Makes {@code SecurityEngine.isSecurityEnabled()} return {@code enabled}, or the real value
    * when {@code enabled} is {@code null}.
    */
   public void setSecurityEnabled(Boolean enabled) {
      set("isSecurityEnabled", enabled);
   }

   /**
    * Removes all overrides, so every dispatched method calls the real method again.
    */
   public void clear() {
      overrides.clear();
   }

   /**
    * Fails loudly if the dispatcher is not installed on {@code engine}, e.g. because the test
    * class's context no longer includes {@link SecurityEngineDispatchConfiguration} or the spy
    * was reset.
    */
   public static void assertInstalled(SecurityEngine engine) {
      Set<String> stubbed = new HashSet<>();
      mockingDetails(engine).getStubbings()
         .forEach(stubbing -> stubbed.add(stubbing.getInvocation().getMethod().getName()));
      assertTrue(stubbed.containsAll(DISPATCHED_METHODS),
                 "SecurityEngine dispatcher not installed, stubbed methods: " + stubbed);
   }

   @Override
   public Object postProcessBeforeInitialization(Object bean, String beanName) {
      if(bean instanceof SecurityEngine engine && mockingDetails(engine).isSpy()) {
         doAnswer(dispatch).when(engine).getOrganizations();
         doAnswer(dispatch).when(engine).getSecurityProvider();
         doAnswer(dispatch).when(engine).isSecurityEnabled();
      }

      return bean;
   }

   @Override
   public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE;
   }

   private void set(String method, Object value) {
      if(value == null) {
         overrides.remove(method);
      }
      else {
         overrides.put(method, value);
      }
   }

   private final Map<String, Object> overrides = new ConcurrentHashMap<>();

   private final Answer<Object> dispatch = inv -> {
      Object override = overrides.get(inv.getMethod().getName());
      return override != null ? override : inv.callRealMethod();
   };
}
