package ru.ifmo.java.one.kek;

public class StepConfig {
    final int numberOfClients;
    final int numberOfElements;
    final long delta;

    public StepConfig(int numberOfClients, int numberOfElements, long delta) {
        this.numberOfClients = numberOfClients;
        this.numberOfElements = numberOfElements;
        this.delta = delta;
    }

    public int getNumberOfClients() {
        return numberOfClients;
    }

    public int getNumberOfElements() {
        return numberOfElements;
    }

    public long getDelta() {
        return delta;
    }
}
