package ru.ifmo.java.one.kek;

public class InclusiveRange<T> {
    final T left;
    final T right;
    final T step;

    public InclusiveRange(T left, T right, T step) {
        this.left = left;
        this.right = right;
        this.step = step;
    }
}
