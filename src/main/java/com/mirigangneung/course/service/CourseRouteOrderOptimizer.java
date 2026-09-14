package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.CourseStop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Generates a small set of geographic route candidates for Kakao walking-distance evaluation. */
final class CourseRouteOrderOptimizer {
    private static final int MAX_CANDIDATE_ORDERS = 3;
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double MIN_IMPROVEMENT_METERS = 0.1;

    List<List<CourseStop>> candidateOrders(List<CourseStop> currentOrder) {
        if (currentOrder.size() < 2 || currentOrder.stream().anyMatch(CourseRouteOrderOptimizer::hasNoCoordinates)) {
            return List.of();
        }

        double[][] distances = distanceMatrix(currentOrder);
        List<Integer> currentIndices = indices(currentOrder.size());
        List<List<Integer>> uniqueOrders = new ArrayList<>();

        for (int start = 0; start < currentOrder.size(); start++) {
            List<Integer> nearestNeighborOrder = nearestNeighborOrder(start, distances);
            improveWithTwoOpt(nearestNeighborOrder, distances);
            addCandidate(uniqueOrders, nearestNeighborOrder, currentIndices);

            List<Integer> reverseOrder = new ArrayList<>(nearestNeighborOrder);
            java.util.Collections.reverse(reverseOrder);
            addCandidate(uniqueOrders, reverseOrder, currentIndices);
        }

        uniqueOrders.sort(Comparator.comparingDouble(order -> routeLength(order, distances)));
        return uniqueOrders.stream()
                .limit(MAX_CANDIDATE_ORDERS)
                .map(order -> order.stream().map(currentOrder::get).toList())
                .toList();
    }

    private static void addCandidate(
            List<List<Integer>> candidates,
            List<Integer> candidate,
            List<Integer> currentOrder
    ) {
        if (!candidate.equals(currentOrder) && !candidates.contains(candidate)) {
            candidates.add(List.copyOf(candidate));
        }
    }

    private static List<Integer> nearestNeighborOrder(int start, double[][] distances) {
        List<Integer> order = new ArrayList<>(distances.length);
        boolean[] visited = new boolean[distances.length];
        int current = start;
        order.add(current);
        visited[current] = true;

        while (order.size() < distances.length) {
            int nearest = -1;
            for (int candidate = 0; candidate < distances.length; candidate++) {
                if (visited[candidate]) {
                    continue;
                }
                if (nearest < 0 || distances[current][candidate] < distances[current][nearest]) {
                    nearest = candidate;
                }
            }
            order.add(nearest);
            visited[nearest] = true;
            current = nearest;
        }
        return order;
    }

    private static void improveWithTwoOpt(List<Integer> order, double[][] distances) {
        boolean improved;
        do {
            improved = false;
            double currentLength = routeLength(order, distances);
            for (int start = 0; start < order.size() - 1 && !improved; start++) {
                for (int end = start + 1; end < order.size(); end++) {
                    List<Integer> candidate = new ArrayList<>(order);
                    java.util.Collections.reverse(candidate.subList(start, end + 1));
                    if (routeLength(candidate, distances) + MIN_IMPROVEMENT_METERS < currentLength) {
                        order.clear();
                        order.addAll(candidate);
                        improved = true;
                        break;
                    }
                }
            }
        } while (improved);
    }

    private static double routeLength(List<Integer> order, double[][] distances) {
        double total = 0;
        for (int index = 1; index < order.size(); index++) {
            total += distances[order.get(index - 1)][order.get(index)];
        }
        return total;
    }

    private static double[][] distanceMatrix(List<CourseStop> stops) {
        double[][] distances = new double[stops.size()][stops.size()];
        for (int from = 0; from < stops.size(); from++) {
            for (int to = from + 1; to < stops.size(); to++) {
                CourseStop origin = stops.get(from);
                CourseStop destination = stops.get(to);
                double distance = haversineMeters(
                        origin.getLatitude(),
                        origin.getLongitude(),
                        destination.getLatitude(),
                        destination.getLongitude()
                );
                distances[from][to] = distance;
                distances[to][from] = distance;
            }
        }
        return distances;
    }

    private static double haversineMeters(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
        double latA = Math.toRadians(latitudeA);
        double latB = Math.toRadians(latitudeB);
        double latDelta = latB - latA;
        double lonDelta = Math.toRadians(longitudeB - longitudeA);
        double haversine = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.cos(latA) * Math.cos(latB) * Math.pow(Math.sin(lonDelta / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
    }

    private static boolean hasNoCoordinates(CourseStop stop) {
        return stop.getLatitude() == null || stop.getLongitude() == null;
    }

    private static List<Integer> indices(int size) {
        List<Integer> indices = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            indices.add(index);
        }
        return indices;
    }
}
