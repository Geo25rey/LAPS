package edu.rit.gec8773.laps.jcompiler;

import edu.rit.gec8773.laps.util.FunctionUtils.CheckedFunction;

import javax.tools.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.rit.gec8773.laps.util.FunctionUtils.exceptWrap;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

/**
 * Credit: https://blog.jooq.org/2018/04/03/how-to-compile-a-class-at-runtime-with-java-8-and-9/
 */
public class Compile {

    private static String getClassName(String source) throws ClassNameNotFoundException {
        Matcher matcher = Pattern.compile(".*package\\s+(\\w+\\.)*\\w+\\s*;").matcher(source);
        String _package = null;
        while (_package == null && !matcher.hitEnd()) {
            if (matcher.find())
                _package = matcher.group();
        }
        if (_package != null)
            _package = _package.replaceFirst(".*package\\s+", "")
                               .replaceFirst("\\s*;","");

        String beginPattern = ".*(public\\s+)?class\\s+";
        String endPattern = "\\s*(<[\\w,]+>\\s*)?(\\s+((implements\\s+[\\w<>]+\\s*(,[\\w<>]+)*)|(extends\\s+[\\w<>]+\\s*(,[\\w<>]+)*))\\s*)?\\{";
        matcher = Pattern.compile(beginPattern + "\\w+" + endPattern).matcher(source);
        String className = null;
        while (className == null && !matcher.hitEnd()) {
            if (matcher.find())
                className = matcher.group();
        }

        if (className == null)
            throw new ClassNameNotFoundException();
        className = className.replaceFirst(beginPattern, "")
                             .replaceFirst(endPattern,"");

        return _package != null ? _package + "." + className : className;
    }

    public static List<Class<?>> compileFiles(File... files) throws ClassNotFoundException {
        return compileFiles(Arrays.asList(files));
    }

    public static List<Class<?>> compileFiles(List<File> files) throws ClassNotFoundException {
        List<String> classNames = files.parallelStream()
                                       .map(exceptWrap((CheckedFunction<File, FileInputStream>)FileInputStream::new)
                                       .andThen(exceptWrap(FileInputStream::readAllBytes))
                                       .andThen(String::new)
                                       .andThen(exceptWrap(Compile::getClassName)))
                                       .collect(Collectors.toList());
        List<SimpleJavaFileObject> javaFileObjects = new ArrayList<>();
        for (var i = 0; i < files.size(); ++i) {
            javaFileObjects.add(new CharSequenceJavaFileObject(classNames.get(i),
                    exceptWrap((CheckedFunction<File, FileInputStream>)FileInputStream::new)
                            .andThen(exceptWrap(FileInputStream::readAllBytes))
                            .andThen(String::new)
                            .apply(files.get(i))));
        }
        return compile(classNames, javaFileObjects);
    }

    public static List<Class<?>> compileSources(String... contents) throws ClassNotFoundException {
        return compileSources(Arrays.asList(contents));
    }

    public static List<Class<?>> compileSources(List<String> contents) throws ClassNotFoundException {
        var classNames = contents.parallelStream()
                .map(exceptWrap(Compile::getClassName))
                .collect(Collectors.toList());
        List<SimpleJavaFileObject> contentList = new ArrayList<>();
        for (var i = 0; i < classNames.size(); ++i) {
            contentList.add(new CharSequenceJavaFileObject(classNames.get(i), contents.get(i)));
        }
        return compile(classNames, contentList);
    }

    private static List<Class<?>> compile(List<String> classNames, List<SimpleJavaFileObject> files)
            throws ClassNotFoundException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Lookup lookup = MethodHandles.lookup();

        List<Class<?>> result = new ArrayList<>();

        for (var i = 0; i < classNames.size(); ++i) {
            // If we have already compiled our class, simply load it
            try {
                result.add(lookup.lookupClass()
                        .getClassLoader()
                        .loadClass(classNames.get(i)));
                classNames.remove(i);
                files.remove(i);
            } // Otherwise, let's try to compile it
            catch (ClassNotFoundException ignore) {

            }
        }

        if (files.isEmpty())
            return result;

        var diagnosticCollector = new DiagnosticCollector<>();
        ClassFileManager manager = new ClassFileManager(
                compiler.getStandardFileManager(diagnosticCollector, null, null));

        var compilationFlags = List.of("-parameters");

        compiler.getTask(null, manager, diagnosticCollector, compilationFlags, null, files)
                .call();
        var errors = diagnosticCollector.getDiagnostics();
        var hasErrors = false;
        for (var error : errors) {
            System.err.println(error);
            hasErrors |= error.getKind().equals(Diagnostic.Kind.ERROR);
        }

        if (hasErrors)
            return result;

        // This method is called by client code from two levels
        // up the current stack frame. We need a private-access
        // lookup from the class in that stack frame in order
        // to get private-access to any local interfaces at
        // that location.
        Class<?> caller = StackWalker
                .getInstance(RETAIN_CLASS_REFERENCE)
                .walk(s -> s
                        .skip(2)
                        .findFirst()
                        .get()
                        .getDeclaringClass());

        for (var className : classNames) {

            // If the compiled class is in the same package as the
            // caller class, then we can use the private-access
            // Lookup of the caller class
            if (className.startsWith(caller.getPackageName())) {
                try {
                    result.add(MethodHandles
                            .privateLookupIn(caller, lookup)
                            .defineClass(manager.get(className).getBytes()));
                } catch (IllegalAccessException ignore) {

                }
            }

            // Otherwise, use an arbitrary class loader. This
            // approach doesn't allow for loading private-access
            // interfaces in the compiled class's type hierarchy
            else {
                result.add(new ClassLoader() {
                    @Override
                    protected Class<?> findClass(String name) {
                        byte[] b = manager.get(className).getBytes();
                        int len = b.length;
                        return defineClass(className, b, 0, len);
                    }
                }.loadClass(className));
            }
        }

        return result;
    }
}
