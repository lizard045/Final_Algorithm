package core;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Schedule類別：表示一個調度方案
 * 包含任務到處理器的分配和適應度計算
 */
public class Schedule implements Cloneable {
    private int[] chromosome; // 染色體：chromosome[i] = j 表示任務i分配給處理器j
    private List<Integer> taskOrder; // 排序染色體：任務的執行順序
    private double makespan; // 總完成時間（適應度值）
    boolean isEvaluated; // 將可見性改為 package-private 以便GA存取
    private DAG dag; // DAG參考

    // 用於追蹤關鍵路徑
    // Map<Integer, Integer> child -> parent (-1 if no parent)
    private Map<Integer, Integer> criticalPathLinks; 
    
    public Schedule(DAG dag) {
        this.dag = dag;
        this.chromosome = new int[dag.getTaskCount()];
        this.makespan = 0.0;
        this.isEvaluated = false;
        this.criticalPathLinks = new HashMap<>();
    }
    
    /**
     * **NEW**: 產生一個此排程的唯一標識符，用於快取。
     * @return 代表此排程的唯一字串鍵。
     */
    public String getCacheKey() {
        // 確保順序是合法的，這樣相同的邏輯狀態才能產生相同的鍵
        if (!isEvaluated) {
            evaluateFitness();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Arrays.toString(chromosome));
        sb.append(':');
        // taskOrder might be null before the first evaluation
        if (taskOrder != null) {
            for (Integer taskId : taskOrder) {
                sb.append(taskId).append(',');
            }
        }
        return sb.toString();
    }
    
    /**
     * **NEW**: 手動設定 makespan，主要用於從快取中恢復狀態。
     * @param makespan 要設定的 makespan 值。
     */
    public void setMakespan(double makespan) {
        this.makespan = makespan;
        this.isEvaluated = true; // 當手動設定makespan時，也應將其視為已評估
    }
    
    public Schedule(DAG dag, int[] assignment) {
        this(dag);
        System.arraycopy(assignment, 0, this.chromosome, 0, assignment.length);
    }
    
    /**
     * **NEW for ACO**: Constructor for creating a schedule with a given assignment and task order.
     */
    public Schedule(DAG dag, int[] assignment, List<Integer> taskOrder) {
        this(dag, assignment);
        this.taskOrder = new ArrayList<>(taskOrder);
        this.isEvaluated = false; // Needs evaluation with the new order
    }
    
    /**
     * 複製建構子
     */
    public Schedule(Schedule other) {
        this.dag = other.dag;
        this.chromosome = Arrays.copyOf(other.chromosome, other.chromosome.length);
        this.taskOrder = (other.taskOrder != null) ? new ArrayList<>(other.taskOrder) : null;
        this.makespan = other.makespan;
        this.isEvaluated = other.isEvaluated;
        // 注意：criticalPathLinks是基於計算的結果，淺拷貝即可，因為每次evaluate都會重建
        this.criticalPathLinks = new HashMap<>(other.criticalPathLinks);
    }
    
    /**
     * 隨機初始化任務分配
     */
    public void randomInitialize(Random random) {
        for (int i = 0; i < chromosome.length; i++) {
            chromosome[i] = random.nextInt(dag.getProcessorCount());
        }
        isEvaluated = false;
    }
    
    /**
     * 計算調度方案的適應度（makespan），並記錄關鍵路徑信息
     */
    public double evaluateFitness() {
        if (isEvaluated) {
            return makespan;
        }
        
        double[] actualFinishTimes = new double[dag.getTaskCount()];
        double[] processorFinishTimes = new double[dag.getProcessorCount()];
        int[] lastTaskOnProcessor = new int[dag.getProcessorCount()];
        Arrays.fill(lastTaskOnProcessor, -1);

        this.criticalPathLinks.clear();

        // **恢復**：直接使用 taskOrder，因為它是由HEFT提供且固定的
        if (this.taskOrder == null || this.taskOrder.isEmpty()) {
            // 這個備用方案理論上不應被觸發，但作為保護性程式碼保留
            this.taskOrder = Heuristics.getRankedTasks(dag).stream()
                                  .map(Task::getTaskId)
                                  .collect(Collectors.toList());
        }
        
        for (int taskId : this.taskOrder) {
            int processorId = chromosome[taskId];
            
            double processorReadyTime = processorFinishTimes[processorId];
            
            double maxPredAFT = 0.0;
            int dataCriticalPred = -1;

            for (int predId : dag.getTask(taskId).getPredecessors()) {
                int predProcessorId = chromosome[predId];
                double commCost = dag.getCommunicationCost(predId, taskId, predProcessorId, processorId);
                double dataReadyTime = actualFinishTimes[predId] + commCost;
                if (dataReadyTime > maxPredAFT) {
                    maxPredAFT = dataReadyTime;
                    dataCriticalPred = predId;
                }
            }
            
            double ast;
            if (processorReadyTime > maxPredAFT) {
                ast = processorReadyTime;
                criticalPathLinks.put(taskId, lastTaskOnProcessor[processorId]);
            } else {
                ast = maxPredAFT;
                criticalPathLinks.put(taskId, dataCriticalPred);
            }
            
            double executionTime = dag.getTask(taskId).getComputationCost(processorId);
            double aft = ast + executionTime;
            
            actualFinishTimes[taskId] = aft;
            processorFinishTimes[processorId] = aft;
            lastTaskOnProcessor[processorId] = taskId;
        }
        
        makespan = 0.0;
        int exitNodeId = -1;
        for (int i = 0; i < dag.getTaskCount(); i++) {
            if (actualFinishTimes[i] > makespan) {
                makespan = actualFinishTimes[i];
                exitNodeId = i;
            }
        }
        
        criticalPathLinks.put(-1, exitNodeId); // Use -1 as a special key to store the exit node

        isEvaluated = true;
        return makespan;
    }

