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
package inetsoft.web.security.support;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SRPrincipal;
import jakarta.servlet.Filter;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Builder for creating a {@link MockMvc} instance backed by real servlet {@link Filter}s
 * (no Spring context required).
 *
 * <p>Typical usage:
 * <pre>{@code
 * FilterTestSupport support = FilterTestSupport.builder()
 *     .withFilter(new CSRFFilter(licenseProvider, authService))
 *     .withMockedSecurityEngine(null);   // optional — intercepts SecurityEngine.getSecurity()
 * MockMvc mvc = support.build();
 * mvc.perform(post("/api/vs/data")).andExpect(status().isForbidden());
 * support.close();   // or use try-with-resources
 * }</pre>
 *
 * <p>Implements {@link AutoCloseable}: call {@link #close()} (or use try-with-resources) after
 * the test to release any open {@link MockedStatic} resources opened by
 * {@link #withMockedSecurityEngine(SRPrincipal)}.
 */
public class FilterTestSupport implements AutoCloseable {

   private final List<Filter> filters = new ArrayList<>();
   private MockedStatic<SecurityEngine> securityEngineMock;

   private FilterTestSupport() {}

   public static FilterTestSupport builder() {
      return new FilterTestSupport();
   }

   /** Adds a servlet filter that will be applied to every MockMvc request. */
   public FilterTestSupport withFilter(Filter filter) {
      filters.add(filter);
      return this;
   }

   /**
    * Mocks {@link SecurityEngine#getSecurity()} for the duration of the test so that filters
    * calling it do not NPE in a standalone (no-Spring-context) environment.
    *
    * <p>If {@code principal} is non-null the mock engine's {@code getActivePrincipalList()}
    * returns a single-element list containing that principal, simulating an already-logged-in
    * user. Otherwise the list is empty.
    *
    * <p>The {@link MockedStatic} is held open until {@link #close()} is called.
    */
   public FilterTestSupport withMockedSecurityEngine(SRPrincipal principal) {
      SecurityEngine mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      List<SRPrincipal> principalList = principal != null ? List.of(principal) : List.of();
      when(mockEngine.getActivePrincipalList()).thenReturn(principalList);

      securityEngineMock = mockStatic(SecurityEngine.class);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);
      return this;
   }

   /**
    * Builds a {@link MockMvc} instance wired with all registered filters and a minimal
    * pass-through controller that returns HTTP 200 for every path.
    */
   public MockMvc build() {
      return MockMvcBuilders.standaloneSetup(new DummyController())
         .addFilters(filters.toArray(new Filter[0]))
         .build();
   }

   /** Releases the open {@link MockedStatic} (if any). Safe to call multiple times. */
   @Override
   public void close() {
      if(securityEngineMock != null) {
         securityEngineMock.close();
         securityEngineMock = null;
      }
   }

   @RestController
   static class DummyController {
      @RequestMapping("/**")
      public String handle() {
         return "ok";
      }
   }
}
