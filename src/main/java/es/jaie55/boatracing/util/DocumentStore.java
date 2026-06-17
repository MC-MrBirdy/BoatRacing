package es.jaie55.boatracing.util;

import java.io.IOException;

public interface DocumentStore extends AutoCloseable {
    String read(String documentName) throws IOException;

    void write(String documentName, String content) throws IOException;

    @Override
    default void close() throws IOException {
    }
}
