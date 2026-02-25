package io.heluna.vm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class HVal {

    public static final byte TYPE_STRING  = 0x01;
    public static final byte TYPE_INTEGER = 0x02;
    public static final byte TYPE_FLOAT   = 0x03;
    public static final byte TYPE_BOOLEAN = 0x04;
    public static final byte TYPE_NOTHING = 0x05;
    public static final byte TYPE_MAYBE   = 0x06;
    public static final byte TYPE_LIST    = 0x07;
    public static final byte TYPE_RECORD  = 0x08;

    public abstract byte typeCode();

    public boolean isNothing() {
        return false;
    }

    // --- Concrete subclasses ---

    public static final class HInteger extends HVal {
        private static final int CACHE_LOW = -128;
        private static final int CACHE_HIGH = 10000;
        private static final HInteger[] CACHE = new HInteger[CACHE_HIGH - CACHE_LOW + 1];
        static {
            for (int i = 0; i < CACHE.length; i++) {
                CACHE[i] = new HInteger(i + CACHE_LOW);
            }
        }

        public static HInteger of(long value) {
            if (value >= CACHE_LOW && value <= CACHE_HIGH) {
                return CACHE[(int) value - CACHE_LOW];
            }
            return new HInteger(value);
        }

        private final long value;

        public HInteger(long value) {
            this.value = value;
        }

        public long value() { return value; }

        @Override public byte typeCode() { return TYPE_INTEGER; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof HInteger) return value == ((HInteger) o).value;
            if (o instanceof HFloat) return (double) value == ((HFloat) o).value;
            return false;
        }

        @Override public int hashCode() {
            // Must be consistent with HFloat for cross-type equality
            if (value == (double) value) {
                return Double.hashCode((double) value);
            }
            return Long.hashCode(value);
        }

        @Override public String toString() { return Long.toString(value); }
    }

    public static final class HFloat extends HVal {
        private final double value;

        public HFloat(double value) {
            this.value = value;
        }

        public double value() { return value; }

        @Override public byte typeCode() { return TYPE_FLOAT; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof HFloat) return Double.compare(value, ((HFloat) o).value) == 0;
            if (o instanceof HInteger) return value == (double) ((HInteger) o).value;
            return false;
        }

        @Override public int hashCode() { return Double.hashCode(value); }

        @Override
        public String toString() {
            if (value == Math.floor(value) && !Double.isInfinite(value)) {
                long lv = (long) value;
                return lv + ".0";
            }
            return Double.toString(value);
        }
    }

    public static final class HString extends HVal {
        private final String value;

        public HString(String value) {
            this.value = Objects.requireNonNull(value);
        }

        public String value() { return value; }

        @Override public byte typeCode() { return TYPE_STRING; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof HString) return value.equals(((HString) o).value);
            return false;
        }

        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    public static final class HBoolean extends HVal {
        public static final HBoolean TRUE = new HBoolean(true);
        public static final HBoolean FALSE = new HBoolean(false);

        private final boolean value;

        private HBoolean(boolean value) {
            this.value = value;
        }

        public static HBoolean of(boolean value) {
            return value ? TRUE : FALSE;
        }

        public boolean value() { return value; }

        @Override public byte typeCode() { return TYPE_BOOLEAN; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof HBoolean) return value == ((HBoolean) o).value;
            return false;
        }

        @Override public int hashCode() { return Boolean.hashCode(value); }
        @Override public String toString() { return Boolean.toString(value); }
    }

    public static final class HNothing extends HVal {
        public static final HNothing INSTANCE = new HNothing();

        private HNothing() {}

        @Override public byte typeCode() { return TYPE_NOTHING; }
        @Override public boolean isNothing() { return true; }

        @Override public boolean equals(Object o) { return o instanceof HNothing; }
        @Override public int hashCode() { return 0; }
        @Override public String toString() { return "nothing"; }
    }

    public static final class HList extends HVal {
        private final ArrayList<HVal> elements;

        public HList() {
            this.elements = new ArrayList<>();
        }

        public HList(int initialCapacity) {
            this.elements = new ArrayList<>(initialCapacity);
        }

        public HList(List<HVal> elements) {
            this.elements = new ArrayList<>(elements);
        }

        public void add(HVal value) {
            elements.add(value);
        }

        public HVal get(int index) {
            if (index < 0 || index >= elements.size()) {
                return HNothing.INSTANCE;
            }
            return elements.get(index);
        }

        public int size() { return elements.size(); }

        public ArrayList<HVal> elements() { return elements; }

        @Override public byte typeCode() { return TYPE_LIST; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof HList) return elements.equals(((HList) o).elements);
            return false;
        }

        @Override public int hashCode() { return elements.hashCode(); }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) sb.append(", ");
                HVal v = elements.get(i);
                if (v instanceof HString) {
                    sb.append('"').append(v.toString()).append('"');
                } else {
                    sb.append(v.toString());
                }
            }
            sb.append(']');
            return sb.toString();
        }
    }

    public static final class HRecord extends HVal {
        private final LinkedHashMap<String, HVal> fields;

        public HRecord() {
            this.fields = new LinkedHashMap<>();
        }

        public HRecord(LinkedHashMap<String, HVal> fields) {
            this.fields = new LinkedHashMap<>(fields);
        }

        public void set(String key, HVal value) {
            fields.put(key, value);
        }

        public void clear() {
            fields.clear();
        }

        public HVal get(String key) {
            HVal v = fields.get(key);
            return v != null ? v : HNothing.INSTANCE;
        }

        public boolean has(String key) {
            return fields.containsKey(key);
        }

        public int size() { return fields.size(); }

        public LinkedHashMap<String, HVal> fields() { return fields; }

        @Override public byte typeCode() { return TYPE_RECORD; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof HRecord) return fields.equals(((HRecord) o).fields);
            return false;
        }

        @Override public int hashCode() { return fields.hashCode(); }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, HVal> e : fields.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(e.getKey()).append(": ");
                HVal v = e.getValue();
                if (v instanceof HString) {
                    sb.append('"').append(v.toString()).append('"');
                } else {
                    sb.append(v.toString());
                }
            }
            sb.append('}');
            return sb.toString();
        }
    }
}
