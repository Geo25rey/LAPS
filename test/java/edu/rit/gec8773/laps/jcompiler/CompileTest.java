package edu.rit.gec8773.laps.jcompiler;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class CompileTest {
    @Test
    public void compileNameTest() throws ClassNameNotFoundException, ClassNotFoundException {
        String source = "package com.test;" +
                "public class Test {}";
        Class<?> cls = Compile.compile(null, source);
        assertEquals(cls.getName(), "com.test.Test");
    }

    @Test
    public void compileInstanceTest() throws ClassNameNotFoundException, ClassNotFoundException,
            IllegalAccessException, InstantiationException {
        String source = "package com.test;" +
                "public class Test {}";
        Class<?> cls = Compile.compile(null, source);
        Object obj = cls.newInstance();
        assertEquals(obj.getClass(), cls);
    }

    @Test
    public void compileNoClassNameTest() {
        String source = "package com.test;";
        assertThrows(ClassNameNotFoundException.class, () -> Compile.compile(null, source));
    }

    @Test
    public void compileDefaultPackageTest() throws ClassNameNotFoundException, ClassNotFoundException {
        String source = "public class Test {}";
        Class<?> cls = Compile.compile(null, source);
        assertNotNull(cls);
    }

    @Test
    public void compileCallerPackageTest() throws ClassNameNotFoundException, ClassNotFoundException {
        String source = "package " + getClass().getPackageName() + ";"
                + "public class Test {}";
        Class<?> cls = Compile.compile(null, source);
        assertNotNull(cls);
    }

    @Test
    public void compileFileTest() throws ClassNameNotFoundException, ClassNotFoundException, IOException {
        // Relative directories start in the Project Directory
        var files = List.of(new File ("examples/src/java/numlist/Numbers.java"),
                                      new File("examples/src/java/numlist/NumList.java"));
        List<Class<?>> cls = Compile.compile(files);
        assertNotNull(cls);
        assertEquals(cls.size(), 2);
    }

    @Test
    public void compileWithErrorsTest() throws ClassNameNotFoundException, ClassNotFoundException {
        String source = "class Test {" +
                "Unknown;" +
                "}";
        Class<?> cls = Compile.compile(null, source);
        assertNull(cls);
    }
}
