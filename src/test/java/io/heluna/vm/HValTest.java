package io.heluna.vm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import io.heluna.vm.HVal.*;

class HValTest {

    // --- HInteger ---

    @Test void integerValue() {
        assertEquals(42, new HInteger(42).value());
        assertEquals(-100, new HInteger(-100).value());
        assertEquals(0, new HInteger(0).value());
    }

    @Test void integerTypeCode() {
        assertEquals(HVal.TYPE_INTEGER, new HInteger(0).typeCode());
    }

    @Test void integerEquality() {
        assertEquals(new HInteger(42), new HInteger(42));
        assertNotEquals(new HInteger(42), new HInteger(43));
    }

    @Test void integerToString() {
        assertEquals("42", new HInteger(42).toString());
        assertEquals("-1", new HInteger(-1).toString());
        assertEquals("0", new HInteger(0).toString());
    }

    @Test void integerIsNotNothing() {
        assertFalse(new HInteger(0).isNothing());
    }

    // --- HFloat ---

    @Test void floatValue() {
        assertEquals(3.14, new HFloat(3.14).value(), 0.001);
        assertEquals(-2.5, new HFloat(-2.5).value(), 0.001);
    }

    @Test void floatTypeCode() {
        assertEquals(HVal.TYPE_FLOAT, new HFloat(0.0).typeCode());
    }

    @Test void floatEquality() {
        assertEquals(new HFloat(3.14), new HFloat(3.14));
        assertNotEquals(new HFloat(3.14), new HFloat(3.15));
    }

    @Test void floatToStringWholeNumber() {
        assertEquals("5.0", new HFloat(5.0).toString());
        assertEquals("0.0", new HFloat(0.0).toString());
    }

    @Test void floatToStringDecimal() {
        assertEquals("3.14", new HFloat(3.14).toString());
    }

    // --- Cross-type numeric equality ---

    @Test void integerFloatEquality() {
        assertEquals(new HInteger(5), new HFloat(5.0));
        assertEquals(new HFloat(5.0), new HInteger(5));
        assertNotEquals(new HInteger(5), new HFloat(5.1));
    }

    @Test void integerFloatHashConsistency() {
        // Equal objects must have equal hash codes
        assertEquals(new HInteger(5).hashCode(), new HFloat(5.0).hashCode());
    }

    // --- HString ---

    @Test void stringValue() {
        assertEquals("hello", new HString("hello").value());
        assertEquals("", new HString("").value());
    }

    @Test void stringTypeCode() {
        assertEquals(HVal.TYPE_STRING, new HString("").typeCode());
    }

    @Test void stringEquality() {
        assertEquals(new HString("abc"), new HString("abc"));
        assertNotEquals(new HString("abc"), new HString("def"));
    }

    @Test void stringToString() {
        assertEquals("hello", new HString("hello").toString());
    }

    @Test void stringRejectsNull() {
        assertThrows(NullPointerException.class, () -> new HString(null));
    }

    @Test void stringNotEqualToInteger() {
        assertNotEquals(new HString("42"), new HInteger(42));
    }

    // --- HBoolean ---

    @Test void booleanSingletons() {
        assertSame(HBoolean.TRUE, HBoolean.of(true));
        assertSame(HBoolean.FALSE, HBoolean.of(false));
    }

    @Test void booleanValue() {
        assertTrue(HBoolean.TRUE.value());
        assertFalse(HBoolean.FALSE.value());
    }

    @Test void booleanTypeCode() {
        assertEquals(HVal.TYPE_BOOLEAN, HBoolean.TRUE.typeCode());
    }

    @Test void booleanEquality() {
        assertEquals(HBoolean.TRUE, HBoolean.of(true));
        assertNotEquals(HBoolean.TRUE, HBoolean.FALSE);
    }

    @Test void booleanToString() {
        assertEquals("true", HBoolean.TRUE.toString());
        assertEquals("false", HBoolean.FALSE.toString());
    }

