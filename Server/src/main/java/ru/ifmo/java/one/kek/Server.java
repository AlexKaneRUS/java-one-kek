package ru.ifmo.java.one.kek;

public abstract class Server implements Runnable {
    protected final MeasurementsGatherer gatherer;

    public Server(MeasurementsGatherer gatherer) {
        this.gatherer = gatherer;
    }

    abstract void close();

    MeasurementsGatherer getGatherer() {
        return gatherer;
    }
}
