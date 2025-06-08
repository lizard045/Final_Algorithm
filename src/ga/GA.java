package ga;

import core.DAG;
import core.Heuristics;
import core.Schedule;
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
    
    // --- 策略模式 ---
    private final CrossoverStrategy crossoverStrategy;
    private final MutationStrategy mutationStrategy;
    // ----------------

    // --- 快取機制 ---
    private final Map<String, Double> fitnessCache;
    // -----------------

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
        this.fitnessCache = new HashMap<>(); // 初始化快取

        // 初始化預設策略
        this.crossoverStrategy = new CombinedCrossoverStrategy();
        this.mutationStrategy = new CombinedMutationStrategy();
    }
    
    public Schedule getBestSchedule() {
        return bestSchedule;
    }

    public void initializePopulation() {
        // **核心升級**: 使用 PEFT 演算法產生初始解和基準順序
        Schedule peftSchedule = Heuristics.createPeftSchedule(dag);
        evaluate(peftSchedule);
        population.add(peftSchedule);
        bestSchedule = peftSchedule.clone();
        
        // Fill the rest with PEFT order but random assignments for diversity
        List<Integer> peftOrder = peftSchedule.getTaskOrder();
        while (population.size() < populationSize) {
            Schedule randomSchedule = createRandomScheduleWithFixedOrder(peftOrder);
            evaluate(randomSchedule);
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
            System.out.printf("Generation %d: Best Makespan=%.2f, Stagnation=%d/%d, Exploring=%d\n",
                i + 1, bestSchedule.getMakespan(), stagnationCounter, STAGNATION_LIMIT, explorationCounter);
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
            Schedule child = crossoverStrategy.crossover(parent1, parent2, dag, random);
            mutationStrategy.mutate(child, currentMutationRate, dag, random);
            
            evaluate(child);

            // **核心優化**: 根據是否處於探索模式，採用不同的局部搜尋策略
            boolean performLocalSearch = false;
            if (explorationCounter > 0) {
                // 在探索模式下，以固定機率進行局部搜尋，以充分探索新區域
                if (random.nextDouble() < currentLocalSearchRate) {
                    performLocalSearch = true;
                }
            } else {
                // 在常規模式下，只對有潛力的子代（優於父代）進行局部搜尋
                if (random.nextDouble() < currentLocalSearchRate && 
                    (child.getMakespan() < parent1.getMakespan() || child.getMakespan() < parent2.getMakespan())) {
                    performLocalSearch = true;
                }
            }
            
            if (performLocalSearch) {
                child.criticalPathLocalSearch();
                // 局部搜尋後，schedule的狀態已改變，需要重新加入快取
                evaluate(child);
            }
            
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
        // After receiving migrants, reset stagnation/exploration and re-evaluate the best schedule
        stagnationCounter = 0;
        explorationCounter = 0; // Also exit any ongoing exploration
        updateBestSchedule();
        System.out.printf("  - Island received %d migrants. New best is %.2f. Stagnation reset.\n", migrants.size(), bestSchedule.getMakespan());
    }

    /**
     * Checks if the island is currently in a state of stagnation.
     * @return true if the stagnation counter has reached the limit, false otherwise.
     */
    public boolean isStagnating() {
        return stagnationCounter >= STAGNATION_LIMIT;
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

    private Schedule createRandomScheduleWithFixedOrder(List<Integer> order) {
        int[] chromosome = new int[dag.getTaskCount()];
        for (int i = 0; i < dag.getTaskCount(); i++) {
            chromosome[i] = random.nextInt(dag.getProcessorCount());
        }
        Schedule schedule = new Schedule(dag, chromosome);
        schedule.setTaskOrder(new ArrayList<>(order)); // Use a copy of the order
        return schedule;
    }

    /**
     * **NEW**: 評估排程的適應度，使用快取來避免重複計算。
     * 這是所有適應度評估的統一入口。
     */
    private double evaluate(Schedule schedule) {
        if (schedule == null) return Double.MAX_VALUE;

        // **NEW**: 使用快取
        String key = schedule.getCacheKey();
        if (fitnessCache.containsKey(key)) {
            // 從快取中獲取後，手動設定到 schedule 物件上
            double cachedMakespan = fitnessCache.get(key);
            schedule.setMakespan(cachedMakespan);
            return cachedMakespan;
        }

        double makespan = schedule.evaluateFitness();
        fitnessCache.put(key, makespan);
        return makespan;
    }
} 