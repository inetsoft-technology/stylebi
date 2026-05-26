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
package inetsoft.web.admin.cache;

/*
 * Test strategy
 *
 * CacheManagerController is a pure REST controller that exposes cache properties as
 * individual GET/POST endpoints. Each setter uses Tool.defaultIfNull() to substitute
 * a sensible default when the incoming property has a null value field.
 *
 * Behavioral guarantees covered:
 *
 * [G1] getDataCacheSize — returns a CacheProperty named "dataCacheSize" wrapping the
 *      service value.
 * [G2] setDataCacheSize — null longValue → 0L forwarded to service.
 * [G3] setDataCacheSize — non-null longValue → that value forwarded to service.
 * [G4] setDataCacheTimeout — null longValue → 0L forwarded to service.
 * [G5] getWorksetSize — returns a CacheProperty named "worksetSize" wrapping the
 *      service value.
 * [G6] setWorksetSize — null intValue → 0 forwarded to service.
 * [G7] isDataSetCachingEnabled — returns a CacheProperty named "dataSetCachingEnabled"
 *      wrapping the boolean from the service.
 * [G8] setDataSetCachingEnabled — null booleanValue → true forwarded to service.
 * [G9] isSecurityCachingEnabled — wraps service boolean in CacheProperty named
 *      "securityCachingEnabled".
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class CacheManagerControllerTest {

   @Mock private CacheService cacheService;
   @Mock private Principal principal;

   private CacheManagerController controller;

   @BeforeEach
   void setUp() {
      controller = new CacheManagerController(cacheService);
   }

   // [G1] get wraps service value in a named CacheProperty
   @Test
   void getDataCacheSize_returnsNamedPropertyWithServiceValue() {
      when(cacheService.getDataCacheSize()).thenReturn(512L);

      CacheProperty result = controller.getDataCacheSize(principal);

      assertEquals("dataCacheSize", result.name());
      assertEquals(512L, result.longValue());
   }

   // [G2] null longValue → service receives 0L
   @Test
   void setDataCacheSize_nullValue_usesZeroDefault() throws Exception {
      CacheProperty property = CacheProperty.builder().name("dataCacheSize").build();

      controller.setDataCacheSize(property, principal);

      verify(cacheService).setDataCacheSize(0L);
   }

   // [G3] non-null longValue → service receives exact value
   @Test
   void setDataCacheSize_nonNullValue_forwardsToService() throws Exception {
      CacheProperty property = CacheProperty.builder()
         .name("dataCacheSize").longValue(256L).build();

      controller.setDataCacheSize(property, principal);

      verify(cacheService).setDataCacheSize(256L);
   }

   // [G4] null timeout → service receives 0L
   @Test
   void setDataCacheTimeout_nullValue_usesZeroDefault() throws Exception {
      CacheProperty property = CacheProperty.builder().name("dataCacheTimeout").build();

      controller.setDataCacheTimeout(property, principal);

      verify(cacheService).setDataCacheTimeout(0L);
   }

   // [G5] get wraps worksetSize integer in a named CacheProperty
   @Test
   void getWorksetSize_returnsNamedPropertyWithServiceValue() {
      when(cacheService.getWorksetSize()).thenReturn(64);

      CacheProperty result = controller.getWorksetSize(principal);

      assertEquals("worksetSize", result.name());
      assertEquals(64, result.intValue());
   }

   // [G6] null intValue → service receives 0
   @Test
   void setWorksetSize_nullValue_usesZeroDefault() throws Exception {
      CacheProperty property = CacheProperty.builder().name("worksetSize").build();

      controller.setWorksetSize(property, principal);

      verify(cacheService).setWorksetSize(0);
   }

   // [G7] get wraps boolean in a named CacheProperty
   @Test
   void isDataSetCachingEnabled_returnsNamedPropertyWithServiceValue() {
      when(cacheService.isDataSetCachingEnabled()).thenReturn(true);

      CacheProperty result = controller.isDataSetCachingEnabled(principal);

      assertEquals("dataSetCachingEnabled", result.name());
      assertTrue(result.booleanValue());
   }

   // [G8] null booleanValue → service receives true (safe default)
   @Test
   void setDataSetCachingEnabled_nullValue_usesTrueDefault() throws Exception {
      CacheProperty property = CacheProperty.builder().name("dataSetCachingEnabled").build();

      controller.setDataSetCachingEnabled(property, principal);

      verify(cacheService).setDataSetCachingEnabled(true);
   }

   // [G9] get wraps security caching boolean in a named CacheProperty
   @Test
   void isSecurityCachingEnabled_returnsNamedPropertyWithServiceValue() {
      when(cacheService.isSecurityCachingEnabled()).thenReturn(false);

      CacheProperty result = controller.isSecurityCachingEnabled(principal);

      assertEquals("securityCachingEnabled", result.name());
      assertFalse(result.booleanValue());
   }
}
