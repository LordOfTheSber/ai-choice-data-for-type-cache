package com.example.cache.master.cache.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.example.cache.master.cache.config.KryoConfig.KryoFactory;
import com.example.cache.master.cache.config.SerializationProperties;
import com.example.cache.master.cache.error.SerializationException;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Service
public class BinarySerializationService {
    private static final byte HEADER_PLAIN = 0;
    private static final byte HEADER_LZ4 = 1;

    private final SerializationProperties properties;
    private final ThreadLocal<Kryo> kryoPerThread;
    private final ThreadLocal<Output> outputPerThread;
    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;

    public BinarySerializationService(KryoFactory kryoFactory, SerializationProperties properties) {
        this.properties = properties;
        this.kryoPerThread = ThreadLocal.withInitial(kryoFactory::create);
        this.outputPerThread = ThreadLocal.withInitial(this::createOutputBuffer);
        LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
        this.compressor = lz4Factory.fastCompressor();
        this.decompressor = lz4Factory.fastDecompressor();
    }

    public byte[] serialize(Object value) {
        try {
            byte[] rawPayload = serializeRaw(value);
            if (shouldCompress(rawPayload.length)) {
                return withLz4Header(rawPayload);
            }
            return withPlainHeader(rawPayload);
        } catch (RuntimeException exception) {
            throw new SerializationException("Failed to serialize cache payload", exception);
        }
    }

    public <T> T deserialize(byte[] data, Class<T> type) {
        try {
            Object value = deserializeByHeader(data);
            return cast(type, value);
        } catch (RuntimeException exception) {
            throw new SerializationException("Failed to deserialize cache payload to " + type.getName(), exception);
        }
    }

    private byte[] serializeRaw(Object value) {
        Output output = outputPerThread.get();
        output.reset();
        kryoPerThread.get().writeClassAndObject(output, value);
        return output.toBytes();
    }

    private Object deserializeByHeader(byte[] data) {
        if (data == null || data.length == 0) {
            throw new SerializationException("Payload is empty");
        }
        byte header = data[0];
        if (header == HEADER_PLAIN) {
            return deserializeRaw(data, 1, data.length - 1);
        }
        if (header == HEADER_LZ4) {
            byte[] rawPayload = inflate(data);
            return deserializeRaw(rawPayload, 0, rawPayload.length);
        }
        throw new SerializationException("Unknown payload header: " + header);
    }

    private byte[] withPlainHeader(byte[] rawPayload) {
        byte[] framed = new byte[rawPayload.length + 1];
        framed[0] = HEADER_PLAIN;
        System.arraycopy(rawPayload, 0, framed, 1, rawPayload.length);
        return framed;
    }

    private byte[] withLz4Header(byte[] rawPayload) {
        int compressedLimit = compressor.maxCompressedLength(rawPayload.length);
        byte[] compressedBuffer = new byte[compressedLimit + 5];
        compressedBuffer[0] = HEADER_LZ4;
        ByteBuffer.wrap(compressedBuffer, 1, 4).putInt(rawPayload.length);
        int compressedLength = compressor.compress(rawPayload, 0, rawPayload.length, compressedBuffer, 5, compressedLimit);
        byte[] framed = new byte[compressedLength + 5];
        System.arraycopy(compressedBuffer, 0, framed, 0, framed.length);
        return framed;
    }

    private byte[] inflate(byte[] data) {
        int rawLength = ByteBuffer.wrap(data, 1, 4).getInt();
        byte[] rawPayload = new byte[rawLength];
        decompressor.decompress(data, 5, rawPayload, 0, rawLength);
        return rawPayload;
    }

    private Object deserializeRaw(byte[] bytes, int offset, int length) {
        Input input = new Input(bytes, offset, length);
        return kryoPerThread.get().readClassAndObject(input);
    }

    private boolean shouldCompress(int payloadLength) {
        return properties.isCompressionEnabled() && payloadLength > properties.getCompressionThresholdBytes();
    }

    private Output createOutputBuffer() {
        return new Output(properties.getOutputBufferInitialBytes(), properties.getOutputBufferMaxBytes());
    }

    private <T> T cast(Class<T> type, Object value) {
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new SerializationException("Payload type mismatch, expected " + type.getName());
        }
        return type.cast(value);
    }
}
