package com.example.cache.master.cache;

import org.springframework.beans.factory.annotation.Value;
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

/**
 * Simple L3 disk cache stub: key -> file. Optimized serialization can be switched
 * to Kryo + compression later.
 */
@Component
public class DiskRegionCache implements CacheStore {
    private static final int LOCK_STRIPES = 64;

    private final Path baseDirectory;
    private final ReentrantLock[] locks;

    public DiskRegionCache(@Value("${cache.l3.path:./data/l3-cache}") String baseDirectory) {
        this.baseDirectory = Paths.get(baseDirectory);
        this.locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            this.locks[i] = new ReentrantLock();
        }
        try {
            Files.createDirectories(this.baseDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize disk cache directory: " + this.baseDirectory, e);
        }
    }

    @Override
    public Optional<CacheValue> get(String key) {
        Path file = pathForKey(key);
        ReentrantLock lock = lockForKey(key);
        lock.lock();
        try {
            if (!Files.exists(file)) {
                return Optional.empty();
            }

            DiskRecord record = readRecord(file);
            if (record == null) {
                return Optional.empty();
            }

            if (isExpired(record.ttlMillis(), record.createdAtEpochMillis())) {
                Files.deleteIfExists(file);
                return Optional.empty();
            }

            // TODO(Kryo integration): deserialize payload bytes into typed object via Kryo.
            // For now we return raw bytes as Object placeholder.
            CacheValue value = new CacheValue(
                record.payload(),
                record.dataClass(),
                Duration.ofMillis(record.ttlMillis()),
                CacheStatus.HIT,
                record.version()
            );
            return Optional.of(value);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read disk cache file for key=" + key, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(String key, CacheValue value) {
        Path file = pathForKey(key);
        ReentrantLock lock = lockForKey(key);
        lock.lock();
        try {
            if (Files.exists(file)) {
                DiskRecord existing = readRecord(file);
                if (existing != null && existing.version() > value.getVersion()) {
                    return;
                }
            }

            byte[] payload = toPayloadBytes(value.getValue());
            Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");

            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmpFile))) {
                out.writeUTF(value.getDataClass().name());
                out.writeUTF(value.getStatus().name());
                out.writeLong(value.getVersion());
                out.writeLong(value.getTtl().toMillis());
                out.writeLong(Instant.now().toEpochMilli());
                out.writeInt(payload.length);
                out.write(payload);
            }

            try {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write disk cache file for key=" + key, e);
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
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete disk cache file for key=" + key, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(String key) {
        return get(key).isPresent();
    }

    private Path pathForKey(String key) {
        return baseDirectory.resolve(hashKey(key) + ".bin");
    }

    private ReentrantLock lockForKey(String key) {
        return locks[Math.floorMod(key.hashCode(), LOCK_STRIPES)];
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private boolean isExpired(long ttlMillis, long createdAtMillis) {
        if (ttlMillis <= 0) {
            return false;
        }
        long ageMillis = Instant.now().toEpochMilli() - createdAtMillis;
        return ageMillis >= ttlMillis;
    }

    private byte[] toPayloadBytes(Object value) {
        if (value == null) {
            return new byte[0];
        }

        if (value instanceof byte[] bytes) {
            return bytes;
        }

        // TODO(Kryo integration): replace with Kryo serialization + optional LZ4 compression.
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private DiskRecord readRecord(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            DataClass dataClass = DataClass.valueOf(in.readUTF());
            CacheStatus status = CacheStatus.valueOf(in.readUTF());
            long version = in.readLong();
            long ttlMillis = in.readLong();
            long createdAtEpochMillis = in.readLong();
            int payloadLength = in.readInt();
            byte[] payload = in.readNBytes(payloadLength);
            return new DiskRecord(dataClass, status, version, ttlMillis, createdAtEpochMillis, payload);
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
    }
}
