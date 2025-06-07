import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the Island Model for Genetic Algorithms.
 * This class manages multiple populations (islands) of schedules,
 * evolving them in parallel and periodically migrating the best individuals
 * between them to enhance diversity and escape local optima.
 */
public class IslandModelGA {
    private final int numIslands;
    private final int migrationInterval; // In generations
    private final int migrationSize; // Number of individuals to migrate
    private final List<GA> islands;
    private final int totalGenerations;
    private final String dagFile;

    public IslandModelGA(
        int numIslands, 
        int totalGenerations, 
        int migrationInterval, 
        int migrationSize,
        int populationPerIsland,
        double mutationRate,
        double localSearchRate,
        String dagFile) {
        
        this.numIslands = numIslands;
        this.totalGenerations = totalGenerations;
        this.migrationInterval = migrationInterval;
        this.migrationSize = migrationSize;
        this.dagFile = dagFile;
        this.islands = new ArrayList<>();

        for (int i = 0; i < numIslands; i++) {
            // Each island is a standard GA instance
            islands.add(new GA(populationPerIsland, totalGenerations, mutationRate, localSearchRate, dagFile));
        }
    }

    public Schedule run() {
        System.out.printf("Starting Island Model GA with %d islands.\n", numIslands);
        
        // Initial evolution
        for (GA island : islands) {
            island.initializePopulation();
        }

        for (int gen = 0; gen < totalGenerations; gen++) {
            // Evolve each island independently
            for (GA island : islands) {
                island.evolveOnce(); // A new method to evolve for a single generation
            }

            // Perform migration periodically
            if ((gen + 1) % migrationInterval == 0 && gen < totalGenerations - 1) {
                System.out.printf("--- Migration at Generation %d ---\n", gen + 1);
                performMigration();
            }
        }
        
        // Find the best schedule among all islands at the end
        return findBestScheduleOverall();
    }

    private void performMigration() {
        // Collect the best individuals from each island
        List<Schedule> migrants = new ArrayList<>();
        for (GA island : islands) {
            migrants.addAll(island.getBestSchedules(migrationSize));
        }
        
        // Shuffle migrants to ensure random distribution
        Collections.shuffle(migrants);
        
        // Distribute migrants to the next island in a ring topology
        for (int i = 0; i < numIslands; i++) {
            GA currentIsland = islands.get(i);
            GA nextIsland = islands.get((i + 1) % numIslands);
            
            // Get the required number of migrants for the next island
            List<Schedule> migrantsForNextIsland = migrants.stream()
                .limit(migrationSize)
                .collect(Collectors.toList());
            
            nextIsland.receiveMigrants(migrantsForNextIsland);
            
            // Rotate the list for the next iteration
            if (!migrants.isEmpty()) {
                 Collections.rotate(migrants, -migrationSize);
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
        System.out.println("\nIsland Model GA finished. Final best makespan: " + (bestOverall != null ? bestOverall.getMakespan() : "N/A"));
        return bestOverall;
    }
} 