import java.util.*;

/**
 * 遺傳演算法核心類別
 * 實現GA的主要操作：選擇、交叉、突變、演化
 */
public class GeneticAlgorithm {
    // GA參數
    private int populationSize;      // 族群大小
    private int maxGenerations;      // 最大世代數
    private double crossoverRate;    // 交叉機率
    private double mutationRate;     // 突變機率
    private int eliteSize;          // 菁英個體數量
    private int tournamentSize;     // 錦標賽選擇大小
    
    // 核心組件
    private FitnessEvaluator fitnessEvaluator;
    private Random random;
    private DAGReader dagData;
    
    // 演化統計
    private List<Double> bestFitnessHistory;
    private List<Double> avgFitnessHistory;
    
    /**
     * 建構函數
     */
    public GeneticAlgorithm(DAGReader dagData, FitnessEvaluator fitnessEvaluator) {
        this.dagData = dagData;
        this.fitnessEvaluator = fitnessEvaluator;
        this.random = new Random();
        
        // 設定預設參數
        this.populationSize = 100;
        this.maxGenerations = 500;
        this.crossoverRate = 0.8;
        this.mutationRate = 0.1;
        this.eliteSize = 5;
        this.tournamentSize = 5;
        
        // 初始化統計記錄
        this.bestFitnessHistory = new ArrayList<>();
        this.avgFitnessHistory = new ArrayList<>();
    }
    
    /**
     * 設定GA參數
     */
    public void setParameters(int populationSize, int maxGenerations, 
                            double crossoverRate, double mutationRate,
                            int eliteSize, int tournamentSize) {
        this.populationSize = populationSize;
        this.maxGenerations = maxGenerations;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.eliteSize = eliteSize;
        this.tournamentSize = tournamentSize;
    }
    
    /**
     * 設定隨機種子
     */
    public void setSeed(long seed) {
        this.random = new Random(seed);
    }
    
    /**
     * 執行遺傳演算法
     */
    public Individual evolve() {
        // 清除歷史記錄
        bestFitnessHistory.clear();
        avgFitnessHistory.clear();
        
        // 初始化族群
        List<Individual> population = initializePopulation();
        
        // 評估初始族群
        evaluatePopulation(population);
        
        Individual bestSolution = new Individual(population.get(0));
        
        // 演化過程
        for (int generation = 0; generation < maxGenerations; generation++) {
            // 記錄統計資料
            recordGenerationStatistics(population, generation);
            
            // 更新最佳解
            Individual currentBest = population.get(0);
            if (currentBest.getFitness() < bestSolution.getFitness()) {
                bestSolution = new Individual(currentBest);
            }
            
            // 產生新世代
            List<Individual> newPopulation = generateNextGeneration(population);
            
            // 評估新世代
            evaluatePopulation(newPopulation);
            
            population = newPopulation;
            
            // 提早終止條件（可選）
            if (shouldTerminateEarly(generation)) {
                break;
            }
        }
        
        return bestSolution;
    }
    
    /**
     * 初始化族群
     */
    private List<Individual> initializePopulation() {
        List<Individual> population = new ArrayList<>();
        
        for (int i = 0; i < populationSize; i++) {
            Individual individual = new Individual(dagData.getTaskCount(), 
                                                 dagData.getProcessorCount());
            
            // 使用不同的初始化策略
            if (i < populationSize * 0.3) {
                // 30% 使用隨機初始化
                individual.randomInitialize(random);
            } else if (i < populationSize * 0.6) {
                // 30% 使用啟發式初始化
                heuristicInitialize(individual);
            } else {
                // 40% 使用混合初始化
                mixedInitialize(individual);
            }
            
            population.add(individual);
        }
        
        return population;
    }
    
    /**
     * 啟發式初始化（基於最小計算成本）
     */
    private void heuristicInitialize(Individual individual) {
        double[][] compCost = dagData.getCompCost();
        
        for (int taskId = 0; taskId < dagData.getTaskCount(); taskId++) {
            int bestProcessor = 0;
            double minCost = compCost[taskId][0];
            
            for (int processorId = 1; processorId < dagData.getProcessorCount(); processorId++) {
                if (compCost[taskId][processorId] < minCost) {
                    minCost = compCost[taskId][processorId];
                    bestProcessor = processorId;
                }
            }
            
            individual.setProcessorAssignment(taskId, bestProcessor);
        }
    }
    
