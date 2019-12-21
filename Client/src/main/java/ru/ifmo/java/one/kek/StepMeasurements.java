package ru.ifmo.java.one.kek;

public class StepMeasurements {
    final double request;
    final double client;
    final double serverResponse;

    public StepMeasurements(double request, double client, double serverResponse) {
        this.request = request;
        this.client = client;
        this.serverResponse = serverResponse;
    }

    public double getClient() {
        return client;
    }

    public double getRequest() {
        return request;
    }

    public double getServerResponse() {
        return serverResponse;
    }
}
