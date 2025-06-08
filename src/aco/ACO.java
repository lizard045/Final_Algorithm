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
    private final double q0;
    private final double[][] pheromoneMatrix;

    private Schedule bestSchedule;
    private final Random random = new Random();

    // **NEW**: To store convergence data
    private List<Double> convergenceData;

    // --- MMAS Parameters ---
    private double tau_max;
    private double tau_min;
    
    // **NEW**: Stagnation Handling
    private int stagnationCounter = 0;
    private static final int STAGNATION_LIMIT = 30; // Generations before reset

    public ACO(int numAnts, int generations, double alpha, double beta, double evaporationRate, double localSearchRate, double q0, String dagFile) {
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
            } else {
                 stagnationCounter++;
            }

            // 4. **REVERT**: 更新資訊素改用「當代最佳解」，以增加探索
            updatePheromones(iterationBestSchedule);

            // 5. 處理停滯
            handleStagnation();
            
            // Record data for convergence curve
            convergenceData.add(bestSchedule.getMakespan());

            System.out.printf("Generation %d: Iteration Best=%.2f, Global Best=%.2f, Stagnation=%d/%d\n",
                    gen + 1, iterationBestSchedule.getMakespan(), bestSchedule.getMakespan(), stagnationCounter, STAGNATION_LIMIT);
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
     * **NEW**: MMAS 資訊素更新規則
     * @param updateSchedule 用於更新資訊素的排程 (通常是當代最佳或全域最佳)
     */
    private void updatePheromones(Schedule updateSchedule) {
        // 1. 資訊素蒸發
        for (int i = 0; i < dag.getTaskCount(); i++) {
            for (int j = 0; j < dag.getProcessorCount(); j++) {
                pheromoneMatrix[i][j] *= (1.0 - evaporationRate);
            }
        }

        // 2. 資訊素增加 (只由指定的 schedule 貢獻)
        double contribution = 1.0 / updateSchedule.getMakespan();
        for (int taskId = 0; taskId < dag.getTaskCount(); taskId++) {
            int processorId = updateSchedule.getProcessorForTask(taskId);
            if (processorId != -1) {
                pheromoneMatrix[taskId][processorId] += contribution;
            }
        }
        
        // 3. 強制執行資訊素上下限
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
     * **NEW**: 處理停滯，如果需要則重置資訊素
     */
    private void handleStagnation() {
        if (stagnationCounter >= STAGNATION_LIMIT) {
            System.out.printf("  -> Stagnation detected! Resetting pheromones to tau_max (%.4f).\n", tau_max);
            initializePheromones();
            stagnationCounter = 0; // Reset after action
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