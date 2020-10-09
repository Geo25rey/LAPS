package edu.rit.gec8773.laps.jcompiler;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CompileTest {
    @Test
    public void compileNameTest() throws ClassNotFoundException {
        String source = "package com.test;" +
                "public class Test {}";
        List<Class<?>> cls = Compile.compileSources(source);
        assertEquals(cls.get(0).getName(), "com.test.Test");
    }

    @Test
    public void compileInstanceTest() throws ClassNotFoundException,
            IllegalAccessException, InstantiationException {
        String source = "package com.test;" +
                "public class Test {}";
        List<Class<?>> cls = Compile.compileSources(source);
        @SuppressWarnings("deprecation") Object obj = cls.get(0).newInstance();
        assertEquals(obj.getClass(), cls.get(0));
    }

    @Test
    public void compileNoClassNameTest() {
        String source = "package com.test;";
        assertThrows(ClassNameNotFoundException.class, () -> Compile.compileSources(source));
    }

    @Test
    public void compileDefaultPackageTest() throws ClassNotFoundException {
        String source = "public class Test {}";
        List<Class<?>> cls = Compile.compileSources(source);
        assertFalse(cls.isEmpty());
    }

    @Test
    public void compileCallerPackageTest() throws ClassNotFoundException {
        String source = "package " + getClass().getPackageName() + ";"
                + "public class Test {}";
        List<Class<?>> cls = Compile.compileSources(source);
        assertFalse(cls.isEmpty());
    }

    @Test
    public void compileFileTest() throws ClassNotFoundException {
        // Relative directories start in the Project Directory
        var files = List.of(new File ("examples/src/java/numlist/Numbers.java"),
                                      new File("examples/src/java/numlist/NumList.java"));
        List<Class<?>> cls = Compile.compileFiles(files);
        assertNotNull(cls);
        assertEquals(cls.size(), 2);
    }

    @Test
    public void compileWithErrorsTest() throws ClassNotFoundException {
        String source = "class Test {" +
                "Unknown;" +
                "}";
        List<Class<?>> cls = Compile.compileSources(source);
        assertTrue(cls.isEmpty());
    }

    @Test
    public void compileKeepsParameterNamesTest() throws ClassNotFoundException, NoSuchMethodException {
        String source = "public class Test {" +
                "public static void function(int parameterName) {}" +
                "}";
        List<Class<?>> cls = Compile.compileSources(source);
        String name = Arrays.stream(cls.get(0)
                                       .getMethod("function", Integer.TYPE)
                                       .getParameters())
                            .findFirst()
                            .orElseThrow()
                            .getName();
        assertEquals("parameterName", name);
    }
}
