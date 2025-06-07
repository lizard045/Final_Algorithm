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
    private boolean isEvaluated; // 是否已計算適應度
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
    
    public Schedule(DAG dag, int[] assignment) {
        this(dag);
        System.arraycopy(assignment, 0, this.chromosome, 0, assignment.length);
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
        
        double[] actualStartTimes = new double[dag.getTaskCount()];
        double[] actualFinishTimes = new double[dag.getTaskCount()];
        double[] processorFinishTimes = new double[dag.getProcessorCount()];
        int[] lastTaskOnProcessor = new int[dag.getProcessorCount()];
        Arrays.fill(lastTaskOnProcessor, -1);

        this.criticalPathLinks.clear();

        // **核心修正**：在使用之前，將任務順序合法化為有效的拓撲排序
        List<Integer> legalTaskOrder = createLegalOrder();
        
        for (int taskId : legalTaskOrder) { // <-- 使用合法化的順序
            Task task = dag.getTask(taskId);
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
            
            actualStartTimes[taskId] = ast;
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
     * **NEW**: 將GA產生的任務順序轉換為一個有效的拓撲排序，以避免無效的排程。
     * 此方法使用Kahn演算法，並以GA提供的順序作為優先級，來決定下一步處理哪個「就緒」的任務。
     */
    private List<Integer> createLegalOrder() {
        if (this.taskOrder == null || this.taskOrder.isEmpty()) {
            this.taskOrder = Heuristics.getRankedTasks(dag).stream()
                                  .map(Task::getTaskId)
                                  .collect(Collectors.toList());
        }

        Map<Integer, Integer> inDegree = new HashMap<>();
        for (Task t : dag.getTasks()) {
            inDegree.put(t.getTaskId(), t.getPredecessors().size());
        }

        Map<Integer, Integer> priority = new HashMap<>();
        for (int i = 0; i < this.taskOrder.size(); i++) {
            priority.put(this.taskOrder.get(i), i);
        }

        PriorityQueue<Integer> readyQueue = new PriorityQueue<>(Comparator.comparingInt(task -> priority.getOrDefault(task, Integer.MAX_VALUE)));
        for (Task t : dag.getTasks()) {
            if (inDegree.get(t.getTaskId()) == 0) {
                readyQueue.add(t.getTaskId());
            }
        }

        List<Integer> legalOrder = new ArrayList<>();
        while (!readyQueue.isEmpty()) {
            int taskId = readyQueue.poll();
            legalOrder.add(taskId);

            for (int successorId : dag.getTask(taskId).getSuccessors()) {
                int newDegree = inDegree.get(successorId) - 1;
                inDegree.put(successorId, newDegree);
                if (newDegree == 0) {
                    readyQueue.add(successorId);
                }
            }
        }

        if (legalOrder.size() != dag.getTaskCount()) {
            throw new RuntimeException("排程錯誤：任務圖中可能存在環路，無法產生有效的拓撲排序。");
        }
        
        // 更新自身的taskOrder為合法版本，這樣後續操作(如clone)都會使用正確的順序
        this.taskOrder = legalOrder;
        return legalOrder;
    }

    /**
     * 基於關鍵路徑的局部搜尋 (新)
     */
    public void criticalPathLocalSearch() {
        boolean improvedInLoop;
        do {
            improvedInLoop = false;
            evaluateFitness(); // Ensure makespan and critical path are up to date
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
                chromosome[taskId] = (bestProcessor == originalProcessor) ? originalProcessor : bestProcessor;
                
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