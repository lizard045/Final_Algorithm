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
    private static final int CONVERGENCE_THRESHOLD = 15; // **NEW**: Stop if converged
    private static final double CONVERGENCE_TOLERANCE = 0.01; // **NEW**: Convergence sensitivity
    
    // **STABILITY**: Diversity protection
    private static final double MIN_DIVERSITY_THRESHOLD = 0.1;
    private int diversityCounter = 0;

    public ACO(int numAnts, int generations, double alpha, double beta, double evaporationRate, double localSearchRate, double q0, double elitistWeight, int numRankedAnts, String dagFile) {
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
            List<Ant> ants = createAnts();
            
            // **STABILITY**: Use fixed random seed for each generation
            Random genRandom = new Random(42 + gen);
            
            for (Ant ant : ants) {
                ant.constructSolution(dag, pheromoneMatrix, alpha, beta, q0, cachedUpwardRanks);
            }

            // --- Apply local search to the top N ants of the iteration ---
            ants.sort(Comparator.comparingDouble(a -> a.getSchedule().getMakespan()));
            int numToSearch = (int) (numAnts * localSearchRate);
            for (int i = 0; i < numToSearch; i++) {
                ants.get(i).getSchedule().criticalPathLocalSearch();
            }

            // Find iteration best after local search
            Schedule iterationBestSchedule = ants.get(0).getSchedule();

            // **CONVERGENCE**: Check for convergence before updating best
            boolean foundImprovement = false;
            if (bestSchedule == null || iterationBestSchedule.getMakespan() < bestSchedule.getMakespan()) {
                bestSchedule = new Schedule(iterationBestSchedule);
                stagnationCounter = 0;
                foundImprovement = true;
                
                // **CONVERGENCE**: Update tracking
                if (Math.abs(iterationBestSchedule.getMakespan() - lastBestMakespan) < CONVERGENCE_TOLERANCE) {
                    convergenceCounter++;
                } else {
                    convergenceCounter = 0;
                    lastBestMakespan = iterationBestSchedule.getMakespan();
                }
                
                // Reset q0 to its initial value if it was lowered
                if (this.q0 < this.initial_q0) {
                    this.q0 = this.initial_q0;
                    System.out.printf("  -> New global best found! Resetting q0 to %.2f\n", this.q0);
                }
            } else {
                stagnationCounter++;
                
                // **CONVERGENCE**: Check if we're stuck at the same solution
                if (Math.abs(iterationBestSchedule.getMakespan() - bestSchedule.getMakespan()) < CONVERGENCE_TOLERANCE) {
                    convergenceCounter++;
                } else {
                    convergenceCounter = 0;
                }
            }

            // **STABILITY**: Track recent best solutions for diversity analysis
            recentBestSolutions[recentBestIndex] = new Schedule(iterationBestSchedule);
            recentBestIndex = (recentBestIndex + 1) % recentBestSolutions.length;

            // 4. 更新資訊素
            updatePheromones(ants, bestSchedule);

            // 5. **ENHANCED**: Advanced stagnation and diversity handling
            handleAdvancedStagnation(ants);
            
            // Record data for convergence curve
            convergenceData.add(bestSchedule.getMakespan());

            System.out.printf("Generation %d: Iteration Best=%.2f, Global Best=%.2f, Stagnation=%d, Convergence=%d\n",
                    gen + 1, iterationBestSchedule.getMakespan(), bestSchedule.getMakespan(), 
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
     */
    private void updatePheromones(List<Ant> sortedAnts, Schedule globalBest) {
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
            double contribution = this.elitistWeight * (1.0 / globalBest.getMakespan());
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
    }

    /**
     * **NEW**: Advanced stagnation handling with diversity protection.
     */
    private void handleAdvancedStagnation(List<Ant> ants) {
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
        
        // Level 2: Hard Stagnation -> Reset Pheromones
        if (stagnationCounter >= HARD_STAGNATION_LIMIT) {
            System.out.printf("  -> Hard stagnation! Resetting pheromones to tau_max (%.4f).\n", tau_max);
            initializePheromones();
            
            // Also reset q0 to its initial value to start fresh
            if (this.q0 < this.initial_q0) {
                this.q0 = this.initial_q0;
                System.out.printf("  -> Resetting q0 to %.2f\n", this.q0);
            }
            stagnationCounter = 0; // Reset counter completely after hard reset
            convergenceCounter = 0; // Reset convergence counter too
        } 
        // Level 1: Soft Stagnation -> Adjust q0, triggers every SOFT_STAGNATION_LIMIT generations
        else if (stagnationCounter > 0 && stagnationCounter % SOFT_STAGNATION_LIMIT == 0) {
            this.q0 = Math.max(0.4, this.q0 * 0.9); // **ENHANCED**: More aggressive reduction
            System.out.printf("  -> Soft stagnation (Stagnation gens: %d)! Reducing q0 to %.4f.\n", stagnationCounter, this.q0);
        }
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