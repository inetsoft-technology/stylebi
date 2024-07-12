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
package inetsoft.web.composer.tablestyle.css;

public class TableStyleCell {
    public TableStyleCell() {
    }

    public TableStyleCell(int row, int col, Object text, String region, TableStyleFormat styleFormat) {
        this.row = row;
        this.col = col;
        this.text = text;
        this.region = region;
        this.styleFormat = styleFormat;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public Object getText() {
        return text;
    }

    public void setText(Object text) {
        this.text = text;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public TableStyleFormat getStyleFormat() {
        return styleFormat;
    }

    public void setStyleFormat(TableStyleFormat styleFormat) {
        this.styleFormat = styleFormat;
    }

    private int row;
    private int col;
    private Object text;
    private String region;
    private TableStyleFormat styleFormat;
}
