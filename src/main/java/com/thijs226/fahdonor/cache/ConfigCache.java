package com.thijs226.fahdonor.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Simple cache implementation with TTL (time-to-live) support for configuration
 * values and expensive computations. Thread-safe and efficient.
 */
public class ConfigCache<K, V> {
    
    private final ConcurrentHashMap<K, CachedValue<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;
    
    public ConfigCache(long ttl, TimeUnit unit) {
        this.ttlMillis = unit.toMillis(ttl);
    }
    
    private static class CachedValue<V> {
        final V value;
        final long timestamp;
        
        CachedValue(V value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired(long ttlMillis) {
            return (System.currentTimeMillis() - timestamp) > ttlMillis;
        }
    }
    
    /**
     * Gets a value from cache, or computes it if missing/expired
     */
    public V get(K key, Supplier<V> supplier) {
        CachedValue<V> cached = cache.get(key);
        
        if (cached != null && !cached.isExpired(ttlMillis)) {
            return cached.value;
        }
        
        V value = supplier.get();
        cache.put(key, new CachedValue<>(value));
        return value;
    }
    
    /**
     * Puts a value directly into cache
     */
    public void put(K key, V value) {
        cache.put(key, new CachedValue<>(value));
    }
    
    /**
     * Invalidates a specific key
     */
    public void invalidate(K key) {
        cache.remove(key);
    }
    
    /**
     * Clears all cached values
     */
    public void invalidateAll() {
        cache.clear();
    }
    
    /**
     * Gets cache size
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Cleans up expired entries
     */
    public int cleanupExpired() {
        int removed = 0;
        for (var entry : cache.entrySet()) {
            if (entry.getValue().isExpired(ttlMillis)) {
                cache.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }
}
