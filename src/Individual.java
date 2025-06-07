import java.util.*;

/**
 * 個體類別 - 代表一個任務調度方案
 * 每個個體包含任務到處理器的映射和適應度值
 */
public class Individual implements Comparable<Individual> {
    private int[] chromosome;        // 染色體：每個位置代表對應任務分配到的處理器
    private double fitness;          // 適應度值（執行時間）
    private int taskCount;          // 任務數量
    private int processorCount;     // 處理器數量
    
    /**
     * 建構函數
     */
    public Individual(int taskCount, int processorCount) {
        this.taskCount = taskCount;
        this.processorCount = processorCount;
        this.chromosome = new int[taskCount];
        this.fitness = Double.MAX_VALUE;
    }
    
    /**
     * 複製建構函數
     */
    public Individual(Individual other) {
        this.taskCount = other.taskCount;
        this.processorCount = other.processorCount;
        this.chromosome = other.chromosome.clone();
        this.fitness = other.fitness;
    }
    
    /**
     * 隨機初始化個體
     */
    public void randomInitialize(Random random) {
        for (int i = 0; i < taskCount; i++) {
            chromosome[i] = random.nextInt(processorCount);
        }
        fitness = Double.MAX_VALUE; // 需要重新計算適應度
    }
    
    /**
     * 獲取指定任務的處理器分配
     */
    public int getProcessorAssignment(int taskId) {
        return chromosome[taskId];
    }
    
    /**
     * 設定指定任務的處理器分配
     */
    public void setProcessorAssignment(int taskId, int processorId) {
        chromosome[taskId] = processorId;
        fitness = Double.MAX_VALUE; // 需要重新計算適應度
    }
    
    /**
     * 獲取染色體
     */
    public int[] getChromosome() {
        return chromosome.clone();
    }
    
    /**
     * 設定染色體
     */
    public void setChromosome(int[] chromosome) {
        this.chromosome = chromosome.clone();
        fitness = Double.MAX_VALUE; // 需要重新計算適應度
    }
    
    /**
     * 獲取適應度值
     */
    public double getFitness() {
        return fitness;
    }
    
    /**
     * 設定適應度值
     */
    public void setFitness(double fitness) {
        this.fitness = fitness;
    }
    
    /**
     * 單點交叉
     */
    public static Individual[] crossover(Individual parent1, Individual parent2, Random random) {
        Individual child1 = new Individual(parent1);
        Individual child2 = new Individual(parent2);
        
        int crossoverPoint = random.nextInt(parent1.taskCount);
        
        for (int i = crossoverPoint; i < parent1.taskCount; i++) {
            child1.chromosome[i] = parent2.chromosome[i];
            child2.chromosome[i] = parent1.chromosome[i];
        }
        
        child1.fitness = Double.MAX_VALUE;
        child2.fitness = Double.MAX_VALUE;
        
        return new Individual[]{child1, child2};
    }
    
    /**
     * 突變操作
     */
    public void mutate(Random random, double mutationRate) {
        for (int i = 0; i < taskCount; i++) {
            if (random.nextDouble() < mutationRate) {
                chromosome[i] = random.nextInt(processorCount);
                fitness = Double.MAX_VALUE; // 需要重新計算適應度
            }
        }
    }
    
    /**
     * 比較函數（用於排序）
     * 適應度值越小越好
     */
    @Override
    public int compareTo(Individual other) {
        return Double.compare(this.fitness, other.fitness);
    }
    
    /**
     * 轉換為字串表示
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Individual{");
        sb.append("chromosome=").append(Arrays.toString(chromosome));
        sb.append(", fitness=").append(String.format("%.2f", fitness));
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 檢查個體是否有效（所有任務都有分配處理器）
     */
    public boolean isValid() {
        for (int assignment : chromosome) {
            if (assignment < 0 || assignment >= processorCount) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 獲取任務數量
     */
    public int getTaskCount() {
        return taskCount;
    }
    
    /**
     * 獲取處理器數量
     */
    public int getProcessorCount() {
        return processorCount;
    }
} 