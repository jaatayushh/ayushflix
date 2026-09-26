package dalvik.system;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class PathClassLoader extends ClassLoader {
    private final String dexPath;
    private URLClassLoader innerLoader;

    public PathClassLoader(String dexPath, ClassLoader parent) {
        super(parent != null ? parent : Thread.currentThread().getContextClassLoader());
        this.dexPath = dexPath;
        initInnerLoader();
    }

    public PathClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(parent != null ? parent : Thread.currentThread().getContextClassLoader());
        this.dexPath = dexPath;
        initInnerLoader();
    }

    private void initInnerLoader() {
        if (dexPath == null) return;
        List<URL> urls = new ArrayList<>();
        for (String part : dexPath.split(File.pathSeparator)) {
            File f = new File(part);
            if (f.exists()) {
                try {
                    urls.add(f.toURI().toURL());
                } catch (MalformedURLException ignored) {}
            }
        }
        if (!urls.isEmpty()) {
            innerLoader = new URLClassLoader(urls.toArray(new URL[0]), getParent());
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (innerLoader != null) {
            try {
                return innerLoader.loadClass(name);
            } catch (ClassNotFoundException ignored) {}
        }
        ClassLoader ctxCl = Thread.currentThread().getContextClassLoader();
        if (ctxCl != null && ctxCl != this && ctxCl != getParent()) {
            try {
                return ctxCl.loadClass(name);
            } catch (ClassNotFoundException ignored) {}
        }
        return super.findClass(name);
    }
}
