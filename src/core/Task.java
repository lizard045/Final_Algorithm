package core;

import java.util.*;

/**
 * Task類別：儲存任務資訊
 * 包含任務ID、計算成本、依賴關係等資訊
 */
public class Task {
    private final int taskId;
    private final double[] computationCosts; // computationCosts[j] 是在處理器 j 上的成本
    private List<Integer> predecessors; // 前驅任務列表
    private List<Integer> successors;   // 後繼任務列表
    private Map<Integer, Integer> dataTransferVolume; // 與其他任務的數據傳輸量
    
    public Task(int taskId, int processorCount) {
        this.taskId = taskId;
        this.computationCosts = new double[processorCount];
        this.predecessors = new ArrayList<>();
        this.successors = new ArrayList<>();
        this.dataTransferVolume = new HashMap<>();
    }
    
    // Getters and Setters
    public int getTaskId() {
        return taskId;
    }
    
    public double[] getComputationCosts() {
        return computationCosts;
    }
    
    public double getComputationCost(int processorId) {
        return computationCosts[processorId];
    }
    
    public void setComputationCost(int processorId, double cost) {
        this.computationCosts[processorId] = cost;
    }
    
    public List<Integer> getPredecessors() {
        return predecessors;
    }
    
    public List<Integer> getSuccessors() {
        return successors;
    }
    
    public void addPredecessor(int taskId) {
        if (!predecessors.contains(taskId)) {
            predecessors.add(taskId);
        }
    }
    
    public void addSuccessor(int taskId) {
        if (!successors.contains(taskId)) {
            successors.add(taskId);
        }
    }
    
    public Map<Integer, Integer> getDataTransferVolume() {
        return dataTransferVolume;
    }
    
    public void setDataTransferVolume(int toTaskId, int volume) {
        this.dataTransferVolume.put(toTaskId, volume);
    }
    
    public int getDataTransferVolume(int toTaskId) {
        return dataTransferVolume.getOrDefault(toTaskId, 0);
    }
    
    @Override
    public String toString() {
        return "Task{" +
                "taskId=" + taskId +
                ", predecessors=" + predecessors +
                ", successors=" + successors +
                '}';
    }
} 