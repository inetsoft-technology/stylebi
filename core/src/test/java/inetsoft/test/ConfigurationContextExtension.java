/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.extension.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * JUnit 5 extension that sets up a Mockito spy on {@link ConfigurationContext} before each test
 * and tears it down after. Use alongside {@code @SreeHome} and {@code MockitoExtension}.
 *
 * <p>Usage:
 * <pre>{@code
 * @SreeHome()
 * @ExtendWith({MockitoExtension.class, ConfigurationContextExtension.class})
 * class MyControllerTest {
 *    @BeforeEach
 *    void setup() {
 *       ConfigurationContext spyContext = ConfigurationContextExtension.getSpyContext();
 *       doReturn(myService).when(spyContext).getSpringBean(MyService.class);
 *    }
 * }
 * }</pre>
 *
 * <p>This eliminates the repeated boilerplate previously found in every controller test:
 * <pre>{@code
 *    ConfigurationContext context = ConfigurationContext.getContext();
 *    ConfigurationContext spyContext = Mockito.spy(context);
 *    staticConfigurationContext = Mockito.mockStatic(ConfigurationContext.class);
 *    staticConfigurationContext.when(ConfigurationContext::getContext).thenReturn(spyContext);
 * }</pre>
 */
public class ConfigurationContextExtension implements BeforeEachCallback, AfterEachCallback {

   private static final ThreadLocal<ConfigurationContext> SPY_CONTEXT = new ThreadLocal<>();
   private static final ThreadLocal<MockedStatic<ConfigurationContext>> STATIC_MOCK =
      new ThreadLocal<>();

   @Override
   public void beforeEach(ExtensionContext context) {
      ConfigurationContext actual = ConfigurationContext.getContext();
      ConfigurationContext spy = Mockito.spy(actual);
      MockedStatic<ConfigurationContext> staticMock = Mockito.mockStatic(ConfigurationContext.class);
      staticMock.when(ConfigurationContext::getContext).thenReturn(spy);
      SPY_CONTEXT.set(spy);
      STATIC_MOCK.set(staticMock);
   }

   @Override
   public void afterEach(ExtensionContext context) {
      MockedStatic<ConfigurationContext> staticMock = STATIC_MOCK.get();

      if(staticMock != null) {
         staticMock.close();
         STATIC_MOCK.remove();
      }

      SPY_CONTEXT.remove();
   }

   /**
    * Returns the Mockito spy on {@link ConfigurationContext} installed for the current test.
    * Call this from your {@code @BeforeEach} to configure which Spring beans the context returns.
    *
    * @return the spy; never {@code null} when called from a test managed by this extension
    */
   public static ConfigurationContext getSpyContext() {
      return SPY_CONTEXT.get();
   }
}