    // --- HNothing ---

    @Test void nothingSingleton() {
        assertSame(HNothing.INSTANCE, HNothing.INSTANCE);
    }

    @Test void nothingTypeCode() {
        assertEquals(HVal.TYPE_NOTHING, HNothing.INSTANCE.typeCode());
    }

    @Test void nothingIsNothing() {
        assertTrue(HNothing.INSTANCE.isNothing());
    }

    @Test void nothingEquality() {
        assertEquals(HNothing.INSTANCE, HNothing.INSTANCE);
    }

    @Test void nothingToString() {
        assertEquals("nothing", HNothing.INSTANCE.toString());
    }

    @Test void nothingNotEqualToOtherTypes() {
        assertNotEquals(HNothing.INSTANCE, new HInteger(0));
        assertNotEquals(HNothing.INSTANCE, HBoolean.FALSE);
        assertNotEquals(HNothing.INSTANCE, new HString(""));
    }

    // --- HList ---

    @Test void emptyList() {
        HList list = new HList();
        assertEquals(0, list.size());
        assertEquals(HVal.TYPE_LIST, list.typeCode());
    }

    @Test void listAddAndGet() {
        HList list = new HList();
        list.add(new HInteger(1));
        list.add(new HInteger(2));
        list.add(new HInteger(3));
        assertEquals(3, list.size());
        assertEquals(new HInteger(1), list.get(0));
        assertEquals(new HInteger(2), list.get(1));
        assertEquals(new HInteger(3), list.get(2));
    }

    @Test void listGetOutOfBoundsReturnsNothing() {
        HList list = new HList();
        list.add(new HInteger(1));
        assertTrue(list.get(-1).isNothing());
        assertTrue(list.get(1).isNothing());
        assertTrue(list.get(100).isNothing());
    }

    @Test void listEquality() {
        HList a = new HList();
        a.add(new HInteger(1));
        a.add(new HString("two"));

        HList b = new HList();
        b.add(new HInteger(1));
        b.add(new HString("two"));

        assertEquals(a, b);

        HList c = new HList();
        c.add(new HInteger(1));
        assertNotEquals(a, c);
    }

    @Test void listToString() {
        HList list = new HList();
        list.add(new HInteger(1));
        list.add(new HString("hello"));
        list.add(HBoolean.TRUE);
        assertEquals("[1, \"hello\", true]", list.toString());
    }

    // --- HRecord ---

    @Test void emptyRecord() {
        HRecord rec = new HRecord();
        assertEquals(0, rec.size());
        assertEquals(HVal.TYPE_RECORD, rec.typeCode());
    }

    @Test void recordSetAndGet() {
        HRecord rec = new HRecord();
        rec.set("name", new HString("Alice"));
        rec.set("age", new HInteger(30));
        assertEquals(new HString("Alice"), rec.get("name"));
        assertEquals(new HInteger(30), rec.get("age"));
        assertEquals(2, rec.size());
    }

    @Test void recordGetMissingReturnsNothing() {
        HRecord rec = new HRecord();
        assertTrue(rec.get("missing").isNothing());
    }

    @Test void recordHas() {
        HRecord rec = new HRecord();
        rec.set("key", new HInteger(1));
        assertTrue(rec.has("key"));
        assertFalse(rec.has("other"));
    }

    @Test void recordPreservesInsertionOrder() {
        HRecord rec = new HRecord();
        rec.set("c", new HInteger(3));
        rec.set("a", new HInteger(1));
        rec.set("b", new HInteger(2));

        String[] keys = rec.fields().keySet().toArray(new String[0]);
        assertArrayEquals(new String[]{"c", "a", "b"}, keys);
    }

