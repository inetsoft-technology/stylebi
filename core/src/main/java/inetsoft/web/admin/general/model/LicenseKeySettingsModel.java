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
package inetsoft.web.admin.general.model;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableLicenseKeySettingsModel.class)
@JsonDeserialize(as = ImmutableLicenseKeySettingsModel.class)
public interface LicenseKeySettingsModel {
   @Nullable List<LicenseKeyModel> serverKeys();
   @Nullable List<LicenseKeyModel> schedulerKeys();
   @Nullable @AllowNulls
   Map<String, String> clusterKeys();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableLicenseKeySettingsModel.Builder {
   }
}
