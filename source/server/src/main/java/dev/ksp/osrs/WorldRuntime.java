package dev.ksp.osrs;

import java.io.IOException;

interface WorldRuntime extends AutoCloseable {
    void start(int id) throws IOException;
    void stop(int id) throws IOException;
    boolean listening(int id);
    int port(int id);
    long attempts(int id);
    long ticks(int id);
    default int population(int id) { return 0; }
    @Override void close();
}
