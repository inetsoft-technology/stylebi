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
package inetsoft.web.admin.content.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.admin.model.NameLabelTuple;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableAnalyzeMVResponse.class)
@JsonDeserialize(as = ImmutableAnalyzeMVResponse.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface AnalyzeMVResponse {
    @Value.Default
    default boolean completed() { return true; }

    @Value.Default
    default boolean exception() { return false; }

    @Nullable
    List<NameLabelTuple> cycles();

    @Value.Default
    default boolean onDemand() { return false; }

    @Nullable
    String defaultCycle();

    @Nullable
    List<MaterializedModel> status();

   @Value.Default
   default boolean runInBackground() { return false; }

    static AnalyzeMVResponse.Builder builder() {
        return new AnalyzeMVResponse.Builder();
    }

    final class Builder extends ImmutableAnalyzeMVResponse.Builder {
    }
}
