package core;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Schedule類別：表示一個調度方案
 * 包含任務到處理器的分配和適應度計算
 */
public class Schedule {
    private int[] chromosome; // 染色體：chromosome[i] = j 表示任務i分配給處理器j
    private List<Integer> taskOrder; // 排序染色體：任務的執行順序
    private double makespan; // 總完成時間（適應度值）
    private boolean isEvaluated;
    private DAG dag; // DAG參考

    // 用於追蹤關鍵路徑
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
        this.criticalPathLinks = new HashMap<>(other.criticalPathLinks);
    }
    
    public void setMakespan(double makespan) {
        this.makespan = makespan;
        this.isEvaluated = true;
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

        if (this.taskOrder == null || this.taskOrder.isEmpty()) {
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
                
                chromosome[taskId] = bestProcessor;
                
                if (improvedInLoop) {
                    evaluateFitness(); // 更新狀態
                    break; 
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

    // Getters and Setters
    public int[] getChromosome() {
        return chromosome;
    }
    
    public List<Integer> getTaskOrder() {
        return taskOrder;
    }

    public void setTaskOrder(List<Integer> taskOrder) {
        this.taskOrder = taskOrder;
        this.isEvaluated = false; 
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

    public int getProcessorForTask(int taskId) {
        if (taskId >= 0 && taskId < chromosome.length) {
            return chromosome[taskId];
        }
        return -1; // Return -1 for invalid task ID
    }
} 