    /**
     * **NEW for ACO**: A public method to calculate makespan, which is just an alias for evaluateFitness.
     */
    public double calculateMakespan() {
        return evaluateFitness();
    }

    /**
     * 基於關鍵路徑的局部搜尋 (完整版)
     * 會持續迭代直到找不到任何改進為止。
     */
    public void criticalPathLocalSearch() {
        boolean improvedInLoop;
        do {
            improvedInLoop = false;
            evaluateFitness(); // 確保 makespan 和關鍵路徑是最新的
            List<Integer> criticalPath = findCriticalPath();

            for (int taskId : criticalPath) {
                int originalProcessor = chromosome[taskId];
                double bestMakespan = this.makespan;
                int bestProcessor = originalProcessor;

                // 嘗試將關鍵任務移動到其他處理器
                for (int pId = 0; pId < dag.getProcessorCount(); pId++) {
                    if (pId == originalProcessor) continue;

                    chromosome[taskId] = pId;
                    double newMakespan = evaluateFitness();

                    if (newMakespan < bestMakespan) {
                        bestMakespan = newMakespan;
                        bestProcessor = pId;
                        improvedInLoop = true;
                    }
                }
                
                // 無論是否找到更好的，都恢復到這個迴圈開始前的最佳狀態
                chromosome[taskId] = bestProcessor;
                
                if (improvedInLoop) {
                    evaluateFitness(); // 更新狀態
                    break; // 找到一個改進就立即重新開始，因為關鍵路徑可能已改變
                }
            }
        } while (improvedInLoop);
    }
    
    /**
     * 從記錄的鏈路中回溯找到關鍵路徑 (新)
     */
    public List<Integer> findCriticalPath() {
        if (!isEvaluated) {
            evaluateFitness();
        }
        
        List<Integer> path = new ArrayList<>();
        Integer currentNodeId = criticalPathLinks.get(-1); // Get exit node

        while (currentNodeId != null && currentNodeId != -1) {
            path.add(currentNodeId);
            currentNodeId = criticalPathLinks.get(currentNodeId);
        }
        
        Collections.reverse(path);
        return path;
    }

    /**
     * 均勻交叉操作 (Uniform Crossover)
     */
    public static Schedule[] crossover(Schedule parent1, Schedule parent2, Random random) {
        int length = parent1.chromosome.length;
        
        Schedule child1 = new Schedule(parent1.dag);
        Schedule child2 = new Schedule(parent2.dag);
        
        for (int i = 0; i < length; i++) {
            if (random.nextDouble() < 0.5) {
                child1.chromosome[i] = parent1.chromosome[i];
                child2.chromosome[i] = parent2.chromosome[i];
            } else {
                child1.chromosome[i] = parent2.chromosome[i];
                child2.chromosome[i] = parent1.chromosome[i];
            }
        }
        
        child1.isEvaluated = false;
        child2.isEvaluated = false;
        
        return new Schedule[]{child1, child2};
    }

    /**
     * 突變操作
     */
    public void mutate(Random random, double mutationRate) {
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < mutationRate) {
                chromosome[i] = random.nextInt(dag.getProcessorCount());
                isEvaluated = false;
            }
        }
    }
    
    // Getters and Setters
    public int[] getChromosome() {
        return chromosome;
    }
    
    public List<Integer> getTaskOrder() {
        return taskOrder;
    }

    public void setTaskOrder(List<Integer> taskOrder) {
        this.taskOrder = taskOrder;
        this.isEvaluated = false; // 順序改變，需要重新評估
    }
    
    public double getMakespan() {
        if (!isEvaluated) {
            evaluateFitness();
        }
        return makespan;
    }
    
    public String getDetailedSchedule() {
        if (!isEvaluated) {
            evaluateFitness();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Makespan: %.2f\n", makespan));
        sb.append("Task Assignment:\n");
        for (int i = 0; i < chromosome.length; i++) {
            sb.append(String.format("  Task %d -> Processor %d\n", i, chromosome[i]));
        }
        sb.append("Task Execution Order:\n  ");
        if (taskOrder != null) {
            for (int taskId : taskOrder) {
                sb.append(taskId).append(" -> ");
            }
        }
        sb.append("END\n");
        return sb.toString();
    }

    public boolean isEvaluated() {
        return isEvaluated;
    }

    /**
     * **NEW for ACO**: Gets the processor assignment for a specific task.
     * @param taskId The ID of the task.
     * @return The ID of the processor the task is assigned to.
     */
    public int getProcessorForTask(int taskId) {
        if (taskId >= 0 && taskId < chromosome.length) {
            return chromosome[taskId];
        }
        return -1; // Return -1 for invalid task ID
    }

    // Public clone method
    @Override
    public Schedule clone() {
        try {
            Schedule cloned = (Schedule) super.clone();
            // The chromosome array needs to be deep-copied
            cloned.chromosome = Arrays.copyOf(this.chromosome, this.chromosome.length);
            // The taskOrder list also needs to be deep-copied
            if (this.taskOrder != null) {
                cloned.taskOrder = new ArrayList<>(this.taskOrder);
            }
            cloned.criticalPathLinks = new HashMap<>(this.criticalPathLinks);
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Should not happen
        }
    }
} 