    @Test void recordEquality() {
        HRecord a = new HRecord();
        a.set("x", new HInteger(1));

        HRecord b = new HRecord();
        b.set("x", new HInteger(1));

        assertEquals(a, b);

        HRecord c = new HRecord();
        c.set("x", new HInteger(2));
        assertNotEquals(a, c);
    }

    @Test void recordToString() {
        HRecord rec = new HRecord();
        rec.set("name", new HString("Bob"));
        rec.set("age", new HInteger(25));
        assertEquals("{name: \"Bob\", age: 25}", rec.toString());
    }

    // --- Mixed type comparisons ---

    @Test void differentTypesNotEqual() {
        assertNotEquals(new HInteger(1), new HString("1"));
        assertNotEquals(new HInteger(1), HBoolean.TRUE);
        assertNotEquals(new HString("true"), HBoolean.TRUE);
        assertNotEquals(new HList(), new HRecord());
    }

    // --- Float edge cases ---

    @Test void floatNaN() {
        HFloat nan1 = new HFloat(Double.NaN);
        HFloat nan2 = new HFloat(Double.NaN);
        // NaN != NaN per IEEE 754 (Double.compare treats NaN == NaN though)
        assertEquals(nan1, nan2); // Our equals uses Double.compare
        assertEquals("NaN", nan1.toString());
    }

    @Test void floatInfinity() {
        HFloat posInf = new HFloat(Double.POSITIVE_INFINITY);
        HFloat negInf = new HFloat(Double.NEGATIVE_INFINITY);
        assertNotEquals(posInf, negInf);
        assertEquals("Infinity", posInf.toString());
        assertEquals("-Infinity", negInf.toString());
    }

    @Test void floatNegativeZero() {
        HFloat negZero = new HFloat(-0.0);
        HFloat posZero = new HFloat(0.0);
        // Double.compare(-0.0, 0.0) != 0, so they're not equal
        assertNotEquals(negZero, posZero);
    }

    // --- Integer edge cases ---

    @Test void integerOverflowWraps() {
        // Java long overflow wraps
        HInteger maxVal = new HInteger(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, maxVal.value());
        // Adding 1 wraps in Java
        long wrapped = Long.MAX_VALUE + 1;
        assertEquals(Long.MIN_VALUE, wrapped);
    }

    // --- String unicode ---

    @Test void stringUnicodeEmoji() {
        HString emoji = new HString("\uD83D\uDE00"); // grinning face
        assertEquals("\uD83D\uDE00", emoji.value());
        assertEquals(HVal.TYPE_STRING, emoji.typeCode());
    }

    @Test void stringUnicodeMultiByte() {
        HString s = new HString("\u00E9\u00E8\u00EA"); // e-accent chars
        assertEquals(3, s.value().length());
    }

    // --- List edge cases ---

    @Test void listDifferentOrderNotEqual() {
        HList a = new HList();
        a.add(new HInteger(1));
        a.add(new HInteger(2));

        HList b = new HList();
        b.add(new HInteger(2));
        b.add(new HInteger(1));

        assertNotEquals(a, b);
    }

    // --- Record edge cases ---

    @Test void recordOverwrite() {
        HRecord rec = new HRecord();
        rec.set("key", new HInteger(1));
        rec.set("key", new HInteger(2));
        assertEquals(new HInteger(2), rec.get("key"));
        assertEquals(1, rec.size()); // still only one entry
    }

    // --- Nothing edge cases ---

    @Test void twoNothingInstancesEqual() {
        assertEquals(HNothing.INSTANCE, HNothing.INSTANCE);
        assertEquals(0, HNothing.INSTANCE.hashCode());
    }

    // --- Cross-type inequality ---

    @Test void crossTypeInequality() {
        assertNotEquals(new HString("1"), new HInteger(1));
        assertNotEquals(new HInteger(0), HBoolean.FALSE);
        assertNotEquals(new HString("nothing"), HNothing.INSTANCE);
        assertNotEquals(new HString("true"), HBoolean.TRUE);
    }
}
