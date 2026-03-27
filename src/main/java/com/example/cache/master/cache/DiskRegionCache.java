package com.example.cache.master.cache;

import com.example.cache.master.cache.config.CacheProperties;
import com.example.cache.master.cache.error.CacheIoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class DiskRegionCache implements CacheStore {
    private static final Logger log = LoggerFactory.getLogger(DiskRegionCache.class);

    private final Path baseDirectory;
    private final ReentrantLock[] stripes;

    public DiskRegionCache(CacheProperties cacheProperties) {
        this.baseDirectory = Paths.get(cacheProperties.getL3().getPath());
        this.stripes = createLocks(cacheProperties.getL3().getLockStripes());
        createDirectory(baseDirectory);
    }

    @Override
    public Optional<CacheValue> get(String key) {
        ReentrantLock lock = lockForKey(key);
        lock.lock();
        try {
            return readValue(key);
        } catch (IOException exception) {
            throw new CacheIoException("Failed to read disk cache for key=" + key, exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(String key, CacheValue value) {
        ReentrantLock lock = lockForKey(key);
        lock.lock();
        try {
            if (isStaleWrite(key, value.getVersion())) {
                log.debug("Skip stale L3 update for key={} version={}", key, value.getVersion());
                return;
            }
            writeRecordAtomically(pathForKey(key), DiskRecord.fromValue(value));
        } catch (IOException exception) {
            throw new CacheIoException("Failed to write disk cache for key=" + key, exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delete(String key) {
        ReentrantLock lock = lockForKey(key);
        lock.lock();
        try {
            Files.deleteIfExists(pathForKey(key));
        } catch (IOException exception) {
            throw new CacheIoException("Failed to delete disk cache for key=" + key, exception);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(String key) {
        return get(key).isPresent();
    }

    private Optional<CacheValue> readValue(String key) throws IOException {
        Path path = pathForKey(key);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        DiskRecord record = readRecord(path);
        if (isExpired(record.ttlMillis(), record.createdAtEpochMillis())) {
            Files.deleteIfExists(path);
            return Optional.empty();
        }
        return Optional.of(record.toCacheHit());
    }

    private boolean isStaleWrite(String key, long incomingVersion) throws IOException {
        Path path = pathForKey(key);
        if (!Files.exists(path)) {
            return false;
        }
        return readRecord(path).version() > incomingVersion;
    }

    private void writeRecordAtomically(Path targetPath, DiskRecord record) throws IOException {
        Path tempPath = temporaryPath(targetPath);
        writeRecord(tempPath, record);
        moveAtomically(tempPath, targetPath);
    }

    private void writeRecord(Path path, DiskRecord record) throws IOException {
        try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(path))) {
            stream.writeUTF(record.dataClass().name());
            stream.writeUTF(record.status().name());
            stream.writeLong(record.version());
            stream.writeLong(record.ttlMillis());
            stream.writeLong(record.createdAtEpochMillis());
            stream.writeInt(record.payload().length);
            stream.write(record.payload());
        }
    }

    private DiskRecord readRecord(Path path) throws IOException {
        try (DataInputStream stream = new DataInputStream(Files.newInputStream(path))) {
            DataClass dataClass = DataClass.valueOf(stream.readUTF());
            CacheStatus status = CacheStatus.valueOf(stream.readUTF());
            long version = stream.readLong();
            long ttlMillis = stream.readLong();
            long createdAtEpochMillis = stream.readLong();
            int payloadLength = stream.readInt();
            byte[] payload = stream.readNBytes(payloadLength);
            return new DiskRecord(dataClass, status, version, ttlMillis, createdAtEpochMillis, payload);
        }
    }

    private void moveAtomically(Path tempPath, Path targetPath) throws IOException {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException moveException) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ReentrantLock[] createLocks(int lockStripes) {
        ReentrantLock[] locks = new ReentrantLock[lockStripes];
        for (int index = 0; index < lockStripes; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    private void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new CacheIoException("Failed to create disk cache directory: " + directory, exception);
        }
    }

    private boolean isExpired(long ttlMillis, long createdAtMillis) {
        if (ttlMillis <= 0L) {
            return false;
        }
        long ageMillis = Instant.now().toEpochMilli() - createdAtMillis;
        return ageMillis >= ttlMillis;
    }

    private Path pathForKey(String key) {
        return baseDirectory.resolve(hashKey(key) + ".bin");
    }

    private Path temporaryPath(Path targetPath) {
        return targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
    }

    private ReentrantLock lockForKey(String key) {
        return stripes[Math.floorMod(key.hashCode(), stripes.length)];
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new CacheIoException("SHA-256 is unavailable", exception);
        }
    }

    private record DiskRecord(
        DataClass dataClass,
        CacheStatus status,
        long version,
        long ttlMillis,
        long createdAtEpochMillis,
        byte[] payload
    ) {
        private static DiskRecord fromValue(CacheValue value) {
            byte[] payload = toPayloadBytes(value.getValue());
            long createdAt = Instant.now().toEpochMilli();
            return new DiskRecord(
                value.getDataClass(),
                value.getStatus(),
                value.getVersion(),
                value.getTtl().toMillis(),
                createdAt,
                payload
            );
        }

        private CacheValue toCacheHit() {
            // TODO: replace raw payload handoff with preconfigured Kryo deserializer.
            return new CacheValue(payload, dataClass, Duration.ofMillis(ttlMillis), CacheStatus.HIT, version);
        }

        private static byte[] toPayloadBytes(Object value) {
            if (value == null) {
                return new byte[0];
            }
            if (value instanceof byte[] bytes) {
                return bytes;
            }
            // TODO: replace with Kryo serializer and optional LZ4 compression pipeline.
            return value.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
