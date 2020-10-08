package edu.rit.gec8773.laps.jcompiler;

import javax.tools.*;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

/**
 * Credit: https://blog.jooq.org/2018/04/03/how-to-compile-a-class-at-runtime-with-java-8-and-9/
 */
public class Compile {

    static String getClassName(String source) throws PackageNameNotFoundException, ClassNameNotFoundException {
        Matcher matcher = Pattern.compile("package (\\w+\\.)*\\w+;").matcher(source);
        String _package = null;
        while (_package == null && !matcher.hitEnd()) {
            matcher.find();
            _package = matcher.group(1);
        }
        if (_package == null)
            throw new PackageNameNotFoundException();
        _package = _package.substring("package ".length(), _package.length() - 1);

        matcher = Pattern.compile("public\\s+class\\s+\\w+\\s*\\{").matcher(source);
        String className = null;
        while (className == null && !matcher.hitEnd()) {
            matcher.find();
            className = matcher.group(1);
        }
        // TODO add more acceptable class name patterns
        if (className == null)
            throw new ClassNameNotFoundException();
        className = className.replaceFirst("public\\s+class\\s+", "")
                             .replaceFirst("\\s*\\{","");

        return _package + "." + className;
    }

    public static Class<?> compile(File file) throws IOException,
            ClassNameNotFoundException, PackageNameNotFoundException, ClassNotFoundException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return compile(null, new String(inputStream.readAllBytes()));
        }
    }

    public static Class<?> compile(String className, String content)
            throws ClassNameNotFoundException, PackageNameNotFoundException, ClassNotFoundException {
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

    static Class<?> compile0(String className, String content, Lookup lookup)
            throws ClassNotFoundException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        ClassFileManager manager = new ClassFileManager(
                compiler.getStandardFileManager(null, null, null));

        List<CharSequenceJavaFileObject> files = new ArrayList<>();
        files.add(new CharSequenceJavaFileObject(
                className, content));

        compiler.getTask(null, manager, null, null, null, files)
                .call();
        Class<?> result = null;

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

        // If the compiled class is in the same package as the
        // caller class, then we can use the private-access
        // Lookup of the caller class
        if (className.startsWith(caller.getPackageName() )) {
            try {
                result = MethodHandles
                        .privateLookupIn(caller, lookup)
                        .defineClass(manager.o.getBytes());
            } catch (IllegalAccessException ignore) {}
        }

        // Otherwise, use an arbitrary class loader. This
        // approach doesn't allow for loading private-access
        // interfaces in the compiled class's type hierarchy
        else {
            result = new ClassLoader() {
                @Override
                protected Class<?> findClass(String name)
                        throws ClassNotFoundException {
                    byte[] b = manager.o.getBytes();
                    int len = b.length;
                    return defineClass(className, b, 0, len);
                }
            }.loadClass(className);
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
        JavaFileObject o;

        ClassFileManager(StandardJavaFileManager m) {
            super(m);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling
        ) {
            return o = new JavaFileObject(className, kind);
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
