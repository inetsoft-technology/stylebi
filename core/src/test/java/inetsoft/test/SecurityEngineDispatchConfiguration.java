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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in test configuration that makes the shared {@code SecurityEngine} spy from
 * {@link BaseTestConfiguration} safe to "stub" from a test while production background threads
 * use it.
 *
 * <p>The spy is the instance {@code SecurityEngine.getSecurity()} returns on every thread, so
 * {@code BlobStorageEvent} listener threads, {@code MockClusterSingleton-*} threads and cluster
 * message delivery (the spy's {@code @PostConstruct} registers it as a cluster listener) call into
 * it concurrently with the tests. Mockito stubbing and {@code reset()} are not thread safe: doing
 * either on the spy while another thread invokes it can throw {@code UnfinishedStubbingException}
 * or {@code AssertionError}, or bind the stub to the other thread's method (Bugs #77168, #77336,
 * #77346).
 *
 * <p>Add this class to {@code @ContextConfiguration}, {@code @Autowired} the
 * {@link SecurityEngineOverrides} bean, and set/clear return values through it instead of
 * stubbing or resetting the spy. Never call {@code when(...)}, {@code doX().when(...)} or
 * {@code reset(...)} on the spy in such a class: a reset also removes the dispatcher.
 *
 * <p>Kept out of {@link BaseTestConfiguration} on purpose, so classes that do not opt in keep a
 * plain, unstubbed spy.
 */
@Configuration
public class SecurityEngineDispatchConfiguration {
   /**
    * Static so the post processor is registered before regular beans, including the spy.
    */
   @Bean
   public static SecurityEngineOverrides securityEngineOverrides() {
      return new SecurityEngineOverrides();
   }
}
