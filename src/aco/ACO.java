package aco;

import core.DAG;
import core.Heuristics;
import core.Schedule;
import java.io.IOException;
import java.util.ArrayList;
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
    private final Random random = new Random();

    // **NEW**: To store convergence data
    private List<Double> convergenceData;

    // --- MMAS Parameters ---
    private double tau_max;
    private double tau_min;
    
    // **MODIFIED**: Stagnation Handling
    private int stagnationCounter = 0;
    private static final int SOFT_STAGNATION_LIMIT = 30;
    private static final int HARD_STAGNATION_LIMIT = 60;

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
            
            for (Ant ant : ants) {
                ant.constructSolution(dag, pheromoneMatrix, alpha, beta, q0);
            }

            // --- Apply local search to the top N ants of the iteration ---
            ants.sort(Comparator.comparingDouble(a -> a.getSchedule().getMakespan()));
            int numToSearch = (int) (numAnts * localSearchRate);
            for (int i = 0; i < numToSearch; i++) {
                ants.get(i).getSchedule().criticalPathLocalSearch();
            }

            // Find iteration best after local search
            Schedule iterationBestSchedule = ants.get(0).getSchedule();

            // 與全域最佳解比較 (處理 bestSchedule 初始化為 null 的情況)
            if (bestSchedule == null || iterationBestSchedule.getMakespan() < bestSchedule.getMakespan()) {
                bestSchedule = new Schedule(iterationBestSchedule);
                stagnationCounter = 0; // Found a better solution, reset counter
                
                // Reset q0 to its initial value if it was lowered
                if (this.q0 < this.initial_q0) {
                    this.q0 = this.initial_q0;
                    System.out.printf("  -> New global best found! Resetting q0 to %.2f\n", this.q0);
                }
            } else {
                 stagnationCounter++;
            }

            // 4. 更新資訊素
            updatePheromones(ants, bestSchedule);

            // 5. 處理停滯
            handleStagnation();
            
            // Record data for convergence curve
            convergenceData.add(bestSchedule.getMakespan());

            System.out.printf("Generation %d: Iteration Best=%.2f, Global Best=%.2f, Stagnation=%d/%d\n",
                    gen + 1, iterationBestSchedule.getMakespan(), bestSchedule.getMakespan(), stagnationCounter, HARD_STAGNATION_LIMIT);
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
        // 1. 資訊素蒸發
        for (int i = 0; i < dag.getTaskCount(); i++) {
            for (int j = 0; j < dag.getProcessorCount(); j++) {
                pheromoneMatrix[i][j] *= (1.0 - evaporationRate);
            }
        }

        // 2. **NEW**: Rank-based update from the top ants
        for (int k = 0; k < this.numRankedAnts; k++) {
            if (k >= sortedAnts.size()) break;
            
            Schedule s = sortedAnts.get(k).getSchedule();
            // **ENHANCED**: Improved weight distribution for ASrank
            double contribution = (this.numRankedAnts - k + 1.0) * (1.0 / s.getMakespan());
            
            for (int taskId = 0; taskId < dag.getTaskCount(); taskId++) {
                int processorId = s.getProcessorForTask(taskId);
                if (processorId != -1) {
                    pheromoneMatrix[taskId][processorId] += contribution;
                }
            }
        }
        
        // 3. 全域最佳解 (精英螞蟻) 貢獻資訊素
        if (globalBest != null) {
            double contribution = this.elitistWeight * (1.0 / globalBest.getMakespan());
            for (int taskId = 0; taskId < dag.getTaskCount(); taskId++) {
                int processorId = globalBest.getProcessorForTask(taskId);
                if (processorId != -1) {
                    pheromoneMatrix[taskId][processorId] += contribution;
                }
            }
        }
        
        // 4. 強制執行資訊素上下限
        for (int i = 0; i < dag.getTaskCount(); i++) {
            for (int j = 0; j < dag.getProcessorCount(); j++) {
                if (pheromoneMatrix[i][j] > tau_max) {
                    pheromoneMatrix[i][j] = tau_max;
                } else if (pheromoneMatrix[i][j] < tau_min) {
                    pheromoneMatrix[i][j] = tau_min;
                }
            }
        }
    }

    /**
     * **REVISED**: Hybrid stagnation handling.
     */
    private void handleStagnation() {
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
        } 
        // Level 1: Soft Stagnation -> Adjust q0, triggers every SOFT_STAGNATION_LIMIT generations
        else if (stagnationCounter > 0 && stagnationCounter % SOFT_STAGNATION_LIMIT == 0) {
            this.q0 = Math.max(0.5, this.q0 * 0.95); // Decrease q0, with a floor of 0.5
            System.out.printf("  -> Soft stagnation (Stagnation gens: %d)! Reducing q0 to %.4f.\n", stagnationCounter, this.q0);
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