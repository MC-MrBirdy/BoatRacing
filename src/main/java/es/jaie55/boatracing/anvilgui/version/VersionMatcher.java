package es.jaie55.boatracing.anvilgui.version;

import net.wesjd.anvilgui.version.VersionWrapper;
import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class VersionMatcher {
    private static final Map<String, String> VERSION_TO_REVISION = new HashMap<>();
    private static final String FALLBACK_REVISION = "26_R1";

    static {
        VERSION_TO_REVISION.put("1.19", "1_19_R1");
        VERSION_TO_REVISION.put("1.19.1", "1_19_R1");
        VERSION_TO_REVISION.put("1.19.2", "1_19_R2");
        VERSION_TO_REVISION.put("1.19.3", "1_19_R3");
        VERSION_TO_REVISION.put("1.19.4", "1_19_R3");
        VERSION_TO_REVISION.put("1.20", "1_20_R1");
        VERSION_TO_REVISION.put("1.20.1", "1_20_R1");
        VERSION_TO_REVISION.put("1.20.2", "1_20_R2");
        VERSION_TO_REVISION.put("1.20.3", "1_20_R3");
        VERSION_TO_REVISION.put("1.20.4", "1_20_R3");
        VERSION_TO_REVISION.put("1.20.5", "1_20_R4");
        VERSION_TO_REVISION.put("1.20.6", "1_20_R4");
        VERSION_TO_REVISION.put("1.21", "1_21_R1");
        VERSION_TO_REVISION.put("1.21.1", "1_21_R1");
        VERSION_TO_REVISION.put("1.21.2", "1_21_R2");
        VERSION_TO_REVISION.put("1.21.3", "1_21_R2");
        VERSION_TO_REVISION.put("1.21.4", "1_21_R3");
        VERSION_TO_REVISION.put("1.21.5", "1_21_R4");
        VERSION_TO_REVISION.put("1.21.6", "1_21_R5");
        VERSION_TO_REVISION.put("1.21.7", "1_21_R5");
        VERSION_TO_REVISION.put("1.21.8", "1_21_R5");
        VERSION_TO_REVISION.put("1.21.9", "1_21_R6");
        VERSION_TO_REVISION.put("1.21.10", "1_21_R6");
        VERSION_TO_REVISION.put("1.21.11", "1_21_R7");
        VERSION_TO_REVISION.put("26.1", "26_R1");
        VERSION_TO_REVISION.put("26.1.1", "26_R1");
        VERSION_TO_REVISION.put("26.1.2", "26_R1");
        VERSION_TO_REVISION.put("26.2", "26_R2");
        VERSION_TO_REVISION.put("26.2.1", "26_R2");
    }

    public VersionWrapper match() {
        String revision = resolveRevision();
        String packageName = getClass().getPackage().getName();

        if ("26_R1".equals(revision)) {
            try {
                return instantiate(packageName + ".Wrapper26_R1_LocalShim");
            } catch (ReflectiveOperationException ignored) { }
            try {
                return instantiate(packageName + ".Wrapper26_R1");
            } catch (ReflectiveOperationException ignored) { }
        }

        try {
            return instantiate(packageName + ".Wrapper" + revision);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("AnvilGUI does not support server version \"" + revision + "\"", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate version wrapper for version " + revision, exception);
        }
    }

    private String resolveRevision() {
        String serverPackage = Bukkit.getServer().getClass().getPackage().getName();
        if (!serverPackage.contains(".v")) {
            String bukkitVersion = Bukkit.getBukkitVersion().split("-")[0];
            return VERSION_TO_REVISION.getOrDefault(bukkitVersion, FALLBACK_REVISION);
        }
        return serverPackage.split("\\.")[3].substring(1);
    }

    private VersionWrapper instantiate(String className) throws ReflectiveOperationException {
        Class<?> wrapperClass = Class.forName(className);
        Constructor<?> constructor = wrapperClass.getDeclaredConstructor();
        return (VersionWrapper) constructor.newInstance();
    }
}
