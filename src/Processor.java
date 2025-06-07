import java.util.*;

/**
 * Processor類別：儲存處理器資訊
 * 包含處理器ID、任務列表、完成時間等資訊
 */
public class Processor {
    private int processorId;
    private List<TaskExecution> taskSchedule; // 任務執行順序
    private double readyTime; // 處理器準備時間
    
    public Processor(int processorId) {
        this.processorId = processorId;
        this.taskSchedule = new ArrayList<>();
        this.readyTime = 0.0;
    }
    
    // 內部類別：表示任務執行資訊
    public static class TaskExecution {
        private int taskId;
        private double startTime;
        private double finishTime;
        
        public TaskExecution(int taskId, double startTime, double finishTime) {
            this.taskId = taskId;
            this.startTime = startTime;
            this.finishTime = finishTime;
        }
        
        // Getters
        public int getTaskId() { return taskId; }
        public double getStartTime() { return startTime; }
        public double getFinishTime() { return finishTime; }
        
        @Override
        public String toString() {
            return String.format("Task%d[%.2f-%.2f]", taskId, startTime, finishTime);
        }
    }
    
    // Getters and Setters
    public int getProcessorId() {
        return processorId;
    }
    
    public List<TaskExecution> getTaskSchedule() {
        return taskSchedule;
    }
    
    public double getReadyTime() {
        return readyTime;
    }
    
    public void setReadyTime(double readyTime) {
        this.readyTime = readyTime;
    }
    
    // 添加任務到處理器
    public void addTask(int taskId, double startTime, double finishTime) {
        taskSchedule.add(new TaskExecution(taskId, startTime, finishTime));
        this.readyTime = Math.max(this.readyTime, finishTime);
    }
    
    // 清空任務調度
    public void clearSchedule() {
        taskSchedule.clear();
        readyTime = 0.0;
    }
    
    // 獲取處理器的總負載時間
    public double getTotalLoadTime() {
        if (taskSchedule.isEmpty()) return 0.0;
        return taskSchedule.get(taskSchedule.size() - 1).getFinishTime();
    }
    
    // 獲取指定任務的完成時間
    public double getTaskFinishTime(int taskId) {
        for (TaskExecution te : taskSchedule) {
            if (te.getTaskId() == taskId) {
                return te.getFinishTime();
            }
        }
        return -1; // 任務不在此處理器上
    }
    
    @Override
    public String toString() {
        return "Processor{" +
                "processorId=" + processorId +
                ", readyTime=" + readyTime +
                ", tasks=" + taskSchedule +
                '}';
    }
} 