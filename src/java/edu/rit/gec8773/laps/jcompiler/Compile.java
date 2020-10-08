package edu.rit.gec8773.laps.jcompiler;

import edu.rit.gec8773.laps.util.FunctionUtils.CheckedFunction;

import javax.tools.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.rit.gec8773.laps.util.FunctionUtils.exceptWrap;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

/**
 * Credit: https://blog.jooq.org/2018/04/03/how-to-compile-a-class-at-runtime-with-java-8-and-9/
 */
public class Compile {

    static String getClassName(String source) throws ClassNameNotFoundException {
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
        String endPattern = "\\s*(\\<[\\w\\,]+\\>\\s*)?(\\s+((implements\\s+[\\w\\<\\>]+\\s*(\\,[\\w\\<\\>]+)*)|(extends\\s+[\\w\\<\\>]+\\s*(\\,[\\w\\<\\>]+)*))\\s*)?\\{";
        matcher = Pattern.compile(beginPattern + "\\w+" + endPattern).matcher(source);
        String className = null;
        while (className == null && !matcher.hitEnd()) {
            if (matcher.find())
                className = matcher.group();
        }
        // TODO add more acceptable class name patterns
        if (className == null)
            throw new ClassNameNotFoundException();
        className = className.replaceFirst(beginPattern, "")
                             .replaceFirst(endPattern,"");

        return _package != null ? _package + "." + className : className;
    }

    public static Class<?> compile(File file) throws IOException,
            ClassNameNotFoundException, ClassNotFoundException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return compile(null, new String(inputStream.readAllBytes()));
        }
    }

    public static List<Class<?>> compile(List<File> files) throws ClassNotFoundException {
        List<String> classNames = files.stream()
                                       .map(exceptWrap(FileInputStream::new))
                                       .map(exceptWrap(FileInputStream::readAllBytes))
                                       .map(String::new)
                                       .map(exceptWrap(Compile::getClassName))
                                       .collect(Collectors.toList());
        List<SimpleJavaFileObject> javaFileObjects = new ArrayList<>();
        for (var i = 0; i < files.size(); ++i) {
            javaFileObjects.add(new CharSequenceJavaFileObject(classNames.get(i),
                    exceptWrap((CheckedFunction<File, FileInputStream>)FileInputStream::new)
                            .andThen(exceptWrap(FileInputStream::readAllBytes))
                            .andThen(String::new)
                            .apply(files.get(i))));
        }
        Lookup lookup = MethodHandles.lookup();
        return compile0(classNames, javaFileObjects, lookup);
    }

    public static Class<?> compile(String className, String content)
            throws ClassNameNotFoundException, ClassNotFoundException {
        assert content != null;
        if (className == null)
            className = getClassName(content);
        Lookup lookup = MethodHandles.lookup();

        // If we have already compiled our class, simply load it
        try {
            return lookup.lookupClass()
                    .getClassLoader()
                    .loadClass(className);
        }

        // Otherwise, let's try to compile it
        catch (ClassNotFoundException ignore) {
            return compile0(className, content, lookup);
        }
    }

    static Class<?> compile0(String className, String content, Lookup lookup) throws ClassNotFoundException {
        List<SimpleJavaFileObject> files = new ArrayList<>();
        files.add(new CharSequenceJavaFileObject(
                className, content));
        var classNames = List.of(className);
        var result = compile0(classNames, files, lookup);
        return result == null ? null : result.get(0);
    }

    static List<Class<?>> compile0(List<String> classNames, List<SimpleJavaFileObject> files, Lookup lookup)
            throws ClassNotFoundException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        var diagnosticCollector = new DiagnosticCollector<>();
        ClassFileManager manager = new ClassFileManager(
                compiler.getStandardFileManager(diagnosticCollector, null, null));


        compiler.getTask(null, manager, diagnosticCollector, null, null, files)
                .call();
        var errors = diagnosticCollector.getDiagnostics();
        var hasErrors = false;
        for (var error : errors) {
            System.err.println(error);
            hasErrors |= error.getKind().equals(Diagnostic.Kind.ERROR);
        }

        if (hasErrors)
            return null;

        List<Class<?>> result = new ArrayList<>();

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
                            .defineClass(manager.objs.get(className).getBytes()));
                } catch (IllegalAccessException ignore) {

                }
            }

            // Otherwise, use an arbitrary class loader. This
            // approach doesn't allow for loading private-access
            // interfaces in the compiled class's type hierarchy
            else {
                result.add(new ClassLoader() {
                    @Override
                    protected Class<?> findClass(String name)
                            throws ClassNotFoundException {
                        byte[] b = manager.objs.get(className).getBytes();
                        int len = b.length;
                        return defineClass(className, b, 0, len);
                    }
                }.loadClass(className));
            }
        }

        return result;
    }

    // These are some utility classes needed for the JavaCompiler
    // ----------------------------------------------------------

    static final class JavaFileObject
            extends SimpleJavaFileObject {
        final ByteArrayOutputStream os =
                new ByteArrayOutputStream();

        JavaFileObject(String name, JavaFileObject.Kind kind) {
            super(URI.create(
                    "string:///"
                            + name.replace('.', '/')
                            + kind.extension),
                    kind);
        }

        byte[] getBytes() {
            return os.toByteArray();
        }

        @Override
        public OutputStream openOutputStream() {
            return os;
        }
    }

    static final class ClassFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {
        Map<String, JavaFileObject> objs;

        ClassFileManager(StandardJavaFileManager m) {
            super(m);
            objs = new HashMap<>();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling
        ) {
            objs.put(className, new JavaFileObject(className, kind));
            return objs.get(className);
        }
    }

    static final class CharSequenceJavaFileObject
            extends SimpleJavaFileObject {
        final CharSequence content;

        public CharSequenceJavaFileObject(
                String className,
                CharSequence content
        ) {
            super(URI.create(
                    "string:///"
                            + className.replace('.', '/')
                            + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(
                boolean ignoreEncodingErrors
        ) {
            return content;
        }
    }
}
