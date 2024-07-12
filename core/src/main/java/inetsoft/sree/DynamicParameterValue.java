/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.sree;

import inetsoft.web.composer.model.vs.DynamicValueModel;

import java.io.Serializable;

public class DynamicParameterValue implements Serializable, Cloneable {
    public DynamicParameterValue() {
    }

    public DynamicParameterValue(Object value, String type, String dataType) {
        this.value = value;
        this.type = type;
        this.dataType = dataType;
    }

    public DynamicParameterValue(Object value, String dataType) {
        this.value = value;
        this.dataType = dataType;
    }

    public DynamicValueModel convertModel() {
        return new DynamicValueModel(this.value, this.type, this.dataType);
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    private Object value;
    private String type = DynamicValueModel.VALUE;
    private String dataType;
}
