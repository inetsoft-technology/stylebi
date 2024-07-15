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
package inetsoft.web.admin.content.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableAnalyzeMVRequest.class)
@JsonDeserialize(as = ImmutableAnalyzeMVRequest.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface AnalyzeMVRequest {
    @Value.Default
    default boolean expanded() {
        return false;
    }

    @Value.Default
    default boolean bypass() {
        return false;
    }

    @Value.Default
    default boolean full() {
        return false;
    }

    List<ContentRepositoryTreeNode> nodes();

    static AnalyzeMVRequest.Builder builder() {
        return new AnalyzeMVRequest.Builder();
    }

    final class Builder extends ImmutableAnalyzeMVRequest.Builder {
    }
}
