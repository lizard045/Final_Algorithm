package aco;

import core.DAG;
import core.Heuristics;
import core.Schedule;
import java.io.IOException;
import java.util.ArrayList;
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
    private final double[][] pheromoneMatrix;

    private Schedule bestSchedule;
    private final Random random = new Random();

    // --- MMAS Parameters ---
    private double tau_max;
    private double tau_min;
    
    // **NEW**: Stagnation Handling
    private int stagnationCounter = 0;
    private static final int STAGNATION_LIMIT = 30; // Generations before reset

    public ACO(int numAnts, int generations, double alpha, double beta, double evaporationRate, double localSearchRate, String dagFile) {
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
        
        this.pheromoneMatrix = new double[dag.getTaskCount()][dag.getProcessorCount()];
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
        // 1. 產生初始解以計算 tau_max
        bestSchedule = Heuristics.createPeftSchedule(dag);
        System.out.printf("Initial Best Makespan (PEFT): %.2f\n", bestSchedule.getMakespan());
        
        // 2. 初始化 MMAS 參數
        tau_max = 1.0 / (evaporationRate * bestSchedule.getMakespan());
        // 根據文獻，為 tau_min 設定一個合理的計算方式
        double p_best = Math.pow(1.0 / dag.getTaskCount(), 1.0 / dag.getTaskCount());
        tau_min = tau_max * (1 - p_best) / ((double)(dag.getTaskCount() / 2 - 1) * p_best);

        System.out.printf("MMAS Params: tau_max=%.4f, tau_min=%.4f\n", tau_max, tau_min);

        // 3. 初始化資訊素矩陣
        initializePheromones();

        for (int gen = 0; gen < generations; gen++) {
            List<Ant> ants = createAnts();
            Schedule iterationBestSchedule = null;
            
            // 記錄本代開始前的最佳Makespan，用於偵測停滯
            double makespanBeforeGeneration = bestSchedule.getMakespan();

            for (Ant ant : ants) {
                ant.constructSolution(dag, pheromoneMatrix, alpha, beta);
                
                if (random.nextDouble() < this.localSearchRate) {
                    ant.getSchedule().criticalPathLocalSearch();
                }

                if (iterationBestSchedule == null || ant.getSchedule().getMakespan() < iterationBestSchedule.getMakespan()) {
                    iterationBestSchedule = ant.getSchedule();
                }
            }

            // 與全域最佳解比較
            if (iterationBestSchedule != null && iterationBestSchedule.getMakespan() < bestSchedule.getMakespan()) {
                bestSchedule = iterationBestSchedule.clone();
            }

            // 4. 更新資訊素 (MMAS 核心)
            updatePheromones(iterationBestSchedule);

            // 5. **NEW**: 處理停滯
            handleStagnation(makespanBeforeGeneration);
            
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
     * @param bestMakespanBeforeUpdate 在本代更新前的最佳 makespan
     */
    private void handleStagnation(double bestMakespanBeforeUpdate) {
        if (bestSchedule.getMakespan() >= bestMakespanBeforeUpdate) {
            stagnationCounter++;
        } else {
            stagnationCounter = 0; // Found a better solution, reset counter
        }

        if (stagnationCounter >= STAGNATION_LIMIT) {
            System.out.printf("  -> Stagnation detected! Resetting pheromones to tau_max (%.4f).\n", tau_max);
            initializePheromones();
            stagnationCounter = 0; // Reset after action
        }
    }

    public Schedule getBestSchedule() {
        return bestSchedule;
    }
} 