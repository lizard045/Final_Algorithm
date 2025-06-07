import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GA類別：實現記憶演算法 (Memetic Algorithm)
 * 結合了遺傳演算法與局部搜尋
 */
public class GA {
    private final DAG dag;
    private List<Schedule> population;
    private final int populationSize;
    private final int generations;
    private final double mutationRate;
    private final double localSearchRate;
    private final Random random = new Random();
    private Schedule bestSchedule;
    
    private final double explorationMutationRate; // For exploration mode
    private final double explorationLocalSearchRate;
    private static final int STAGNATION_LIMIT = 30; // Generations
    private static final int EXPLORATION_DURATION = 15; // Generations
    private int stagnationCounter = 0;
    private int explorationCounter = 0;

    public GA(int populationSize, int generations, double mutationRate, double localSearchRate, String dagFile) {
        this.dag = new DAG();
        try {
            this.dag.loadFromFile(dagFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DAG file: " + dagFile, e);
        }
        this.populationSize = populationSize;
        this.generations = generations;
        this.mutationRate = mutationRate;
        this.localSearchRate = localSearchRate;
        // Set exploration rates relative to standard rates
        this.explorationMutationRate = Math.min(1.0, mutationRate * 5); 
        this.explorationLocalSearchRate = Math.max(0.0, localSearchRate / 5);
        this.population = new ArrayList<>(populationSize);
    }
    
    public Schedule getBestSchedule() {
        return bestSchedule;
    }

    public void initializePopulation() {
        // Initialize with one HEFT solution
        Schedule heftSchedule = Heuristics.createHeftSchedule(dag);
        heftSchedule.evaluateFitness();
        population.add(heftSchedule);
        bestSchedule = heftSchedule.clone();
        
        // Fill the rest with random solutions
        while (population.size() < populationSize) {
            Schedule randomSchedule = createRandomSchedule();
            randomSchedule.evaluateFitness();
            population.add(randomSchedule);
            if (randomSchedule.getMakespan() < bestSchedule.getMakespan()) {
                bestSchedule = randomSchedule.clone();
            }
        }
    }

    public Schedule run() {
        initializePopulation();

        for (int i = 0; i < generations; i++) {
            evolveOnce();
        }
        
        System.out.printf("Finished GA run. Best Makespan: %.2f\n", bestSchedule.getMakespan());
        return bestSchedule;
    }

    /**
     * Executes a single generation of the genetic algorithm.
     * This is used by the IslandModelGA to control evolution step-by-step.
     */
    public void evolveOnce() {
        List<Schedule> newPopulation = new ArrayList<>();
        
        // Elitism: carry over the best schedule
        newPopulation.add(bestSchedule.clone());

        updateStagnationAndExploration();

        // Determine current rates based on exploration mode
        double currentMutationRate = (explorationCounter > 0) ? explorationMutationRate : mutationRate;
        double currentLocalSearchRate = (explorationCounter > 0) ? explorationLocalSearchRate : localSearchRate;

        while (newPopulation.size() < populationSize) {
            Schedule parent1 = selectParent();
            Schedule parent2 = selectParent();
            Schedule child = crossover(parent1, parent2);
            mutate(child, currentMutationRate);
            
            if (random.nextDouble() < currentLocalSearchRate) {
                child.criticalPathLocalSearch();
            }
            
            child.evaluateFitness();
            newPopulation.add(child);
        }

        population = newPopulation;
        updateBestSchedule();
    }
    
    /**
     * Gets the top N schedules from the current population.
     * @param count The number of best schedules to return.
     * @return A list of the best schedules.
     */
    public List<Schedule> getBestSchedules(int count) {
        population.sort(Comparator.comparingDouble(Schedule::getMakespan));
        return population.stream().limit(count).map(Schedule::clone).collect(Collectors.toList());
    }

    /**
     * Replaces the worst individuals in the population with new migrant schedules.
     * @param migrants The list of schedules to introduce into the population.
     */
    public void receiveMigrants(List<Schedule> migrants) {
        if (migrants == null || migrants.isEmpty()) {
            return;
        }
        // Sort population to find the worst
        population.sort(Comparator.comparingDouble(Schedule::getMakespan).reversed());
        
        for (int i = 0; i < migrants.size() && i < population.size(); i++) {
            population.set(i, migrants.get(i).clone()); // Replace worst with migrant
        }
        // After receiving migrants, re-evaluate the best schedule for the island
        updateBestSchedule();
        System.out.printf("  - Island received %d migrants. New best is %.2f\n", migrants.size(), bestSchedule.getMakespan());
    }

    private void updateBestSchedule() {
        boolean foundNewBest = false;
        for (Schedule s : population) {
            if (bestSchedule == null || s.getMakespan() < bestSchedule.getMakespan()) {
                bestSchedule = s.clone();
                foundNewBest = true;
            }
        }
        
        if (foundNewBest) {
            stagnationCounter = 0;
            // If we found a new best, we can exit exploration mode early
            if (explorationCounter > 0) {
                System.out.printf("  ** Island found new best: %.2f, exiting exploration mode. **\n", bestSchedule.getMakespan());
                explorationCounter = 0;
            }
        } else {
            stagnationCounter++;
        }
    }
    
    private void updateStagnationAndExploration() {
        if (explorationCounter > 0) {
            explorationCounter--;
            return; // Don't check for stagnation while exploring
        }

        if (stagnationCounter >= STAGNATION_LIMIT) {
             System.out.printf("  - Island stagnated. Best: %.2f. Triggering exploration mode for %d gens.\n", bestSchedule.getMakespan(), EXPLORATION_DURATION);
             explorationCounter = EXPLORATION_DURATION;
             stagnationCounter = 0;
        }
    }

    private Schedule selectParent() {
        int tournamentSize = 5;
        Schedule best = null;
        for (int i = 0; i < tournamentSize; i++) {
            Schedule randomSchedule = population.get(random.nextInt(population.size()));
            if (best == null || randomSchedule.getMakespan() < best.getMakespan()) {
                best = randomSchedule;
            }
        }
        return best;
    }

    private Schedule crossover(Schedule parent1, Schedule parent2) {
        int[] childChromosome = new int[dag.getTaskCount()];
        int[] p1Chromosome = parent1.getChromosome();
        int[] p2Chromosome = parent2.getChromosome();

        // Uniform Crossover
        for (int i = 0; i < dag.getTaskCount(); i++) {
            if (random.nextBoolean()) {
                childChromosome[i] = p1Chromosome[i];
            } else {
                childChromosome[i] = p2Chromosome[i];
            }
        }
        return new Schedule(dag, childChromosome);
    }
    
    private void mutate(Schedule schedule, double effectiveMutationRate) {
        int[] chromosome = schedule.getChromosome();
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < effectiveMutationRate) {
                // Smart mutation: prefer processors with lower computation cost for this task
                int currentTask = i;
                int currentProc = chromosome[currentTask];
                
                double minCost = Double.MAX_VALUE;
                int bestProc = currentProc;

                for (int p = 0; p < dag.getProcessorCount(); p++) {
                    double cost = dag.getTask(currentTask).getComputationCost(p);
                    if (cost < minCost) {
                        minCost = cost;
                        bestProc = p;
                    }
                }
                
                // Mutate to the best processor, or a random one if multiple are equally good
                if (bestProc != currentProc) {
                    chromosome[currentTask] = bestProc;
                } else {
                    // If already on the best, mutate to a random different one
                    int newProc = random.nextInt(dag.getProcessorCount());
                    while (newProc == currentProc) {
                        newProc = random.nextInt(dag.getProcessorCount());
                    }
                    chromosome[currentTask] = newProc;
                }
            }
        }
    }

    private Schedule createRandomSchedule() {
        int[] chromosome = new int[dag.getTaskCount()];
        List<Task> taskOrder = Heuristics.getRankedTasks(dag); // Use ranked order for better random schedules
        for (Task task : taskOrder) {
            int taskId = task.getTaskId();
            chromosome[taskId] = random.nextInt(dag.getProcessorCount());
        }
        return new Schedule(dag, chromosome);
    }
} 