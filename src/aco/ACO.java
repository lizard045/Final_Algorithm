package aco;

import core.DAG;
import core.Heuristics;
import core.Schedule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * ACO類別：實現螞蟻群優化演算法 (Ant Colony Optimization)
 * **NEW**: 升級為最大最小螞蟻系統 (Max-Min Ant System, MMAS)
 */
public class ACO {
    private final DAG dag;
    private final int numAnts;
    private final int generations;
    private final double alpha;
    private final double beta;
    private final double evaporationRate;
    private final double localSearchRate;
    private double q0;
    private final double initial_q0;
    private final double elitistWeight;
    private final int numRankedAnts;
    private final double[][] pheromoneMatrix;
    private final double pheromoneSmoothingFactor;
    
    private Schedule bestSchedule;
    // **STABILITY**: Fixed random seed for reproducible results
    private final Random random = new Random(42);
    
    // **CONVERGENCE**: Tracking best solutions for convergence detection
    private List<Double> convergenceData;
    private Schedule[] recentBestSolutions = new Schedule[10]; // Track last 10 best solutions
    private int recentBestIndex = 0;
    
    // **PERFORMANCE**: Pre-computed upward ranks
    private double[] cachedUpwardRanks;
    
    // MMAS parameters
    private double tau_max;
    private double tau_min;
    
    // **ENHANCED STAGNATION**: More sophisticated stagnation handling
    private int stagnationCounter = 0;
    private int convergenceCounter = 0; // **NEW**: Track convergence stability
    private double lastBestMakespan = Double.MAX_VALUE;
    private static final int SOFT_STAGNATION_LIMIT = 25; // **REDUCED**: More responsive
    private static final int HARD_STAGNATION_LIMIT = 50; // **REDUCED**: Faster reset
    private static final int CONVERGENCE_THRESHOLD = 30; // **NEW**: Stop if converged
    private static final double CONVERGENCE_TOLERANCE = 0.01; // **NEW**: Convergence sensitivity
    private static final double MUTATION_RATE_ON_STAGNATION = 0.05; // **NEW**: Mutation rate for the best solution
    
    // **STABILITY**: Diversity protection
    private static final double MIN_DIVERSITY_THRESHOLD = 0.1;
    private int diversityCounter = 0;

