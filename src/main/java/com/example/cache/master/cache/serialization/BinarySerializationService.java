package com.example.cache.master.cache.serialization;

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
    private static final int LZ4_HEADER_BYTES = 5;

    private final SerializationProperties properties;
    private final KryoPayloadSerializer kryoPayloadSerializer;
    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;

    public BinarySerializationService(SerializationProperties properties, KryoPayloadSerializer kryoPayloadSerializer) {
        this.properties = properties;
        this.kryoPayloadSerializer = kryoPayloadSerializer;
        LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
        this.compressor = lz4Factory.fastCompressor();
        this.decompressor = lz4Factory.fastDecompressor();
    }

    public byte[] serialize(Object value) {
        byte[] payload = kryoPayloadSerializer.serialize(value);
        if (!shouldCompress(payload.length)) {
            return withPlainHeader(payload);
        }
        return withLz4Header(payload);
    }

    public <T> T deserialize(byte[] data, Class<T> type) {
        validateFrame(data);
        if (data[0] == HEADER_PLAIN) {
            return kryoPayloadSerializer.deserialize(data, 1, data.length - 1, type);
        }
        if (data[0] == HEADER_LZ4) {
            byte[] payload = inflate(data);
            return kryoPayloadSerializer.deserialize(payload, 0, payload.length, type);
        }
        throw new SerializationException("Unknown payload header: " + data[0]);
    }

    private boolean shouldCompress(int payloadLength) {
        return properties.isCompressionEnabled() && payloadLength > properties.getCompressionThresholdBytes();
    }

    private void validateFrame(byte[] data) {
        if (data == null || data.length == 0) {
            throw new SerializationException("Payload is empty");
        }
    }

    private byte[] withPlainHeader(byte[] payload) {
        byte[] framed = new byte[payload.length + 1];
        framed[0] = HEADER_PLAIN;
        System.arraycopy(payload, 0, framed, 1, payload.length);
        return framed;
    }

    private byte[] withLz4Header(byte[] payload) {
        int maxCompressed = compressor.maxCompressedLength(payload.length);
        byte[] framed = new byte[maxCompressed + LZ4_HEADER_BYTES];
        framed[0] = HEADER_LZ4;
        ByteBuffer.wrap(framed, 1, 4).putInt(payload.length);
        int length = compressor.compress(payload, 0, payload.length, framed, LZ4_HEADER_BYTES, maxCompressed);
        byte[] sized = new byte[length + LZ4_HEADER_BYTES];
        System.arraycopy(framed, 0, sized, 0, sized.length);
        return sized;
    }

    private byte[] inflate(byte[] frame) {
        int payloadLength = ByteBuffer.wrap(frame, 1, 4).getInt();
        byte[] payload = new byte[payloadLength];
        decompressor.decompress(frame, LZ4_HEADER_BYTES, payload, 0, payloadLength);
        return payload;
    }
}
