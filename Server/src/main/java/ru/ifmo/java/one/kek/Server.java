package ru.ifmo.java.one.kek;

public abstract class Server implements Runnable {
    protected final MetricsGatherer gatherer;

    public Server(MetricsGatherer gatherer) {
        this.gatherer = gatherer;
    }

    abstract void close();

    MetricsGatherer getGatherer() {
        return gatherer;
    }
}