    public ACO(int numAnts, int generations, double alpha, double beta, double evaporationRate, double localSearchRate, double q0, double elitistWeight, int numRankedAnts, double pheromoneSmoothingFactor, String dagFile) {
        this.dag = new DAG();
        try {
            this.dag.loadFromFile(dagFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DAG file: " + dagFile, e);
        }
        this.numAnts = numAnts;
        this.generations = generations;
        this.alpha = alpha;
        this.beta = beta;
        this.evaporationRate = evaporationRate;
        this.localSearchRate = localSearchRate;
        this.q0 = q0;
        this.initial_q0 = q0;
        this.elitistWeight = elitistWeight;
        this.numRankedAnts = numRankedAnts;
        this.pheromoneSmoothingFactor = pheromoneSmoothingFactor;
        
        this.pheromoneMatrix = new double[dag.getTaskCount()][dag.getProcessorCount()];
        this.convergenceData = new ArrayList<>();
        
        // **PERFORMANCE**: Pre-compute and cache upward ranks once
        this.cachedUpwardRanks = Heuristics.calculateUpwardRanks(dag);
    }

    private void initializePheromones() {
        // 在 MMAS 中，資訊素被初始化為上限 tau_max
        for (int i = 0; i < dag.getTaskCount(); i++) {
            for (int j = 0; j < dag.getProcessorCount(); j++) {
                pheromoneMatrix[i][j] = tau_max;
            }
        }
    }

    public Schedule run() {
        // 1. 產生初始解以計算 tau_max，但不將其設為全域最佳解
        Schedule initialHeuristicSchedule = Heuristics.createPeftSchedule(dag);
        System.out.printf("Initial Heuristic Makespan (PEFT): %.2f\n", initialHeuristicSchedule.getMakespan());
        
        // 2. 初始化 MMAS 參數
        tau_max = 1.0 / (evaporationRate * initialHeuristicSchedule.getMakespan());
        // 根據文獻，為 tau_min 設定一個合理的計算方式
        double p_best = Math.pow(1.0 / dag.getTaskCount(), 1.0 / dag.getTaskCount());
        tau_min = tau_max * (1 - p_best) / ((double)(dag.getTaskCount() / 2 - 1) * p_best);

        System.out.printf("MMAS Params: tau_max=%.4f, tau_min=%.4f\n", tau_max, tau_min);

        // **NEW**: ACO 的搜尋從 null 開始，不使用 PEFT 作為起點
        bestSchedule = null; 

        // 3. 初始化資訊素矩陣
        initializePheromones();

        for (int gen = 0; gen < generations; gen++) {
            // **NEW**: Dynamic elitist weight decay
            double currentElitistWeight = this.elitistWeight * (1.0 - (double) gen / generations);

            List<Ant> ants = createAnts();
            
            // **STABILITY**: Use fixed random seed for each generation
            Random genRandom = new Random(42 + gen);
            
            for (Ant ant : ants) {
                ant.constructSolution(dag, pheromoneMatrix, alpha, beta, q0, cachedUpwardRanks);
            }

            // --- STRATEGY CHANGE: Decouple Local Search from population generation ---
            // Sort ants by their raw constructed solution to find the best of this iteration.
            ants.sort(Comparator.comparingDouble(a -> a.getSchedule().getMakespan()));
            Schedule iterationBestAntSchedule = ants.get(0).getSchedule();

            // Local search is now only applied to refine a new candidate for the global best solution.
            boolean foundNewGlobalBest = false;
            if (bestSchedule == null || iterationBestAntSchedule.getMakespan() < bestSchedule.getMakespan()) {
                Schedule refinedCandidate = new Schedule(iterationBestAntSchedule);
                refinedCandidate.criticalPathLocalSearch(); // Apply powerful LS to the promising candidate

                if (bestSchedule == null || refinedCandidate.getMakespan() < bestSchedule.getMakespan()) {
                    bestSchedule = refinedCandidate; // Update global best with the refined version
                    foundNewGlobalBest = true;
                    System.out.printf("  -> New global best found (after LS): %.2f\n", bestSchedule.getMakespan());
                }
            }

            // Update stagnation and convergence counters based on whether a new global best was found.
            if (foundNewGlobalBest) {
                stagnationCounter = 0;

                // **NEW ADAPTIVE CONTROL**: Improvement found, increase exploitation pressure.
                this.q0 = Math.min(0.98, this.q0 / 0.95); // Increase q0, but cap it to avoid pure greediness.
                System.out.printf("  -> Global best improved. Increasing q0 to %.4f\n", this.q0);

                // **CONVERGENCE**: Update tracking
                if (Math.abs(bestSchedule.getMakespan() - lastBestMakespan) < CONVERGENCE_TOLERANCE) {
                    convergenceCounter++;
                } else {
                    convergenceCounter = 0;
                    lastBestMakespan = bestSchedule.getMakespan();
                }
                 // Reset q0 to its initial value if it was lowered
                if (this.q0 < this.initial_q0) {
                    this.q0 = this.initial_q0;
                    System.out.printf("  -> Resetting q0 to %.2f\n", this.q0);
                }
            } else {
                stagnationCounter++;
                // Check if we are stuck near the same solution
                if (bestSchedule != null && Math.abs(iterationBestAntSchedule.getMakespan() - bestSchedule.getMakespan()) < CONVERGENCE_TOLERANCE) {
                    convergenceCounter++;
                } else {
                    convergenceCounter = 0;
                }
            }
            
            // **STABILITY**: Track recent best solutions for diversity analysis
            recentBestSolutions[recentBestIndex] = new Schedule(iterationBestAntSchedule);
            recentBestIndex = (recentBestIndex + 1) % recentBestSolutions.length;

            // 4. 更新資訊素 (based on the original ant solutions)
            updatePheromones(ants, bestSchedule, currentElitistWeight);

            // 5. **ENHANCED**: Advanced stagnation and diversity handling
            Schedule mutatedSolution = handleAdvancedStagnation(ants);
            
            // **NEW**: If stagnation produced a mutated solution, inject it into the next generation
            if (mutatedSolution != null) {
                // Replace the worst ant's schedule with the mutated one
                ants.get(ants.size() - 1).setSchedule(mutatedSolution);
            }

            // Record data for convergence curve
            convergenceData.add(bestSchedule.getMakespan());

            System.out.printf("Generation %d: Iteration Best (Ant)=%.2f, Global Best=%.2f, Stagnation=%d, Convergence=%d\n",
                    gen + 1, iterationBestAntSchedule.getMakespan(), bestSchedule.getMakespan(), 
                    stagnationCounter, convergenceCounter);

            // **CONVERGENCE**: Early stopping if converged
            if (convergenceCounter >= CONVERGENCE_THRESHOLD) {
                System.out.printf("  -> Algorithm converged after %d generations! Stopping early.\n", gen + 1);
                break;
            }
        }
        
        System.out.printf("Finished ACO run. Best Makespan: %.2f\n", bestSchedule.getMakespan());
        return bestSchedule;
    }

    private List<Ant> createAnts() {
        List<Ant> ants = new ArrayList<>();
        for (int i = 0; i < numAnts; i++) {
            ants.add(new Ant());
        }
        return ants;
    }

    /**
     * **REVISED**: Implements Rank-Based pheromone update.
     * @param sortedAnts The list of ants from the current generation, sorted by makespan.
     * @param globalBest The best solution found so far over all generations.
     * @param currentElitistWeight The dynamic weight for the elitist ant.
     */
    private void updatePheromones(List<Ant> sortedAnts, Schedule globalBest, double currentElitistWeight) {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        
        // 1. 資訊素蒸發 - **PERFORMANCE**: Optimized loop structure
        double evapFactor = 1.0 - evaporationRate;
        for (int i = 0; i < taskCount; i++) {
            double[] pheromoneRow = pheromoneMatrix[i]; // **PERFORMANCE**: Cache row reference
            for (int j = 0; j < processorCount; j++) {
                pheromoneRow[j] *= evapFactor;
            }
        }

        // 2. **NEW**: Rank-based update from the top ants
        for (int k = 0; k < this.numRankedAnts; k++) {
            if (k >= sortedAnts.size()) break;
            
            Schedule s = sortedAnts.get(k).getSchedule();
            // **ENHANCED**: Improved weight distribution for ASrank
            double contribution = (this.numRankedAnts - k + 1.0) * (1.0 / s.getMakespan());
            
            for (int taskId = 0; taskId < taskCount; taskId++) {
                int processorId = s.getProcessorForTask(taskId);
                if (processorId != -1) {
                    pheromoneMatrix[taskId][processorId] += contribution;
                }
            }
        }
        
        // 3. 全域最佳解 (精英螞蟻) 貢獻資訊素
        if (globalBest != null) {
            double contribution = currentElitistWeight * (1.0 / globalBest.getMakespan());
            for (int taskId = 0; taskId < taskCount; taskId++) {
                int processorId = globalBest.getProcessorForTask(taskId);
                if (processorId != -1) {
                    pheromoneMatrix[taskId][processorId] += contribution;
                }
            }
        }
        
        // 4. 強制執行資訊素上下限 - **PERFORMANCE**: Optimized bounds checking
        for (int i = 0; i < taskCount; i++) {
            double[] pheromoneRow = pheromoneMatrix[i]; // **PERFORMANCE**: Cache row reference
            for (int j = 0; j < processorCount; j++) {
                if (pheromoneRow[j] > tau_max) {
                    pheromoneRow[j] = tau_max;
                } else if (pheromoneRow[j] < tau_min) {
                    pheromoneRow[j] = tau_min;
                }
            }
        }

        // 5. **NEW**: Pheromone Smoothing to proactively encourage exploration
        if (pheromoneSmoothingFactor > 0) {
            double totalPheromone = 0;
            for (int i = 0; i < taskCount; i++) {
                for (int j = 0; j < processorCount; j++) {
                    totalPheromone += pheromoneMatrix[i][j];
                }
            }
            double avgPheromone = totalPheromone / (taskCount * processorCount);
            
            for (int i = 0; i < taskCount; i++) {
                for (int j = 0; j < processorCount; j++) {
                    pheromoneMatrix[i][j] = (1.0 - pheromoneSmoothingFactor) * pheromoneMatrix[i][j] + pheromoneSmoothingFactor * avgPheromone;
                }
            }
        }
    }

    /**
     * **NEW**: Advanced stagnation handling with diversity protection. Returns a mutated solution if hard stagnation is triggered.
     */
    private Schedule handleAdvancedStagnation(List<Ant> ants) {
        // **DIVERSITY**: Calculate population diversity
        double diversity = calculatePopulationDiversity(ants);
        
        // **DIVERSITY**: Force diversity if too low
        if (diversity < MIN_DIVERSITY_THRESHOLD) {
            diversityCounter++;
            if (diversityCounter >= 5) {
                System.out.printf("  -> Low diversity detected (%.4f)! Forcing diversification.\n", diversity);
                forceDiversification();
                diversityCounter = 0;
            }
        } else {
            diversityCounter = 0;
        }
        
        // Level 2: Hard Stagnation -> **MODIFIED**: Force diversification AND mutate the best solution.
        if (stagnationCounter >= HARD_STAGNATION_LIMIT) {
            System.out.printf("  -> Hard stagnation! Forcing diversification and mutating best solution.\n");
            forceDiversification();
            
            // **NEW**: Mutate the global best schedule to inject new genetic material
            Schedule mutatedBest = new Schedule(bestSchedule);
            mutatedBest.mutate(MUTATION_RATE_ON_STAGNATION, random);
            mutatedBest.evaluateFitness(); // Re-evaluate makespan after mutation
            System.out.printf("  -> Mutated best solution from %.2f to %.2f\n", bestSchedule.getMakespan(), mutatedBest.getMakespan());

            // Also reset q0 to its initial value to start fresh
            if (this.q0 < this.initial_q0) {
                this.q0 = this.initial_q0;
                System.out.printf("  -> Resetting q0 to %.2f\n", this.q0);
            }
            stagnationCounter = 0; // Reset counter completely after action
            convergenceCounter = 0; // Reset convergence counter too
            return mutatedBest;
        } 
        // Level 1: Soft Stagnation -> Adjust q0, triggers every SOFT_STAGNATION_LIMIT generations
        else if (stagnationCounter > 0 && stagnationCounter % SOFT_STAGNATION_LIMIT == 0) {
            // **NEW ADAPTIVE CONTROL**: Stagnation detected, increase exploration.
            this.q0 = Math.max(0.3, this.q0 * 0.9); // More aggressive reduction, with a floor.
            System.out.printf("  -> Soft stagnation (Stagnation gens: %d)! Reducing q0 to %.4f.\n", stagnationCounter, this.q0);
        }
        return null; // No mutation occurred
    }

    /**
     * **NEW**: Calculate population diversity based on makespan variance.
     */
    private double calculatePopulationDiversity(List<Ant> ants) {
        if (ants.size() <= 1) return 1.0;
        
        double[] makespans = ants.stream()
            .mapToDouble(ant -> ant.getSchedule().getMakespan())
            .toArray();
        
        double mean = Arrays.stream(makespans).average().orElse(0.0);
        double variance = Arrays.stream(makespans)
            .map(x -> Math.pow(x - mean, 2))
            .average().orElse(0.0);
        
        return Math.sqrt(variance) / mean; // Normalized standard deviation
    }

    /**
     * **NEW**: Force diversification by partially randomizing pheromone matrix.
     */
    private void forceDiversification() {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        
        // **STABILITY**: Use controlled randomization
        Random diversityRandom = new Random(System.currentTimeMillis() % 1000);
        
        // Partially randomize 30% of pheromone values
        for (int i = 0; i < taskCount; i++) {
            for (int j = 0; j < processorCount; j++) {
                if (diversityRandom.nextDouble() < 0.3) {
                    // Set to random value between tau_min and tau_max
                    pheromoneMatrix[i][j] = tau_min + diversityRandom.nextDouble() * (tau_max - tau_min);
                }
            }
        }
    }

    public Schedule getBestSchedule() {
        return bestSchedule;
    }

    /**
     * **NEW**: Returns the convergence data.
     * @return A list of the best makespan found at each generation.
     */
    public List<Double> getConvergenceData() {
        return convergenceData;
    }
}