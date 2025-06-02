package io.github.alvinchiu.gstreamgate.optimization;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 內存優化系統
 * 提供對象池、緩存管理、內存監控等功能以減少 GC 壓力
 */
@Component
public class MemoryOptimizer {
    private static final Logger logger = LoggerFactory.getLogger(MemoryOptimizer.class);

    // 對象池
    private final ObjectPool<ByteArrayOutputStream> byteArrayOutputStreamPool;
    private final ObjectPool<ByteBuffer> byteBufferPool;
    private final ByteBufAllocator byteBufAllocator;

    // 緩存
    private final Cache<String, ByteBuf> responseCache;
    private final Cache<String, Object> metadataCache;

    // 內存監控
    private final ScheduledExecutorService memoryMonitor;
    private final AtomicLong totalAllocatedBytes = new AtomicLong(0);
    private final AtomicLong totalDeallocatedBytes = new AtomicLong(0);
    private final AtomicLong peakMemoryUsage = new AtomicLong(0);

    public MemoryOptimizer() {
        // 初始化對象池
        this.byteArrayOutputStreamPool = createByteArrayOutputStreamPool();
        this.byteBufferPool = createByteBufferPool();
        this.byteBufAllocator = PooledByteBufAllocator.DEFAULT;

        // 初始化緩存
        this.responseCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .removalListener(notification -> {
                    // 釋放 ByteBuf
                    if (notification.getValue() instanceof ByteBuf) {
                        ((ByteBuf) notification.getValue()).release();
                    }
                })
                .build();

        this.metadataCache = CacheBuilder.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();

        // 初始化內存監控
        this.memoryMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "memory-monitor");
            t.setDaemon(true);
            return t;
        });

        startMemoryMonitoring();
        logger.info("MemoryOptimizer initialized with object pools and caches");
    }

    /**
     * 創建 ByteArrayOutputStream 對象池
     */
    private ObjectPool<ByteArrayOutputStream> createByteArrayOutputStreamPool() {
        GenericObjectPoolConfig<ByteArrayOutputStream> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(100);
        config.setMaxIdle(20);
        config.setMinIdle(5);
        config.setTestOnBorrow(false);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        config.setMinEvictableIdleTime(Duration.ofMinutes(5));

        return new GenericObjectPool<>(new ByteArrayOutputStreamFactory(), config);
    }

    /**
     * 創建 ByteBuffer 對象池
     */
    private ObjectPool<ByteBuffer> createByteBufferPool() {
        GenericObjectPoolConfig<ByteBuffer> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(200);
        config.setMaxIdle(50);
        config.setMinIdle(10);
        config.setTestOnBorrow(false);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        config.setMinEvictableIdleTime(Duration.ofMinutes(5));

        return new GenericObjectPool<>(new ByteBufferFactory(), config);
    }

    /**
     * 借用 ByteArrayOutputStream
     */
    public ByteArrayOutputStream borrowByteArrayOutputStream() {
        try {
            ByteArrayOutputStream baos = byteArrayOutputStreamPool.borrowObject();
            baos.reset(); // 重置流
            return baos;
        } catch (Exception e) {
            logger.warn("Failed to borrow ByteArrayOutputStream from pool, creating new instance", e);
            return new ByteArrayOutputStream();
        }
    }

    /**
     * 歸還 ByteArrayOutputStream
     */
    public void returnByteArrayOutputStream(ByteArrayOutputStream baos) {
        try {
            byteArrayOutputStreamPool.returnObject(baos);
        } catch (Exception e) {
            logger.warn("Failed to return ByteArrayOutputStream to pool", e);
        }
    }

    /**
     * 借用 ByteBuffer
     */
    public ByteBuffer borrowByteBuffer() {
        try {
            ByteBuffer buffer = byteBufferPool.borrowObject();
            buffer.clear(); // 重置緩衝區
            return buffer;
        } catch (Exception e) {
            logger.warn("Failed to borrow ByteBuffer from pool, creating new instance", e);
            return ByteBuffer.allocate(8192);
        }
    }

    /**
     * 歸還 ByteBuffer
     */
    public void returnByteBuffer(ByteBuffer buffer) {
        try {
            byteBufferPool.returnObject(buffer);
        } catch (Exception e) {
            logger.warn("Failed to return ByteBuffer to pool", e);
        }
    }

    /**
     * 分配 ByteBuf
     */
    public ByteBuf allocateByteBuf(int capacity) {
        ByteBuf buffer = byteBufAllocator.buffer(capacity);
        totalAllocatedBytes.addAndGet(capacity);
        return buffer;
    }

    /**
     * 釋放 ByteBuf
     */
    public void releaseByteBuf(ByteBuf buffer) {
        if (buffer != null && buffer.refCnt() > 0) {
            int capacity = buffer.capacity();
            buffer.release();
            totalDeallocatedBytes.addAndGet(capacity);
        }
    }

    /**
     * 創建零拷貝 ByteBuf
     */
    public ByteBuf createZeroCopyByteBuf(byte[] data) {
        return Unpooled.wrappedBuffer(data);
    }

    /**
     * 緩存響應數據
     */
    public void cacheResponse(String key, ByteBuf response) {
        if (response != null && response.isReadable()) {
            // 增加引用計數以防止被釋放
            response.retain();
            responseCache.put(key, response);
        }
    }

    /**
     * 獲取緩存的響應數據
     */
    public ByteBuf getCachedResponse(String key) {
        ByteBuf cached = responseCache.getIfPresent(key);
        if (cached != null && cached.isReadable()) {
            // 返回副本以避免多線程問題
            return cached.retainedDuplicate();
        }
        return null;
    }

    /**
     * 緩存元數據
     */
    public void cacheMetadata(String key, Object metadata) {
        metadataCache.put(key, metadata);
    }

    /**
     * 獲取緩存的元數據
     */
    public Object getCachedMetadata(String key) {
        return metadataCache.getIfPresent(key);
    }

    /**
     * 清理緩存
     */
    public void cleanupCaches() {
        logger.debug("Cleaning up caches...");
        responseCache.cleanUp();
        metadataCache.cleanUp();
    }

    /**
     * 獲取內存統計信息
     */
    public MemoryStatistics getMemoryStatistics() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        return new MemoryStatistics(
                totalMemory,
                freeMemory,
                usedMemory,
                maxMemory,
                totalAllocatedBytes.get(),
                totalDeallocatedBytes.get(),
                peakMemoryUsage.get(),
                responseCache.size(),
                metadataCache.size()
        );
    }

    /**
     * 開始內存監控
     */
    private void startMemoryMonitoring() {
        memoryMonitor.scheduleAtFixedRate(() -> {
            try {
                monitorMemoryUsage();
                cleanupCaches();
            } catch (Exception e) {
                logger.error("Error during memory monitoring", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 監控內存使用情況
     */
    private void monitorMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        // 更新峰值內存使用量
        peakMemoryUsage.updateAndGet(current -> Math.max(current, usedMemory));

        // 檢查內存使用情況
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercentage = (double) usedMemory / maxMemory * 100;

        if (memoryUsagePercentage > 80) {
            logger.warn("High memory usage detected: {:.2f}% ({} MB / {} MB)",
                    memoryUsagePercentage, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024);

            // 主動清理緩存
            cleanupCaches();

            // 建議 GC
            if (memoryUsagePercentage > 90) {
                logger.warn("Critical memory usage, suggesting GC");
                System.gc();
            }
        }

        // 記錄統計信息
        if (logger.isDebugEnabled()) {
            MemoryStatistics stats = getMemoryStatistics();
            logger.debug("Memory stats: {}", stats);
        }
    }

    /**
     * 強制清理內存
     */
    public void forceCleanup() {
        logger.info("Forcing memory cleanup...");

        // 清理緩存
        responseCache.invalidateAll();
        metadataCache.invalidateAll();

        // 清理對象池
        try {
            byteArrayOutputStreamPool.clear();
            byteBufferPool.clear();
        } catch (Exception e) {
            logger.warn("Error clearing object pools", e);
        }

        // 建議 GC
        System.gc();

        logger.info("Memory cleanup completed");
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down MemoryOptimizer...");

        // 停止監控
        memoryMonitor.shutdown();
        try {
            if (!memoryMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                memoryMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            memoryMonitor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 清理資源
        forceCleanup();

        // 關閉對象池
        try {
            byteArrayOutputStreamPool.close();
            byteBufferPool.close();
        } catch (Exception e) {
            logger.warn("Error closing object pools", e);
        }

        logger.info("MemoryOptimizer shutdown completed");
    }

    /**
     * ByteArrayOutputStream 工廠
     */
    private static class ByteArrayOutputStreamFactory extends BasePooledObjectFactory<ByteArrayOutputStream> {
        @Override
        public ByteArrayOutputStream create() {
            return new ByteArrayOutputStream(8192); // 8KB 初始容量
        }

        @Override
        public PooledObject<ByteArrayOutputStream> wrap(ByteArrayOutputStream obj) {
            return new DefaultPooledObject<>(obj);
        }

        @Override
        public void passivateObject(PooledObject<ByteArrayOutputStream> p) {
            p.getObject().reset(); // 重置流
        }

        @Override
        public boolean validateObject(PooledObject<ByteArrayOutputStream> p) {
            return p.getObject() != null;
        }
    }

    /**
     * ByteBuffer 工廠
     */
    private static class ByteBufferFactory extends BasePooledObjectFactory<ByteBuffer> {
        @Override
        public ByteBuffer create() {
            return ByteBuffer.allocateDirect(8192); // 8KB 直接緩衝區
        }

        @Override
        public PooledObject<ByteBuffer> wrap(ByteBuffer obj) {
            return new DefaultPooledObject<>(obj);
        }

        @Override
        public void passivateObject(PooledObject<ByteBuffer> p) {
            p.getObject().clear(); // 重置緩衝區
        }

        @Override
        public boolean validateObject(PooledObject<ByteBuffer> p) {
            return p.getObject() != null;
        }
    }

    /**
     * 內存統計信息
     */
    public static class MemoryStatistics {
        private final long totalMemory;
        private final long freeMemory;
        private final long usedMemory;
        private final long maxMemory;
        private final long totalAllocatedBytes;
        private final long totalDeallocatedBytes;
        private final long peakMemoryUsage;
        private final long responseCacheSize;
        private final long metadataCacheSize;

        public MemoryStatistics(long totalMemory, long freeMemory, long usedMemory, long maxMemory,
                                long totalAllocatedBytes, long totalDeallocatedBytes, long peakMemoryUsage,
                                long responseCacheSize, long metadataCacheSize) {
            this.totalMemory = totalMemory;
            this.freeMemory = freeMemory;
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
            this.totalAllocatedBytes = totalAllocatedBytes;
            this.totalDeallocatedBytes = totalDeallocatedBytes;
            this.peakMemoryUsage = peakMemoryUsage;
            this.responseCacheSize = responseCacheSize;
            this.metadataCacheSize = metadataCacheSize;
        }

        // Getters
        public long getTotalMemory() { return totalMemory; }
        public long getFreeMemory() { return freeMemory; }
        public long getUsedMemory() { return usedMemory; }
        public long getMaxMemory() { return maxMemory; }
        public long getTotalAllocatedBytes() { return totalAllocatedBytes; }
        public long getTotalDeallocatedBytes() { return totalDeallocatedBytes; }
        public long getPeakMemoryUsage() { return peakMemoryUsage; }
        public long getResponseCacheSize() { return responseCacheSize; }
        public long getMetadataCacheSize() { return metadataCacheSize; }

        public double getMemoryUsagePercentage() {
            return maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
        }

        public long getNetAllocatedBytes() {
            return totalAllocatedBytes - totalDeallocatedBytes;
        }

        @Override
        public String toString() {
            return String.format("MemoryStatistics{used=%dMB/%.1f%%, peak=%dMB, " +
                            "netAllocated=%dMB, responseCacheSize=%d, metadataCacheSize=%d}",
                    usedMemory / 1024 / 1024, getMemoryUsagePercentage(),
                    peakMemoryUsage / 1024 / 1024, getNetAllocatedBytes() / 1024 / 1024,
                    responseCacheSize, metadataCacheSize);
        }
    }
}