/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.json.lookup;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableCustomJsonLookupEndpoint.class)
public interface CustomJsonLookupEndpoint extends JsonLookupEndpoint {

   @Override
   @Value.Default
   default String endpoint() {
      return "CUSTOM";
   }

   /**
    * The address of the custom endpoint.
    * Normally it would correspond to the suffix of the address, which is appended to the data source's url.
    * If ignoreBaseUrl is on, it will be the full url address.
    */
   String url();

   /**
    * If true, the url will be the full url address instead of a suffix appended to the data source url.
    * This is used when doing lookups on url addresses.
    */
   boolean ignoreBaseURL();

   static CustomJsonLookupEndpoint.Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableCustomJsonLookupEndpoint.Builder {
   }
}
