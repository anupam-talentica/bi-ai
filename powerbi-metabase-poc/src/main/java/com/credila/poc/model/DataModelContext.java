package com.credila.poc.model;

import java.util.ArrayList;
import java.util.List;

public class DataModelContext {
    private List<TableInfo> tables = new ArrayList<>();
    private List<DaxMeasure> measures = new ArrayList<>();

    public List<TableInfo> getTables() {
        return tables;
    }

    public void setTables(List<TableInfo> tables) {
        this.tables = tables;
    }

    public List<DaxMeasure> getMeasures() {
        return measures;
    }

    public void setMeasures(List<DaxMeasure> measures) {
        this.measures = measures;
    }

    public static class TableInfo {
        private String name;
        private List<ColumnInfo> columns = new ArrayList<>();

        public TableInfo() {}

        public TableInfo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ColumnInfo> getColumns() {
            return columns;
        }

        public void setColumns(List<ColumnInfo> columns) {
            this.columns = columns;
        }
    }

    public static class ColumnInfo {
        private String name;
        private String dataType;

        public ColumnInfo() {}

        public ColumnInfo(String name, String dataType) {
            this.name = name;
            this.dataType = dataType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }
    }
}
