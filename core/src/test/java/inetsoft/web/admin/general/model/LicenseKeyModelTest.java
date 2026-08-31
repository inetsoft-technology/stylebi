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

package inetsoft.web.admin.general.model;

import inetsoft.report.internal.license.License;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class LicenseKeyModelTest {
   // Builder.from() must carry the license's own valid() signal through to the model. Bug #76344:
   // an expired/duplicate/unparseable license was silently reported as valid: true in the EM
   // console's License Key page (and the single-key preview endpoint) because the default
   // valid() method on the interface always returns true and Builder.from() never overrode it.
   @Test
   void fromReportsInvalidLicenseAsInvalid() {
      License license = mock(License.class);
      when(license.key()).thenReturn("LICENSE-KEY");
      when(license.description()).thenReturn("Expired");
      when(license.valid()).thenReturn(false);

      LicenseKeyModel model = LicenseKeyModel.builder().from(license).build();

      assertFalse(model.valid());
   }

   @Test
   void fromReportsValidLicenseAsValid() {
      License license = mock(License.class);
      when(license.key()).thenReturn("LICENSE-KEY");
      when(license.description()).thenReturn("1 Year(s)");
      when(license.valid()).thenReturn(true);

      LicenseKeyModel model = LicenseKeyModel.builder().from(license).build();

      assertTrue(model.valid());
   }
}
