package ru.method.linkenvisuals.managers.misc;


import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;


public class FriendManager {

    // COW: чтений (isFriend в рендере каждый кадр) на порядки больше записей из HTTP-потока,
    // чтение/итерация без блокировок и без ConcurrentModificationException
    @Getter private final List<String> friends = new CopyOnWriteArrayList<>();
    // Атомарный счётчик изменений — читается/пишется из разных потоков
    private final AtomicInteger revision = new AtomicInteger();

    public int getRevision() {
        return revision.get();
    }

    public void add(String friend) {
        if (!friend.isEmpty()) {
            friends.add(friend);
            revision.incrementAndGet();
        }
    }

    public void remove(String friend) {
        if (friends.remove(friend)) {
            revision.incrementAndGet();
        }
    }

    public void clear() {
        if (friends.isEmpty()) {
            return;
        }
        friends.clear();
        revision.incrementAndGet();
    }

    public boolean isFriend(String friend) {
        return friends.contains(friend);
    }

    public boolean isFriendIgnoreCase(String friend) {
        if (friend == null) return false;
        for (String existing : friends) {
            if (existing.equalsIgnoreCase(friend)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return friends.isEmpty();
    }
}
