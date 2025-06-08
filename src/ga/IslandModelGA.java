package ga;

import core.Heuristics;
import core.Schedule;
import java.util.*;

/**
 * Implements the Island Model for Genetic Algorithms with enhanced, on-demand communication.
 * This class manages multiple populations (islands), evolving them in parallel.
 * Migration is no longer time-based; instead, a stagnating island actively
 * requests elite individuals from the current best-performing island.
 */
public class IslandModelGA {
    private final int numIslands;
    private final int migrationSize; // Number of individuals to migrate
    private final List<GA> islands;
    private final int totalGenerations;

    public IslandModelGA(
        int numIslands, 
        int totalGenerations, 
        int migrationSize,
        int populationPerIsland,
        double mutationRate,
        double localSearchRate,
        String dagFile) {
        
        this.numIslands = numIslands;
        this.totalGenerations = totalGenerations;
        this.migrationSize = migrationSize;
        this.islands = new ArrayList<>();

        for (int i = 0; i < numIslands; i++) {
            islands.add(new GA(populationPerIsland, totalGenerations, mutationRate, localSearchRate, dagFile));
        }
    }

    public Schedule run() {
        System.out.printf("Starting Island Model GA with %d islands (Enhanced On-Demand Migration).\n", numIslands);
        
        for (GA island : islands) {
            island.initializePopulation();
        }

        for (int gen = 0; gen < totalGenerations; gen++) {
            // Evolve each island independently
            for (GA island : islands) {
                island.evolveOnce();
            }

            // Perform migration on-demand based on stagnation
            performDynamicMigration(gen);
        }
        
        return findBestScheduleOverall();
    }

    private void performDynamicMigration(int generation) {
        // 1. Find the best island to be the source of elite migrants
        GA bestIsland = null;
        Schedule bestOverallSchedule = null;

        for (GA island : islands) {
            Schedule bestInIsland = island.getBestSchedule();
            if (bestOverallSchedule == null || (bestInIsland != null && bestInIsland.getMakespan() < bestOverallSchedule.getMakespan())) {
                bestOverallSchedule = bestInIsland;
                bestIsland = island;
            }
        }

        if (bestIsland == null) return; // Should not happen in a populated model

        // 2. Check for stagnating islands and perform migration from the best island
        boolean migrationHeaderPrinted = false;
        for (GA island : islands) {
            // An island requests help if it's stagnating AND it's not the best one
            if (island != bestIsland && island.isStagnating()) {
                if (!migrationHeaderPrinted) {
                    System.out.printf("--- Dynamic Migration & Path-Relinking at Generation %d ---\n", generation + 1);
                    if (bestOverallSchedule != null) {
                        System.out.printf("  - Best island (Best Makespan: %.2f) is the guide.\n", bestOverallSchedule.getMakespan());
                    }
                    migrationHeaderPrinted = true;
                }
                
                System.out.printf("  - Island (Best: %.2f) is stagnating, initiating help protocol.\n", island.getBestSchedule().getMakespan());
                
                // --- Path-Relinking Step ---
                Schedule sourceSchedule = island.getBestSchedule();
                Schedule guidingSchedule = bestIsland.getBestSchedule();
                Schedule pathRelinkingResult = Heuristics.pathRelinking(sourceSchedule, guidingSchedule);
                System.out.printf("    - Path-Relinking explored from %.2f to %.2f, found new best: %.2f\n", 
                                  sourceSchedule.getMakespan(), guidingSchedule.getMakespan(), pathRelinkingResult.getMakespan());

                // --- Migration Step ---
                List<Schedule> migrants = bestIsland.getBestSchedules(migrationSize);
                migrants.add(pathRelinkingResult); // Add the PL result to the migrant pool
                
                island.receiveMigrants(migrants);
            }
        }
    }

    private Schedule findBestScheduleOverall() {
        Schedule bestOverall = null;
        for (GA island : islands) {
            Schedule bestInIsland = island.getBestSchedule();
            if (bestOverall == null || (bestInIsland != null && bestInIsland.getMakespan() < bestOverall.getMakespan())) {
                bestOverall = bestInIsland;
            }
        }
        System.out.println("\nIsland Model GA finished. Final best makespan: " + (bestOverall != null ? String.format("%.2f", bestOverall.getMakespan()) : "N/A"));
        return bestOverall;
    }
} 