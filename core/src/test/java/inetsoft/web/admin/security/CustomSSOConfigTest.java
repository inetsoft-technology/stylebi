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
package inetsoft.web.admin.security;

/*
 * Test plan 2026-09-03, scenario 16: CustomSSOConfig.setClassName()/setInlineGroovyClass() are
 * mutually exclusive by construction -- each one clears the other as a side effect
 * (setClassName(nonNull) calls setInlineGroovyClass(null); setInlineGroovyClass(nonNull) calls
 * setClassName(null)) -- so whichever is called LAST silently wins, with no error or warning if a
 * caller sets both. SSOSettingsService.updateSSOSettings()'s CUSTOM branch always calls
 * setClassName(...) before setInlineGroovyClass(...) (see its own source), so in that real caller,
 * inline Groovy always wins if a request configures both -- the Java class name is silently
 * discarded, not rejected.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class CustomSSOConfigTest {
   @Mock
   private DataSpace dataSpace;

   private CustomSSOConfig config;
   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() {
      config = new CustomSSOConfig(dataSpace);
      sreeEnvMock = mockStatic(SreeEnv.class);
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
   }

   @Test
   void setClassNameThenInlineGroovy_matchesRealCallerOrder_groovyOverwritesClassName()
      throws Exception
   {
      // this order (setClassName then setInlineGroovyClass) is exactly what
      // SSOSettingsService.updateSSOSettings()'s CUSTOM branch does when a request configures
      // both a Java class name AND inline Groovy at once.
      lenient().when(dataSpace.exists(null, "GroovySSOFilter.groovy")).thenReturn(false);

      config.setClassName("com.example.MyCustomSSOFilter");
      config.setInlineGroovyClass("class Foo { }");

      // setInlineGroovyClass(nonNull) calls setClassName(null) internally -- the class name
      // property ends up cleared, even though it was explicitly set moments earlier.
      sreeEnvMock.verify(() -> SreeEnv.setProperty("sso.custom.class", "com.example.MyCustomSSOFilter"));
      sreeEnvMock.verify(() -> SreeEnv.setProperty(eq("sso.custom.class"), isNull()));
      verify(dataSpace).withOutputStream(eq(null), eq("GroovySSOFilter.groovy"), any());
   }

   @Test
   void setInlineGroovyThenClassName_classNameOverwritesGroovy() {
      // reverse order: the groovy file is written first (setInlineGroovyClass clears className as
      // a side effect), then setClassName() is called -- since className != null, it deletes the
      // groovy file it just wrote.
      when(dataSpace.exists(null, "GroovySSOFilter.groovy")).thenReturn(true);

      config.setInlineGroovyClass("class Foo { }");
      config.setClassName("com.example.MyCustomSSOFilter");

      sreeEnvMock.verify(() -> SreeEnv.setProperty(eq("sso.custom.class"), isNull()));
      sreeEnvMock.verify(() -> SreeEnv.setProperty("sso.custom.class", "com.example.MyCustomSSOFilter"));
      verify(dataSpace).delete(null, "GroovySSOFilter.groovy");
   }

   @Test
   void setClassNameToNull_doesNotTouchInlineGroovyFile() {
      // control: clearing the class name (className == null) must NOT trigger the
      // setInlineGroovyClass(null) side effect -- that only happens for a non-null class name.
      config.setClassName(null);

      sreeEnvMock.verify(() -> SreeEnv.setProperty("sso.custom.class", null));
      verify(dataSpace, never()).exists(any(), anyString());
      verify(dataSpace, never()).delete(any(), anyString());
   }
}
