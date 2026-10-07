package com.edstem.interviewprep.common;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";

    private static final long MAXIMUM_ENTRIES_PER_CACHE = 10_000;

    /**
     * Evictions wait until the surrounding transaction commits. Evicting earlier would let a concurrent lookup
     * reload the old row before the commit and keep serving it.
     */
    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager(PRODUCTS_CACHE);
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(MAXIMUM_ENTRIES_PER_CACHE));
        caffeineCacheManager.setAllowNullValues(false);
        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }
}
