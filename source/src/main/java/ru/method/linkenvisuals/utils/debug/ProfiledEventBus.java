package ru.method.linkenvisuals.utils.debug;

import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.ICancellable;
import meteordevelopment.orbit.IEventBus;
import meteordevelopment.orbit.listeners.IListener;
import meteordevelopment.orbit.listeners.LambdaListener;
import ru.method.linkenvisuals.LinkenVisuals;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Event bus wrapper that profiles every {@link #post(Object)} and each listener invocation.
 */
public final class ProfiledEventBus implements IEventBus {
    private final EventBus delegate = new EventBus();

    @Override
    public void registerLambdaFactory(String packagePrefix, LambdaListener.Factory factory) {
        delegate.registerLambdaFactory(packagePrefix, factory);
    }

    @Override
    public <T> T post(T event) {
        return dispatch(event);
    }

    @Override
    public <T extends ICancellable> T post(T event) {
        return dispatch(event);
    }

    private <T> T dispatch(T event) {
        if (!FullFrameProfiler.isEnabled()) {
            return delegate.post(event);
        }

        long start = FullFrameProfiler.startSection();
        T result = delegate.post(event);
        finishPost(event, start);
        return result;
    }

    @Override
    public void subscribe(Object object) {
        delegate.subscribe(object);
        bindOwner(object);
    }

    @Override
    public void subscribe(Class<?> klass) {
        delegate.subscribe(klass);
        bindOwner(klass);
    }

    @Override
    public void subscribe(IListener listener) {
        delegate.subscribe(listener);
        FullFrameProfiler.bindListener(listener, listener.getClass().getSimpleName());
    }

    @Override
    public void unsubscribe(Object object) {
        delegate.unsubscribe(object);
    }

    @Override
    public void unsubscribe(Class<?> klass) {
        delegate.unsubscribe(klass);
    }

    @Override
    public void unsubscribe(IListener listener) {
        delegate.unsubscribe(listener);
    }

    public void bindExistingListeners() {
        try {
            Map<Object, List<IListener>> instanceCache = readInstanceCache();
            if (instanceCache != null) {
                for (Object owner : instanceCache.keySet()) {
                    bindOwner(owner);
                }
            }
            Map<Class<?>, List<IListener>> staticCache = readStaticCache();
            if (staticCache != null) {
                for (Class<?> owner : staticCache.keySet()) {
                    bindOwner(owner);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static void bindAllFromInstance() {
        LinkenVisuals instance = LinkenVisuals.getInstance();
        if (instance == null) {
            return;
        }
        IEventBus bus = instance.getEventHandler();
        if (bus instanceof ProfiledEventBus profiled) {
            profiled.bindExistingListeners();
        }
    }

    private void finishPost(Object event, long start) {
        long elapsed = System.nanoTime() - start;
        FullFrameProfiler.recordEventPost(event, elapsed);
        FullFrameProfiler.endSection("events.total", start);
    }

    private void bindOwner(Object owner) {
        if (owner == null) {
            return;
        }
        String name = owner.getClass().getSimpleName();
        try {
            List<IListener> listeners = readInstanceCache().get(owner);
            if (listeners == null) {
                return;
            }
            for (IListener listener : listeners) {
                FullFrameProfiler.bindListener(listener, name);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void bindOwner(Class<?> owner) {
        if (owner == null) {
            return;
        }
        String name = owner.getSimpleName();
        try {
            List<IListener> listeners = readStaticCache().get(owner);
            if (listeners == null) {
                return;
            }
            for (IListener listener : listeners) {
                FullFrameProfiler.bindListener(listener, name);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, List<IListener>> readInstanceCache() throws ReflectiveOperationException {
        Field field = EventBus.class.getDeclaredField("listenerCache");
        field.setAccessible(true);
        return (Map<Object, List<IListener>>) field.get(delegate);
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, List<IListener>> readStaticCache() throws ReflectiveOperationException {
        Field field = EventBus.class.getDeclaredField("staticListenerCache");
        field.setAccessible(true);
        return (Map<Class<?>, List<IListener>>) field.get(delegate);
    }
}
