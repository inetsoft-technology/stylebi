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
package inetsoft.web.admin.ai.recyclebin;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for {@code GET /api/wiz/v1/admin/recycle-bin/entry}. {@code found: false} is a
 * normal 200 answer, not an error -- matching {@code get_viewsheet_folder}/{@code
 * get_permission_grant}'s own precedent (track-a-recycle-bin/01-design.md section 1) -- in which
 * case every field below {@code found} is {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetRecycleBinEntryResult(boolean found, String path, String originalPath,
                                       String originalName, String originalType,
                                       String originalScope, String originalOwner,
                                       String timestamp)
{
   public static GetRecycleBinEntryResult notFound() {
      return new GetRecycleBinEntryResult(false, null, null, null, null, null, null, null);
   }

   public static GetRecycleBinEntryResult of(RecycleBinEntryProjection projection) {
      return new GetRecycleBinEntryResult(true, projection.path(), projection.originalPath(),
         projection.originalName(), projection.originalType(), projection.originalScope(),
         projection.originalOwner(), projection.timestamp());
   }
}