    /**
     * 混合初始化（部分啟發式，部分隨機）
     */
    private void mixedInitialize(Individual individual) {
        heuristicInitialize(individual);
        
        // 隨機改變一些任務的分配
        int changeCount = random.nextInt(dagData.getTaskCount() / 3) + 1;
        for (int i = 0; i < changeCount; i++) {
            int taskId = random.nextInt(dagData.getTaskCount());
            int processorId = random.nextInt(dagData.getProcessorCount());
            individual.setProcessorAssignment(taskId, processorId);
        }
    }
    
    /**
     * 評估族群中所有個體的適應度
     */
    private void evaluatePopulation(List<Individual> population) {
        for (Individual individual : population) {
            if (individual.getFitness() == Double.MAX_VALUE) {
                double fitness = fitnessEvaluator.evaluateFitness(individual);
                individual.setFitness(fitness);
            }
        }
        
        // 按適應度排序（升序，適應度越小越好）
        Collections.sort(population);
    }
    
    /**
     * 產生下一世代
     */
    private List<Individual> generateNextGeneration(List<Individual> population) {
        List<Individual> newPopulation = new ArrayList<>();
        
        // 菁英保留
        for (int i = 0; i < eliteSize && i < population.size(); i++) {
            newPopulation.add(new Individual(population.get(i)));
        }
        
        // 產生剩餘個體
        while (newPopulation.size() < populationSize) {
            // 選擇父母
            Individual parent1 = tournamentSelection(population);
            Individual parent2 = tournamentSelection(population);
            
            // 交叉
            Individual[] children = {new Individual(parent1), new Individual(parent2)};
            if (random.nextDouble() < crossoverRate) {
                children = Individual.crossover(parent1, parent2, random);
            }
            
            // 突變
            for (Individual child : children) {
                child.mutate(random, mutationRate);
                if (newPopulation.size() < populationSize) {
                    newPopulation.add(child);
                }
            }
        }
        
        return newPopulation;
    }
    
    /**
     * 錦標賽選擇
     */
    private Individual tournamentSelection(List<Individual> population) {
        Individual best = null;
        
        for (int i = 0; i < tournamentSize; i++) {
            Individual candidate = population.get(random.nextInt(population.size()));
            if (best == null || candidate.getFitness() < best.getFitness()) {
                best = candidate;
            }
        }
        
        return best;
    }
    
    /**
     * 記錄世代統計資料
     */
    private void recordGenerationStatistics(List<Individual> population, int generation) {
        double bestFitness = population.get(0).getFitness();
        double totalFitness = 0.0;
        
        for (Individual individual : population) {
            totalFitness += individual.getFitness();
        }
        
        double avgFitness = totalFitness / population.size();
        
        bestFitnessHistory.add(bestFitness);
        avgFitnessHistory.add(avgFitness);
        
        // 每50世代輸出一次進度
        if (generation % 50 == 0 || generation == maxGenerations - 1) {
            System.out.printf("世代 %d: 最佳適應度 = %.2f, 平均適應度 = %.2f\n", 
                            generation, bestFitness, avgFitness);
        }
    }
    
    /**
     * 判斷是否提早終止
     */
    private boolean shouldTerminateEarly(int generation) {
        // 如果連續一定世代數沒有改善，則提早終止
        if (generation < 100) return false;
        
        int windowSize = 50;
        if (bestFitnessHistory.size() < windowSize) return false;
        
        double recentBest = bestFitnessHistory.get(bestFitnessHistory.size() - 1);
        double pastBest = bestFitnessHistory.get(bestFitnessHistory.size() - windowSize);
        
        // 如果改善幅度小於0.1%，則認為收斂
        double improvementRate = (pastBest - recentBest) / pastBest;
        return improvementRate < 0.001;
    }
    
    /**
     * 獲取最佳適應度歷史
     */
    public List<Double> getBestFitnessHistory() {
        return new ArrayList<>(bestFitnessHistory);
    }
    
    /**
     * 獲取平均適應度歷史
     */
    public List<Double> getAvgFitnessHistory() {
        return new ArrayList<>(avgFitnessHistory);
    }
    
    /**
     * 獲取當前參數資訊
     */
    public String getParameterInfo() {
        return String.format(
            "GA參數: 族群大小=%d, 最大世代=%d, 交叉率=%.2f, 突變率=%.2f, 菁英數=%d, 錦標賽大小=%d",
            populationSize, maxGenerations, crossoverRate, mutationRate, eliteSize, tournamentSize
        );
    }
} 