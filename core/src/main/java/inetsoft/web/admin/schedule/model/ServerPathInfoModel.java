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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.internal.Util;
import inetsoft.sree.schedule.ServerPathInfo;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link ServerPathInfoModel}
 */
@Value.Immutable
@JsonSerialize(as = ImmutableServerPathInfoModel.class)
@JsonDeserialize(as = ImmutableServerPathInfoModel.class)
public abstract class ServerPathInfoModel {
   @Nullable
   public abstract String path();

   @Nullable
   public abstract String username();

   @Nullable
   public abstract String password();

   @Nullable
   public abstract String oldPasswordKey();

   @Nullable
   public abstract String secretId();

   @Value.Default
   public boolean useCredential() {
      return false;
   }

   @Value.Default
   public boolean ftp() {
      return false;
   }


   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableServerPathInfoModel.Builder {
      public Builder from(ServerPathInfo info) {
         path(info.getPath());
         useCredential(info.isUseCredential());
         ftp(info.isFTP());

         if(info.isUseCredential()) {
            secretId(info.getSecretId());
         }
         else {
            username(info.getUsername());
            password(Util.PLACEHOLDER_PASSWORD);
         }

         return this;
      }
   }
}
