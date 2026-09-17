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
package inetsoft.web.admin.ai.identities;

import inetsoft.sree.security.ResourceType;

/**
 * The four identity kinds this area administers (spec section 1). The discriminator every
 * {@code preview}/{@code apply} entry names explicitly, so a caller-supplied {@code spec} can be
 * validated against the right field set before any {@code SecurityService} call is made (spec
 * section 11 -- the discriminator-confusion defense named as this area's central tool-robustness
 * design point).
 */
public enum IdentityUnitType {
   USER(ResourceType.SECURITY_USER, "user"),
   GROUP(ResourceType.SECURITY_GROUP, "group"),
   ROLE(ResourceType.SECURITY_ROLE, "role"),
   ORGANIZATION(ResourceType.SECURITY_ORGANIZATION, "organization");

   IdentityUnitType(ResourceType resourceType, String label) {
      this.resourceType = resourceType;
      this.label = label;
   }

   public ResourceType resourceType() {
      return resourceType;
   }

   public String label() {
      return label;
   }

   private final ResourceType resourceType;
   private final String label;
}
