package ru.ifmo.java.one.kek;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class Algorithms {
    public static List<Integer> sort(List<Integer> arr) {
        List<Integer> result = new ArrayList<>();

        while (!arr.isEmpty()) {
            result.add(arr.stream().min(Comparator.comparing(Function.identity())).get());
            arr.remove(result.get(result.size() - 1));
        }

        return result;
    }

    private Algorithms() {}
}
