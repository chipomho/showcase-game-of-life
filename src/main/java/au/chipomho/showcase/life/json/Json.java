package au.chipomho.showcase.life.json;

import java.lang.reflect.RecordComponent;
import java.util.Collection;

/**
 * Writes records as JSON objects by reading their components, in declaration order, which is all
 * an API built from records needs. Strings, booleans, integers, enums (by name), records and
 * collections are supported. A record component that is null is left out rather than written
 * as null, so optional fields simply don't appear.
 *
 * <p>Records must be public, and nested records must be inside public classes, because their
 * accessors are invoked reflectively.
 */
public final class Json {

    private Json() {
    }

    public static String write(Object value) {
        var out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case String text -> writeString(text, out);
            case Boolean flag -> out.append(flag);
            case Integer number -> out.append(number);
            case Long number -> out.append(number);
            case Enum<?> constant -> writeString(constant.name(), out);
            case Record record -> writeObject(record, out);
            case Collection<?> items -> writeArray(items, out);
            default -> throw new IllegalArgumentException("No JSON form for " + value.getClass().getName());
        }
    }

    private static void writeObject(Record record, StringBuilder out) {
        out.append('{');
        var separator = "";
        for (var component : record.getClass().getRecordComponents()) {
            var value = read(record, component);
            if (value != null) {
                out.append(separator);
                writeString(component.getName(), out);
                out.append(':');
                write(value, out);
                separator = ",";
            }
        }
        out.append('}');
    }

    private static Object read(Record record, RecordComponent component) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot read %s of %s".formatted(component.getName(), record.getClass().getName()), e);
        }
    }

    private static void writeArray(Collection<?> items, StringBuilder out) {
        out.append('[');
        var separator = "";
        for (var item : items) {
            out.append(separator);
            write(item, out);
            separator = ",";
        }
        out.append(']');
    }

    /**
     * Escapes what RFC 8259 requires: quotation mark, reverse solidus and control characters.
     */
    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
