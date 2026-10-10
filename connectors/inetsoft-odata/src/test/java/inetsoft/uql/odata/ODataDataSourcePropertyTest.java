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
package inetsoft.uql.odata;

import inetsoft.uql.tabular.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78242: ODataDataSource overrides the OAuthDataSource default accessors of user, password,
 * accessToken, refreshToken and tokenExpiration, and @Property was declared only on the interface
 * defaults. Since JDK-8347826 (OpenJDK 21.0.9+, 17.0.18+) java.beans.Introspector reports the
 * class's own override as the read method instead of the interface default, so PropertyMeta saw
 * no @Property, the properties dropped out of TabularUtil.getPropertyMap(), and
 * LayoutCreator.createLayout() threw "Property missing for view: user" (a 500 on the data source
 * listing and editor).
 */
class ODataDataSourcePropertyTest {
   /**
    * JDK-independent: the PropertyDescriptor is built from the class's own declared override, which
    * is what Introspector returns on JDK 21.0.9 and later, regardless of the JDK running the test.
    */
   @ParameterizedTest
   @CsvSource({
      "user, getUser, setUser, User, false, true",
      "password, getPassword, setPassword, Password, true, true",
      "accessToken, getAccessToken, setAccessToken, Access Token, true, false",
      "refreshToken, getRefreshToken, setRefreshToken, Refresh Token, true, false",
      "tokenExpiration, getTokenExpiration, setTokenExpiration, Token Expiration, false, false"
   })
   void ownOverrideCarriesPropertyAnnotation(String name, String getterName, String setterName,
                                             String label, boolean password, boolean enabled)
      throws Exception
   {
      Method getter = ODataDataSource.class.getDeclaredMethod(getterName);
      Method setter = ODataDataSource.class.getDeclaredMethod(
         setterName, getter.getReturnType());
      PropertyMeta meta = new PropertyMeta(new PropertyDescriptor(name, getter, setter));

      assertTrue(meta.isAnnotated(),
                 "ODataDataSource." + getterName + "() must declare @Property itself");
      assertEquals(label, meta.getProperty().label());
      assertEquals(password, meta.getProperty().password());
      assertNotNull(meta.getEditor(), getterName + "() must declare @PropertyEditor");
      assertEquals(enabled, meta.getEditor().enabled());
   }

   /**
    * Every COMPONENT/EDITOR property referenced by the @View must be in the property map, or
    * LayoutCreator throws "Property missing for view". Note: this only catches the regression on
    * a JDK that includes JDK-8347826 (21.0.9+); on older JDKs Introspector returns the annotated
    * interface default and this passes even without the fix.
    */
   @Test
   void viewPropertiesAreInPropertyMap() {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(ODataDataSource.class);
      View view = ODataDataSource.class.getAnnotation(View.class);
      assertNotNull(view);
      List<String> missing = new ArrayList<>();

      for(View1 elem : view.value()) {
         if((elem.type() == ViewType.COMPONENT || elem.type() == ViewType.EDITOR) &&
            !elem.value().isEmpty() && !pmap.containsKey(elem.value()))
         {
            missing.add(elem.value());
         }
      }

      assertEquals(Collections.emptyList(), missing,
                   "Java " + System.getProperty("java.version"));
   }
}
