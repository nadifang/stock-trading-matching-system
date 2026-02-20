package com.stms.match.utils;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.TreeMap;

public class OrderBook {
    public TreeMap<Double, LinkedList<MatchingOrder>> buyBook = new TreeMap<>(Comparator.reverseOrder());
    public TreeMap<Double, LinkedList<MatchingOrder>> sellBook = new TreeMap<>();
}
