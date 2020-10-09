package edu.rit.gec8773.laps.jcompiler;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.StandardJavaFileManager;
import java.util.HashMap;
import java.util.Map;

public class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Map<String, JavaFileObject> objs;

    public ClassFileManager(StandardJavaFileManager m) {
        super(m);
        objs = new HashMap<>();
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
            Location location,
            String className,
            JavaFileObject.Kind kind,
            FileObject sibling
    ) {
        objs.put(className, new JavaFileObject(className, kind));
        return objs.get(className);
    }

    public JavaFileObject get(String className) {
        return objs.get(className);
    }
}
