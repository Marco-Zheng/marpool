package com.marco.threadpool.core;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class ConcurrentHashSet<T> extends AbstractSet<T> {

    private ConcurrentHashMap<T, Object> map = new ConcurrentHashMap<>();

    private final Object PRESENT = new Object();

    private AtomicInteger count = new AtomicInteger();

    @Override
    public Iterator<T> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public boolean add(T e) {
        // 利用AtomicInteger的原子性进行增减
        count.incrementAndGet();
        return map.put(e, PRESENT) == null;
    }

    @Override
    public boolean remove(Object o) {
        // 利用AtomicInteger的原子性进行增减
        count.decrementAndGet();
        return map.remove(o) == null;
    }

    @Override
    public int size() {
        return count.get();
    }
}

