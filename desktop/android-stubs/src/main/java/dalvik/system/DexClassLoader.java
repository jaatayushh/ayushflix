package dalvik.system;

public class DexClassLoader extends PathClassLoader {
    public DexClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(dexPath, librarySearchPath, parent);
    }
}
