package edu.rit.gec8773.laps.jcompiler;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

public class JavaFileObject extends SimpleJavaFileObject {
    final ByteArrayOutputStream os =
            new ByteArrayOutputStream();

    public JavaFileObject(String name, Kind kind) {
        super(URI.create(
                "string:///"
                        + name.replace('.', '/')
                        + kind.extension),
                kind);
    }

    public byte[] getBytes() {
        return os.toByteArray();
    }

    @Override
    public OutputStream openOutputStream() {
        return os;
    }
}
