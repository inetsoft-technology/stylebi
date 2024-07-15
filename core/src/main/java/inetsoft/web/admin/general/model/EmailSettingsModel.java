/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.general.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableEmailSettingsModel.class)
@JsonDeserialize(as = ImmutableEmailSettingsModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface EmailSettingsModel {
   @Nullable
   String smtpHost();

   boolean ssl();

   boolean tls();

   @Nullable
   String jndiUrl();

   boolean smtpAuthentication();

   @Nullable
   String smtpUser();

   @Nullable
   String smtpPassword();

   @Value.Default
   default boolean historyEnabled() {
      return false;
   }

   @Value.Default
   default boolean fromAddressEnabled() { return false; }

   String fromAddress();

   @Nullable
   String deliveryMailSubjectFormat();

   @Nullable
   String notificationMailSubjectFormat();

   static EmailSettingsModel.Builder builder() {
      return new EmailSettingsModel.Builder();
   }

   final class Builder extends ImmutableEmailSettingsModel.Builder {

   }
}
