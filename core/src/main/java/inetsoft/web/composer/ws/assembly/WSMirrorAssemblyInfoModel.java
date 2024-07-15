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
package inetsoft.web.composer.ws.assembly;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.MirrorAssemblyImpl;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Date;

@Value.Immutable
@JsonSerialize(as = ImmutableWSMirrorAssemblyInfoModel.class)
public abstract class WSMirrorAssemblyInfoModel {
   @Nullable
   public abstract AssetEntry entry();

   public abstract String mirrorName();

   public abstract boolean outerMirror();

   public abstract boolean autoUpdate();

   public abstract String modified();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableWSMirrorAssemblyInfoModel.Builder {
      public ImmutableWSMirrorAssemblyInfoModel.Builder from(MirrorAssemblyImpl impl) {
         return super
            .entry(impl.getEntry())
            .mirrorName(impl.getAssemblyName())
            .outerMirror(impl.isOuterMirror())
            .autoUpdate(impl.isAutoUpdate())
            .modified(AssetUtil.getDateTimeFormat().format(new Date(impl.getLastModified())));
      }
   }
}
