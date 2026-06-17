package es.jaie55.boatracing.anvilgui.version;

import net.wesjd.anvilgui.version.VersionWrapper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class Wrapper26_R1_LocalShim implements VersionWrapper {
    private static final String CLOSE_REASON_CLASS = "org.bukkit.event.inventory.InventoryCloseEvent$Reason";
    private static volatile boolean warnedOnce;

    private final VersionWrapper delegate;

    public Wrapper26_R1_LocalShim() {
        this.delegate = new net.wesjd.anvilgui.version.Wrapper26_R1();
    }

    @Override
    public int getNextContainerId(Player player, AnvilContainerWrapper container) {
        return delegate.getNextContainerId(player, container);
    }

    @Override
    public void handleInventoryCloseEvent(Player player) {
        try {
            delegate.handleInventoryCloseEvent(player);
            return;
        } catch (RuntimeException | NoSuchMethodError originalFailure) {
            if (!invokeCloseEventFallback(player)) {
                throw originalFailure;
            }
            invokeCloseContainerFallback(player);
            warnOnce(originalFailure);
        }
    }

    @Override
    public void sendPacketOpenWindow(Player player, int containerId, Object inventoryTitle) {
        delegate.sendPacketOpenWindow(player, containerId, inventoryTitle);
    }

    @Override
    public void sendPacketCloseWindow(Player player, int containerId) {
        delegate.sendPacketCloseWindow(player, containerId);
    }

    @Override
    public void sendPacketExperienceChange(Player player, int experienceLevel) {
        delegate.sendPacketExperienceChange(player, experienceLevel);
    }

    @Override
    public void setActiveContainerDefault(Player player) {
        delegate.setActiveContainerDefault(player);
    }

    @Override
    public void setActiveContainer(Player player, AnvilContainerWrapper container) {
        delegate.setActiveContainer(player, container);
    }

    @Override
    public void setActiveContainerId(AnvilContainerWrapper container, int containerId) {
        delegate.setActiveContainerId(container, containerId);
    }

    @Override
    public void addActiveContainerSlotListener(AnvilContainerWrapper container, Player player) {
        delegate.addActiveContainerSlotListener(container, player);
    }

    @Override
    public AnvilContainerWrapper newContainerAnvil(Player player, Object title) {
        return delegate.newContainerAnvil(player, title);
    }

    @Override
    public Object literalChatComponent(String content) {
        return delegate.literalChatComponent(content);
    }

    @Override
    public Object jsonChatComponent(String json) {
        return delegate.jsonChatComponent(json);
    }

    private boolean invokeCloseEventFallback(Player player) {
        Object nmsPlayer = toNmsPlayer(player);
        if (nmsPlayer == null) {
            return false;
        }

        try {
            Class<?> craftEventFactoryClass = Class.forName("org.bukkit.craftbukkit.event.CraftEventFactory");
            Class<?> reasonClass = null;
            Object unknownReason = null;

            try {
                reasonClass = Class.forName(CLOSE_REASON_CLASS);
                unknownReason = reasonClass.getField("UNKNOWN").get(null);
            } catch (ReflectiveOperationException ignored) {
                // Some builds may not expose InventoryCloseEvent.Reason.
            }

            for (Method method : craftEventFactoryClass.getMethods()) {
                if (!"handleInventoryCloseEvent".equals(method.getName()) || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(nmsPlayer.getClass())) {
                    method.invoke(null, nmsPlayer);
                    return true;
                }

                if (params.length == 2
                        && reasonClass != null
                        && params[0].isAssignableFrom(nmsPlayer.getClass())
                        && params[1].isAssignableFrom(reasonClass)) {
                    Object reason = unknownReason != null ? unknownReason : firstEnumConstant(reasonClass);
                    if (reason != null) {
                        method.invoke(null, nmsPlayer, reason);
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }

        return false;
    }

    private void invokeCloseContainerFallback(Player player) {
        Object nmsPlayer = toNmsPlayer(player);
        if (nmsPlayer == null) {
            return;
        }

        for (String methodName : new String[]{"doCloseContainer", "closeContainer", "s"}) {
            Method method = findNoArgMethod(nmsPlayer.getClass(), methodName);
            if (method == null) {
                continue;
            }
            try {
                method.invoke(nmsPlayer);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Try next candidate.
            }
        }
    }

    private static Object toNmsPlayer(Player player) {
        Method getHandle = findNoArgMethod(player.getClass(), "getHandle");
        if (getHandle == null) {
            return null;
        }
        try {
            return getHandle.invoke(player);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findNoArgMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object firstEnumConstant(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        return (constants != null && constants.length > 0) ? constants[0] : null;
    }

    private static void warnOnce(Throwable cause) {
        if (warnedOnce) {
            return;
        }
        warnedOnce = true;
        Bukkit.getLogger().warning("[BoatRacing] Applied AnvilGUI compatibility fallback for inventory close handling: "
                + cause.getClass().getSimpleName());
    }
}
