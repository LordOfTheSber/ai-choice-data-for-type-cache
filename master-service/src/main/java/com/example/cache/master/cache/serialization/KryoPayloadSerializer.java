package com.example.cache.master.cache.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.example.cache.master.cache.config.KryoConfig.KryoFactory;
import com.example.cache.master.cache.config.SerializationProperties;
import com.example.cache.master.cache.error.SerializationException;
import org.springframework.stereotype.Component;

@Component
public class KryoPayloadSerializer {
    private final ThreadLocal<Kryo> kryoPerThread;
    private final ThreadLocal<Output> outputPerThread;

    public KryoPayloadSerializer(KryoFactory kryoFactory, SerializationProperties properties) {
        this.kryoPerThread = ThreadLocal.withInitial(kryoFactory::create);
        this.outputPerThread = ThreadLocal.withInitial(() -> createOutputBuffer(properties));
    }

    public byte[] serialize(Object value) {
        try {
            Output output = outputPerThread.get();
            output.reset();
            kryoPerThread.get().writeClassAndObject(output, value);
            return output.toBytes();
        } catch (RuntimeException exception) {
            throw new SerializationException("Failed to serialize payload with Kryo", exception);
        }
    }

    public <T> T deserialize(byte[] data, int offset, int length, Class<T> type) {
        try {
            Input input = new Input(data, offset, length);
            Object value = kryoPerThread.get().readClassAndObject(input);
            return cast(type, value);
        } catch (RuntimeException exception) {
            throw new SerializationException("Failed to deserialize payload with Kryo", exception);
        }
    }

    private Output createOutputBuffer(SerializationProperties properties) {
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
