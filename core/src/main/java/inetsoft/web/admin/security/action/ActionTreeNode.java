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
package inetsoft.web.admin.security.action;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableActionTreeNode.class)
@JsonDeserialize(as = ImmutableActionTreeNode.class)
public interface ActionTreeNode {
   @Nullable
   String resource();

   String label();

   boolean folder();

   @Value.Default
   default boolean grant() {
      return false;
   }

   @Nullable
   ResourceType type();

   EnumSet<ResourceAction> actions();

   List<ActionTreeNode> children();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableActionTreeNode.Builder {
      boolean orgAdminFlag = false;

      @CanIgnoreReturnValue
      public ActionTreeNode.Builder orgAdmin(boolean orgAdmin) {
         orgAdminFlag = orgAdmin;
         return this;
      }

      @CanIgnoreReturnValue
      public ActionTreeNode.Builder addFilteredChildren(ActionTreeNode element) {
         if(orgAdminFlag) {
            if(isEmptyFolderNode(element)) {
               return this;
            }
            else if(element.resource() != null) {
               if(!ActionPermissionService.isOrgAdminAction(element.type(), element.resource())) {
                  return this;
               }
            }
         }

         this.addChildren(element);
         return this;
      }

      private boolean isEmptyFolderNode(ActionTreeNode element) {
         if(element.folder()) {
            for(ActionTreeNode child : element.children()) {
               if(!isEmptyFolderNode(child)) {
                  return false;
               }
            }

            return true;
         }

         return false;
      }

      @CanIgnoreReturnValue
      public ActionTreeNode.Builder addFilteredChildren(ActionTreeNode... elements) {
         for(ActionTreeNode element : elements) {
            this.addFilteredChildren(element);
         }

         return this;
      }
   }
}
